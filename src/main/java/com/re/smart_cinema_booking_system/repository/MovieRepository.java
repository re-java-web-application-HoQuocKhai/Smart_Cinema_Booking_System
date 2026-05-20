package com.re.smart_cinema_booking_system.repository;

import com.re.smart_cinema_booking_system.entity.Movie;
import com.re.smart_cinema_booking_system.enums.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findAllByOrderByCreatedAtDesc();
    List<Movie> findByStatus(MovieStatus status);
    List<Movie> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);

    @Query(value = "SELECT COUNT(*) FROM showtimes WHERE movie_id = :movieId", nativeQuery = true)
    long countShowtimesByMovieId(@Param("movieId") Long movieId);
}
