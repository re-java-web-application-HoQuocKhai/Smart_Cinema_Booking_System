package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {
    Optional<SeatType> findByCode(String code);
}
