package com.re.smart_cinema_booking_system.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.re.smart_cinema_booking_system.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public String dashboard(@RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Integer quarter,
                            Model model) throws JsonProcessingException {
        
        // If year is not selected but quarter is, default to current year
        if (quarter != null && year == null) {
            year = LocalDate.now().getYear();
        }

        // 1. KPI Metrics
        model.addAttribute("totalRevenue", dashboardService.getTotalRevenue(year, quarter));
        model.addAttribute("totalTicketsSold", dashboardService.getTotalTicketsSold(year, quarter));
        model.addAttribute("totalBookings", dashboardService.getTotalBookings(year, quarter));
        model.addAttribute("totalCustomers", dashboardService.getTotalCustomers()); // Khách hàng không filter theo thời gian

        // 2. Jackson ObjectMapper để convert DTO thành JSON string cho Chart.js ở frontend
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. Data for Charts
        model.addAttribute("monthlyRevenueJson", mapper.writeValueAsString(dashboardService.getMonthlyRevenue(year, quarter)));
        model.addAttribute("topMoviesJson", mapper.writeValueAsString(dashboardService.getTopMoviesByRevenue(year, quarter)));
        model.addAttribute("cinemaRevenueJson", mapper.writeValueAsString(dashboardService.getRevenueByCinema(year, quarter)));
        
        // Daily bookings luôn lấy 30 ngày gần nhất (không bị ảnh hưởng bởi filter năm/quý)
        model.addAttribute("dailyBookingsJson", mapper.writeValueAsString(dashboardService.getDailyBookings(30)));

        // 4. Filter Options
        List<Integer> availableYears = dashboardService.getAvailableYears();
        if (availableYears.isEmpty()) {
            availableYears.add(LocalDate.now().getYear());
        }
        model.addAttribute("availableYears", availableYears);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedQuarter", quarter);

        return "admin/dashboard";
    }
}
