package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long> {
    List<Cinema> findByStatusOrderByNameAsc(String status);
}
