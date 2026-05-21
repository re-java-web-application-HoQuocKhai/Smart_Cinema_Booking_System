package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.entity.Booking;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.interceptor.AuthInterceptor;
import com.re.smart_cinema_booking_system.service.BookingService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/booking")
@RequiredArgsConstructor
public class StaffBookingController {

    private final BookingService bookingService;

    @GetMapping
    public String searchPage() {
        return "staff/booking/search";
    }

    @GetMapping("/search")
    public String search(@RequestParam("bookingCode") String bookingCode, RedirectAttributes redirectAttributes) {
        if (bookingCode == null || bookingCode.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập mã đơn hàng");
            return "redirect:/staff/booking";
        }
        try {
            // Check if booking exists
            bookingService.getBookingByCode(bookingCode.trim().toUpperCase());
            return "redirect:/staff/booking/" + bookingCode.trim().toUpperCase();
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn hàng với mã: " + bookingCode);
            return "redirect:/staff/booking";
        }
    }

    @GetMapping("/{bookingCode}")
    public String bookingDetail(@PathVariable String bookingCode, Model model, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.getBookingByCode(bookingCode);
            model.addAttribute("booking", booking);
            return "staff/booking/detail";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/staff/booking";
        }
    }

    @PostMapping("/{bookingCode}/confirm")
    public String confirmPayment(@PathVariable String bookingCode, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute(AuthInterceptor.SESSION_USER);
            if (currentUser == null) {
                return "redirect:/auth/login";
            }
            bookingService.confirmPaymentByStaff(bookingCode, currentUser.getEmail());
            redirectAttributes.addFlashAttribute("success", "Xác nhận thanh toán thành công cho đơn hàng: " + bookingCode);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/booking/" + bookingCode;
    }

    @GetMapping("/{bookingCode}/print")
    public String printTicket(@PathVariable String bookingCode, Model model, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.getBookingByCode(bookingCode);
            // Confirm the printed action internally
            bookingService.printTickets(bookingCode);
            model.addAttribute("booking", booking);
            return "staff/booking/print";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/staff/booking/" + bookingCode;
        }
    }
}
