package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Showtime;
import com.re.smart_cinema_booking_system.enums.ShowtimeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    @Query("SELECT s FROM Showtime s JOIN FETCH s.movie JOIN FETCH s.room r JOIN FETCH r.cinema ORDER BY s.startTime DESC")
    List<Showtime> findAllWithDetails();

    @Query("SELECT s FROM Showtime s JOIN FETCH s.movie JOIN FETCH s.room r JOIN FETCH r.cinema WHERE s.status = :status ORDER BY s.startTime ASC")
    List<Showtime> findAllWithDetailsByStatus(@Param("status") ShowtimeStatus status);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie
        JOIN FETCH s.room r
        JOIN FETCH r.cinema
        WHERE s.status = :status AND s.startTime > :now
        ORDER BY s.startTime ASC
    """)
    List<Showtime> findAllActiveShowtimes(
        @Param("status") ShowtimeStatus status,
        @Param("now") LocalDateTime now
    );

    @Query("""
        SELECT s FROM Showtime s
        WHERE s.room.id = :roomId
          AND s.status <> :cancelledStatus
          AND s.startTime < :endTime
          AND s.endTime > :startTime
          AND (:excludeId IS NULL OR s.id <> :excludeId)
    """)
    List<Showtime> findConflicts(
        @Param("roomId") Long roomId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("excludeId") Long excludeId,
        @Param("cancelledStatus") ShowtimeStatus cancelledStatus
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Showtime s SET s.status = 'STARTED' WHERE s.status IN ('SCHEDULED', 'BOOKING_OPEN') AND s.startTime <= :now AND s.endTime > :now")
    int updateStatusToStarted(@Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Showtime s SET s.status = 'FINISHED' WHERE s.status NOT IN ('FINISHED', 'CANCELLED') AND s.endTime <= :now")
    int updateStatusToFinished(@Param("now") LocalDateTime now);
}
