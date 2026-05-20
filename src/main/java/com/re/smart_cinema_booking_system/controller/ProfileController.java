package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.ProfileUpdateRequest;
import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.enums.Gender;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.interceptor.AuthInterceptor;
import com.re.smart_cinema_booking_system.service.UserProfileService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public String viewProfile(HttpSession session, Model model) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute(AuthInterceptor.SESSION_USER);
        ProfileUpdateRequest profile = userProfileService.getProfile(currentUser.getId());

        model.addAttribute("user", currentUser);
        model.addAttribute("profile", profile);
        return "profile/view";
    }

    @GetMapping("/edit")
    public String editProfilePage(HttpSession session, Model model) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute(AuthInterceptor.SESSION_USER);
        ProfileUpdateRequest profile = userProfileService.getProfile(currentUser.getId());

        model.addAttribute("profileRequest", profile);
        model.addAttribute("genders", Gender.values());
        return "profile/edit";
    }

    @PostMapping("/edit")
    public String updateProfile(@Valid @ModelAttribute("profileRequest") ProfileUpdateRequest request,
                                BindingResult result,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("genders", Gender.values());
            return "profile/edit";
        }
        try {
            UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute(AuthInterceptor.SESSION_USER);
            UserSessionDTO updatedUser = userProfileService.updateProfile(currentUser.getId(), request);
            session.setAttribute(AuthInterceptor.SESSION_USER, updatedUser);
            redirectAttributes.addFlashAttribute("success", "Cập nhật hồ sơ thành công!");
            return "redirect:/profile";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("genders", Gender.values());
            return "profile/edit";
        }
    }
}
