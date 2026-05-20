package com.re.smart_cinema_booking_system.controller;

import com.re.smart_cinema_booking_system.dto.BookingConfirmRequest;
import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.entity.Booking;
import com.re.smart_cinema_booking_system.entity.Seat;
import com.re.smart_cinema_booking_system.entity.SeatHold;
import com.re.smart_cinema_booking_system.entity.Showtime;
import com.re.smart_cinema_booking_system.enums.PaymentMethod;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.service.BookingService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/customer/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    public String selectShowtime(Model model) {
        List<Showtime> showtimes = bookingService.getAvailableShowtimes();
        model.addAttribute("showtimes", showtimes);
        model.addAttribute("soldOutMap", bookingService.getSoldOutMap(showtimes));
        return "customer/booking/select-showtime";
    }

    @GetMapping("/seats/{showtimeId}")
    public String selectSeats(@PathVariable("showtimeId") Long showtimeId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            Showtime showtime = bookingService.getShowtimeById(showtimeId);
            List<Seat> seats = bookingService.getSeatsForShowtime(showtimeId);
            Set<Long> bookedSeatIds = bookingService.getBookedSeatIds(showtimeId);
            Map<Long, SeatHold> activeHolds = bookingService.getActiveHoldsMap(showtimeId);
            boolean isSoldOut = bookingService.isSoldOut(showtimeId);

            java.util.Map<String, java.util.List<Seat>> seatsByRow = new java.util.LinkedHashMap<>();
            for (Seat seat : seats) {
                seatsByRow.computeIfAbsent(seat.getRowName(), k -> new java.util.ArrayList<>()).add(seat);
            }

            model.addAttribute("showtime", showtime);
            model.addAttribute("seatsByRow", seatsByRow);
            model.addAttribute("bookedSeatIds", bookedSeatIds);
            model.addAttribute("activeHolds", activeHolds);
            model.addAttribute("currentUserId", currentUser.getId());
            model.addAttribute("isSoldOut", isSoldOut);

            return "customer/booking/select-seats";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking";
        }
    }

    @PostMapping("/hold")
    public String holdSeats(@RequestParam("showtimeId") Long showtimeId,
                            @RequestParam(value = "seatIds", required = false) List<Long> seatIds,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        if (seatIds == null || seatIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất một ghế");
            return "redirect:/customer/booking/seats/" + showtimeId;
        }
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            if (bookingService.isSoldOut(showtimeId)) {
                throw new BusinessException("Suất chiếu đã hết vé, không thể đặt thêm.");
            }
            bookingService.holdSeats(currentUser.getId(), showtimeId, seatIds);
            return "redirect:/customer/booking/confirm/" + showtimeId;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking/seats/" + showtimeId;
        }
    }

    @GetMapping("/confirm/{showtimeId}")
    public String confirmForm(@PathVariable("showtimeId") Long showtimeId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            Showtime showtime = bookingService.getShowtimeById(showtimeId);
            List<SeatHold> heldSeats = bookingService.getUserHeldSeats(currentUser.getId(), showtimeId);
            if (heldSeats.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Thời gian giữ ghế đã hết hạn hoặc bạn chưa chọn ghế.");
                return "redirect:/customer/booking/seats/" + showtimeId;
            }

            List<Long> seatIds = heldSeats.stream().map(sh -> sh.getSeat().getId()).toList();
            BigDecimal totalAmount = bookingService.calculatePrice(showtimeId, seatIds);

            // Find the earliest expiry time
            LocalDateTime expiryTime = heldSeats.stream()
                    .map(SeatHold::getExpiredAt)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            model.addAttribute("showtime", showtime);
            model.addAttribute("heldSeats", heldSeats);
            model.addAttribute("totalAmount", totalAmount);
            model.addAttribute("expiryTime", expiryTime);
            model.addAttribute("paymentMethods", PaymentMethod.values());

            return "customer/booking/confirm";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking";
        }
    }

    @PostMapping("/confirm")
    public String confirmBooking(@RequestParam("showtimeId") Long showtimeId,
                                 @RequestParam("seatIds") List<Long> seatIds,
                                 @RequestParam("paymentMethod") PaymentMethod paymentMethod,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            BookingConfirmRequest request = new BookingConfirmRequest();
            request.setShowtimeId(showtimeId);
            request.setSeatIds(seatIds);
            request.setPaymentMethod(paymentMethod);

            Booking booking = bookingService.confirmBooking(currentUser.getId(), request);
            return "redirect:/customer/booking/result/" + booking.getBookingCode();
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking/seats/" + showtimeId;
        }
    }

    @GetMapping("/result/{bookingCode}")
    public String bookingResult(@PathVariable("bookingCode") String bookingCode,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.getBookingByCode(bookingCode);
            model.addAttribute("booking", booking);
            return "customer/booking/result";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking";
        }
    }

    @GetMapping("/history")
    public String bookingHistory(HttpSession session, Model model) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        model.addAttribute("bookings", bookingService.getUserBookings(currentUser.getId()));
        return "customer/booking/history";
    }

    @GetMapping("/history/{bookingCode}")
    public String bookingHistoryDetail(@PathVariable("bookingCode") String bookingCode,
                                       HttpSession session,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            Booking booking = bookingService.getBookingByCode(bookingCode);
            if (!booking.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem chi tiết đơn đặt vé này");
                return "redirect:/customer/booking/history";
            }

            boolean canCancel = false;
            if (booking.getStatus() == com.re.smart_cinema_booking_system.enums.BookingStatus.CONFIRMED && !booking.getTickets().isEmpty()) {
                LocalDateTime showtimeStart = booking.getTickets().get(0).getShowtimeSnapshot();
                if (showtimeStart == null) {
                    showtimeStart = booking.getTickets().get(0).getShowtime().getStartTime();
                }
                canCancel = LocalDateTime.now().plusHours(24).isBefore(showtimeStart);
            }

            model.addAttribute("booking", booking);
            model.addAttribute("canCancel", canCancel);
            return "customer/booking/history-detail";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/booking/history";
        }
    }

    @PostMapping("/cancel")
    public String cancelBooking(@RequestParam("bookingCode") String bookingCode,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        UserSessionDTO currentUser = (UserSessionDTO) session.getAttribute("currentUser");
        try {
            bookingService.cancelBooking(currentUser.getId(), bookingCode);
            redirectAttributes.addFlashAttribute("success", "Hủy vé và hoàn tiền thành công!");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/booking/history";
    }
}
