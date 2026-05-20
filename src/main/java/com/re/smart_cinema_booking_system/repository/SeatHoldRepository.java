package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.SeatHold;
import com.re.smart_cinema_booking_system.enums.SeatHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    @Query("""
        SELECT sh FROM SeatHold sh
        WHERE sh.showtime.id = :showtimeId
          AND sh.status = :status
          AND sh.expiredAt > :now
    """)
    List<SeatHold> findActiveHolds(
        @Param("showtimeId") Long showtimeId,
        @Param("status") SeatHoldStatus status,
        @Param("now") LocalDateTime now
    );

    List<SeatHold> findByShowtimeIdAndUserIdAndStatus(Long showtimeId, Long userId, SeatHoldStatus status);

    @Modifying
    @Query("""
        DELETE FROM SeatHold sh
        WHERE sh.showtime.id = :showtimeId
          AND sh.seat.id IN :seatIds
          AND sh.status IN :statuses
    """)
    void deleteByShowtimeIdAndSeatIdAndStatusIn(
        @Param("showtimeId") Long showtimeId,
        @Param("seatIds") Collection<Long> seatIds,
        @Param("statuses") Collection<SeatHoldStatus> statuses
    );

    @Modifying
    @Query("DELETE FROM SeatHold sh WHERE sh.expiredAt <= :now AND sh.status = 'HOLDING'")
    void deleteExpiredHolds(@Param("now") LocalDateTime now);
}
