package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.*;
import com.re.smart_cinema_booking_system.repository.BookingRepository;
import com.re.smart_cinema_booking_system.repository.TicketRepository;
import com.re.smart_cinema_booking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service tổng hợp dữ liệu Dashboard cho Admin.
 * Tất cả phép tính tổng/đếm được thực hiện bằng SQL (SUM, COUNT, GROUP BY),
 * KHÔNG dùng vòng lặp for trong Java — đúng yêu cầu SRS Hướng 4.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    // ===================== KPI METRICS =====================

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(Integer year, Integer quarter) {
        if (year != null && quarter != null) {
            return bookingRepository.sumTotalRevenueByYearAndQuarter(year, quarter);
        } else if (year != null) {
            return bookingRepository.sumTotalRevenueByYear(year);
        }
        return bookingRepository.sumTotalRevenue();
    }

    @Transactional(readOnly = true)
    public long getTotalTicketsSold(Integer year, Integer quarter) {
        if (year != null && quarter != null) {
            return ticketRepository.countSoldTicketsByYearAndQuarter(year, quarter);
        } else if (year != null) {
            return ticketRepository.countSoldTicketsByYear(year);
        }
        return ticketRepository.countSoldTickets();
    }

    @Transactional(readOnly = true)
    public long getTotalBookings(Integer year, Integer quarter) {
        if (year != null && quarter != null) {
            return bookingRepository.countConfirmedBookingsByYearAndQuarter(year, quarter);
        } else if (year != null) {
            return bookingRepository.countConfirmedBookingsByYear(year);
        }
        return bookingRepository.countConfirmedBookings();
    }

    @Transactional(readOnly = true)
    public long getTotalCustomers() {
        return userRepository.countActiveCustomers();
    }

    // ===================== CHART DATA =====================

    /**
     * Doanh thu theo tháng — cho biểu đồ cột (Bar Chart)
     * SQL: GROUP BY YEAR(paid_at), MONTH(paid_at)
     */
    @Transactional(readOnly = true)
    public List<MonthlyRevenueDTO> getMonthlyRevenue(Integer year, Integer quarter) {
        List<Object[]> results;
        if (year != null && quarter != null) {
            results = bookingRepository.findMonthlyRevenueByYearAndQuarter(year, quarter);
        } else if (year != null) {
            results = bookingRepository.findMonthlyRevenueByYear(year);
        } else {
            results = bookingRepository.findMonthlyRevenue();
        }
        return results.stream()
                .map(row -> MonthlyRevenueDTO.builder()
                        .year(((Number) row[0]).intValue())
                        .month(((Number) row[1]).intValue())
                        .totalRevenue((BigDecimal) row[2])
                        .bookingCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Top 5 phim doanh thu cao nhất — cho biểu đồ ngang (Horizontal Bar Chart)
     * SQL: JOIN tickets + bookings + movies, GROUP BY movie, ORDER BY revenue DESC LIMIT 5
     */
    @Transactional(readOnly = true)
    public List<MovieRevenueDTO> getTopMoviesByRevenue(Integer year, Integer quarter) {
        List<Object[]> results;
        if (year != null && quarter != null) {
            results = ticketRepository.findTopMoviesByRevenueByYearAndQuarter(year, quarter);
        } else if (year != null) {
            results = ticketRepository.findTopMoviesByRevenueByYear(year);
        } else {
            results = ticketRepository.findTopMoviesByRevenue();
        }
        return results.stream()
                .map(row -> MovieRevenueDTO.builder()
                        .movieId(((Number) row[0]).longValue())
                        .movieTitle((String) row[1])
                        .posterUrl((String) row[2])
                        .totalRevenue((BigDecimal) row[3])
                        .ticketCount(((Number) row[4]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Doanh thu theo rạp — cho biểu đồ tròn (Doughnut Chart)
     * SQL: GROUP BY cinema_name_snapshot
     */
    @Transactional(readOnly = true)
    public List<CinemaRevenueDTO> getRevenueByCinema(Integer year, Integer quarter) {
        List<Object[]> results;
        if (year != null && quarter != null) {
            results = ticketRepository.findRevenueBycinemaByYearAndQuarter(year, quarter);
        } else if (year != null) {
            results = ticketRepository.findRevenueBycinemaByYear(year);
        } else {
            results = ticketRepository.findRevenueBycinema();
        }
        return results.stream()
                .map(row -> CinemaRevenueDTO.builder()
                        .cinemaName((String) row[0])
                        .totalRevenue((BigDecimal) row[1])
                        .ticketCount(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Booking theo ngày trong 30 ngày gần nhất — cho biểu đồ line (Line Chart)
     * SQL: GROUP BY DATE(paid_at)
     */
    @Transactional(readOnly = true)
    public List<DailyBookingDTO> getDailyBookings(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = bookingRepository.findDailyBookings(startDate);
        return results.stream()
                .map(row -> {
                    java.time.LocalDate dateVal;
                    if (row[0] instanceof java.sql.Date) {
                        dateVal = ((java.sql.Date) row[0]).toLocalDate();
                    } else if (row[0] instanceof java.time.LocalDate) {
                        dateVal = (java.time.LocalDate) row[0];
                    } else {
                        dateVal = java.time.LocalDate.parse(row[0].toString());
                    }
                    return DailyBookingDTO.builder()
                        .date(dateVal)
                        .bookingCount(((Number) row[1]).longValue())
                        .totalRevenue((BigDecimal) row[2])
                        .build();
                })
                .collect(Collectors.toList());
    }

    // ===================== FILTER OPTIONS =====================

    /**
     * Lấy danh sách các năm có dữ liệu (cho dropdown filter)
     */
    @Transactional(readOnly = true)
    public List<Integer> getAvailableYears() {
        return bookingRepository.findDistinctYears();
    }
}
