package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Booking> findByBookingCode(String bookingCode);

    // ===================== DASHBOARD STATISTICS =====================
    // Tất cả tính toán dùng SUM/COUNT/GROUP BY trong SQL,
    // KHÔNG dùng vòng lặp for trong Java (đúng yêu cầu SRS Hướng 4)

    /**
     * Tổng doanh thu (chỉ đơn CONFIRMED)
     */
    @Query(value = """
        SELECT COALESCE(SUM(b.total_amount), 0)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
    """, nativeQuery = true)
    BigDecimal sumTotalRevenue();

    /**
     * Tổng doanh thu có lọc theo năm
     */
    @Query(value = """
        SELECT COALESCE(SUM(b.total_amount), 0)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
    """, nativeQuery = true)
    BigDecimal sumTotalRevenueByYear(@Param("year") int year);

    /**
     * Tổng doanh thu có lọc theo năm + quý
     */
    @Query(value = """
        SELECT COALESCE(SUM(b.total_amount), 0)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
    """, nativeQuery = true)
    BigDecimal sumTotalRevenueByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);

    /**
     * Tổng số đơn đặt vé (CONFIRMED)
     */
    @Query(value = """
        SELECT COUNT(b.id)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
    """, nativeQuery = true)
    long countConfirmedBookings();

    @Query(value = """
        SELECT COUNT(b.id)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
    """, nativeQuery = true)
    long countConfirmedBookingsByYear(@Param("year") int year);

    @Query(value = """
        SELECT COUNT(b.id)
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
    """, nativeQuery = true)
    long countConfirmedBookingsByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);

    /**
     * Doanh thu theo tháng — Biểu đồ cột (GROUP BY year, month)
     * Trả về: [year, month, totalRevenue, bookingCount]
     */
    @Query(value = """
        SELECT YEAR(b.paid_at) as yr, MONTH(b.paid_at) as mn,
               SUM(b.total_amount) as revenue, COUNT(b.id) as cnt
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
        GROUP BY YEAR(b.paid_at), MONTH(b.paid_at)
        ORDER BY yr ASC, mn ASC
    """, nativeQuery = true)
    List<Object[]> findMonthlyRevenue();

    @Query(value = """
        SELECT YEAR(b.paid_at) as yr, MONTH(b.paid_at) as mn,
               SUM(b.total_amount) as revenue, COUNT(b.id) as cnt
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
        GROUP BY YEAR(b.paid_at), MONTH(b.paid_at)
        ORDER BY mn ASC
    """, nativeQuery = true)
    List<Object[]> findMonthlyRevenueByYear(@Param("year") int year);

    @Query(value = """
        SELECT YEAR(b.paid_at) as yr, MONTH(b.paid_at) as mn,
               SUM(b.total_amount) as revenue, COUNT(b.id) as cnt
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
        GROUP BY YEAR(b.paid_at), MONTH(b.paid_at)
        ORDER BY mn ASC
    """, nativeQuery = true)
    List<Object[]> findMonthlyRevenueByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);

    /**
     * Booking theo ngày trong khoảng thời gian (Biểu đồ line)
     * Trả về: [date, bookingCount, totalRevenue]
     */
    @Query(value = """
        SELECT DATE(b.paid_at) as dt, COUNT(b.id) as cnt, SUM(b.total_amount) as revenue
        FROM bookings b
        WHERE b.status = 'CONFIRMED'
          AND b.paid_at IS NOT NULL
          AND b.paid_at >= :startDate
        GROUP BY DATE(b.paid_at)
        ORDER BY dt ASC
    """, nativeQuery = true)
    List<Object[]> findDailyBookings(@Param("startDate") LocalDateTime startDate);

    /**
     * Lấy danh sách các năm có dữ liệu booking (cho dropdown filter)
     */
    @Query(value = """
        SELECT DISTINCT YEAR(b.paid_at) as yr
        FROM bookings b
        WHERE b.status = 'CONFIRMED' AND b.paid_at IS NOT NULL
        ORDER BY yr DESC
    """, nativeQuery = true)
    List<Integer> findDistinctYears();
}
