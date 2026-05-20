package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRoomIdAndStatusOrderByRowNameAscSeatNumberAsc(Long roomId, String status);
    long countByRoomIdAndStatus(Long roomId, String status);
}
