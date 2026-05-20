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
}
