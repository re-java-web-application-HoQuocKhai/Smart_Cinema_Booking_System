package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByCinemaIdOrderByNameAsc(Long cinemaId);

    @Query("SELECT r FROM Room r JOIN FETCH r.cinema WHERE r.status = 'ACTIVE' ORDER BY r.cinema.name, r.name")
    List<Room> findAllActiveWithCinema();
}
