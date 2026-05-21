package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Ticket;
import com.re.smart_cinema_booking_system.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
        SELECT t.seat.id FROM Ticket t
        WHERE t.showtime.id = :showtimeId
          AND t.ticketStatus IN :statuses
    """)
    List<Long> findBookedSeatIds(
        @Param("showtimeId") Long showtimeId,
        @Param("statuses") Collection<TicketStatus> statuses
    );

    long countByShowtimeIdAndTicketStatusIn(Long showtimeId, Collection<TicketStatus> statuses);

    // ===================== DASHBOARD STATISTICS =====================

    /**
     * Tổng số vé đã bán (BOOKED + USED)
     */
    @Query(value = """
        SELECT COUNT(t.id)
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
    """, nativeQuery = true)
    long countSoldTickets();

    @Query(value = """
        SELECT COUNT(t.id)
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
    """, nativeQuery = true)
    long countSoldTicketsByYear(@Param("year") int year);

    @Query(value = """
        SELECT COUNT(t.id)
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
    """, nativeQuery = true)
    long countSoldTicketsByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);

    /**
     * Top 5 phim doanh thu cao nhất (JOIN + GROUP BY + ORDER BY + LIMIT)
     * Trả về: [movieId, movieTitle, posterUrl, totalRevenue, ticketCount]
     */
    @Query(value = """
        SELECT m.id, m.title, m.poster_url,
               SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
        GROUP BY m.id, m.title, m.poster_url
        ORDER BY revenue DESC
        LIMIT 5
    """, nativeQuery = true)
    List<Object[]> findTopMoviesByRevenue();

    @Query(value = """
        SELECT m.id, m.title, m.poster_url,
               SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
        GROUP BY m.id, m.title, m.poster_url
        ORDER BY revenue DESC
        LIMIT 5
    """, nativeQuery = true)
    List<Object[]> findTopMoviesByRevenueByYear(@Param("year") int year);

    @Query(value = """
        SELECT m.id, m.title, m.poster_url,
               SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
        GROUP BY m.id, m.title, m.poster_url
        ORDER BY revenue DESC
        LIMIT 5
    """, nativeQuery = true)
    List<Object[]> findTopMoviesByRevenueByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);

    /**
     * Doanh thu theo rạp (GROUP BY cinema_name_snapshot)
     * Trả về: [cinemaName, totalRevenue, ticketCount]
     */
    @Query(value = """
        SELECT t.cinema_name_snapshot, SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
        GROUP BY t.cinema_name_snapshot
        ORDER BY revenue DESC
    """, nativeQuery = true)
    List<Object[]> findRevenueBycinema();

    @Query(value = """
        SELECT t.cinema_name_snapshot, SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
        GROUP BY t.cinema_name_snapshot
        ORDER BY revenue DESC
    """, nativeQuery = true)
    List<Object[]> findRevenueBycinemaByYear(@Param("year") int year);

    @Query(value = """
        SELECT t.cinema_name_snapshot, SUM(t.unit_price_snapshot) as revenue, COUNT(t.id) as cnt
        FROM tickets t
        JOIN bookings b ON t.booking_id = b.id
        WHERE b.status = 'CONFIRMED'
          AND t.ticket_status IN ('BOOKED', 'USED')
          AND b.paid_at IS NOT NULL
          AND YEAR(b.paid_at) = :year
          AND QUARTER(b.paid_at) = :quarter
        GROUP BY t.cinema_name_snapshot
        ORDER BY revenue DESC
    """, nativeQuery = true)
    List<Object[]> findRevenueBycinemaByYearAndQuarter(@Param("year") int year, @Param("quarter") int quarter);
}
