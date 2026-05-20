package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    List<Genre> findAllByOrderByNameAsc();
}
