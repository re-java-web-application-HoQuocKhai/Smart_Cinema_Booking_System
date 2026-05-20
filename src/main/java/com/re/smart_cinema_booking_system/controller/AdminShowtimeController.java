package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.ShowtimeRequest;
import com.re.smart_cinema_booking_system.entity.Showtime;
import com.re.smart_cinema_booking_system.enums.ShowtimeStatus;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.service.ShowtimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/showtimes")
@RequiredArgsConstructor
public class AdminShowtimeController {

    private final ShowtimeService showtimeService;

    @GetMapping
    public String listShowtimes(Model model) {
        model.addAttribute("showtimes", showtimeService.getAllShowtimes());
        return "admin/showtimes/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("showtimeRequest", new ShowtimeRequest());
        populateFormData(model);
        return "admin/showtimes/create";
    }

    @PostMapping("/create")
    public String createShowtime(@Valid @ModelAttribute ShowtimeRequest showtimeRequest,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateFormData(model);
            return "admin/showtimes/create";
        }
        try {
            showtimeService.createShowtime(showtimeRequest);
            redirectAttributes.addFlashAttribute("success", "Tạo suất chiếu thành công!");
            return "redirect:/admin/showtimes";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            populateFormData(model);
            return "admin/showtimes/create";
        }
    }

    @GetMapping("/{id}")
    public String viewShowtime(@PathVariable Long id, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            Showtime showtime = showtimeService.getShowtimeById(id);
            model.addAttribute("showtime", showtime);
            return "admin/showtimes/detail";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/showtimes";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                           RedirectAttributes redirectAttributes) {
        try {
            Showtime showtime = showtimeService.getShowtimeById(id);
            ShowtimeRequest showtimeRequest = showtimeService.toRequest(showtime);
            model.addAttribute("showtimeRequest", showtimeRequest);
            model.addAttribute("showtimeId", id);
            model.addAttribute("currentEndTime", showtime.getEndTime());
            populateFormData(model);
            return "admin/showtimes/edit";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/showtimes";
        }
    }

    @PostMapping("/{id}/edit")
    public String updateShowtime(@PathVariable Long id,
                                  @Valid @ModelAttribute ShowtimeRequest showtimeRequest,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("showtimeId", id);
            populateFormData(model);
            return "admin/showtimes/edit";
        }
        try {
            showtimeService.updateShowtime(id, showtimeRequest);
            redirectAttributes.addFlashAttribute("success", "Cập nhật suất chiếu thành công!");
            return "redirect:/admin/showtimes";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("showtimeId", id);
            populateFormData(model);
            return "admin/showtimes/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteShowtime(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            showtimeService.deleteShowtime(id);
            redirectAttributes.addFlashAttribute("success", "Xóa suất chiếu thành công!");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/showtimes";
    }

    private void populateFormData(Model model) {
        model.addAttribute("movies", showtimeService.getAvailableMovies());
        model.addAttribute("rooms", showtimeService.getAllActiveRooms());
        model.addAttribute("statuses", ShowtimeStatus.values());
    }
}
