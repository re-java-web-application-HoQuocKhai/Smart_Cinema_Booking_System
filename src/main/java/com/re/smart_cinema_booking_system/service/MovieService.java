package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.MovieRequest;
import com.re.smart_cinema_booking_system.entity.Genre;
import com.re.smart_cinema_booking_system.entity.Movie;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.repository.GenreRepository;
import com.re.smart_cinema_booking_system.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    @Transactional(readOnly = true)
    public List<Movie> getAllMovies() {
        List<Movie> movies = movieRepository.findAllByOrderByCreatedAtDesc();
        movies.forEach(m -> m.getGenres().size());
        return movies;
    }

    @Transactional(readOnly = true)
    public Movie getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phim với ID: " + id));
        movie.getGenres().size();
        return movie;
    }

    @Transactional(readOnly = true)
    public List<Movie> searchMovies(String keyword) {
        List<Movie> movies = movieRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword);
        movies.forEach(m -> m.getGenres().size());
        return movies;
    }

    @Transactional(readOnly = true)
    public List<Genre> getAllGenres() {
        return genreRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Movie createMovie(MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .durationMinutes(request.getDurationMinutes())
                .ageRating(request.getAgeRating())
                .posterUrl(request.getPosterUrl())
                .trailerUrl(request.getTrailerUrl())
                .releaseDate(request.getReleaseDate())
                .language(request.getLanguage())
                .status(request.getStatus())
                .build();

        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            List<Genre> genres = genreRepository.findAllById(request.getGenreIds());
            movie.setGenres(new HashSet<>(genres));
        }

        return movieRepository.save(movie);
    }

    @Transactional
    public Movie updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phim với ID: " + id));

        movie.setTitle(request.getTitle().trim());
        movie.setDescription(request.getDescription());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setAgeRating(request.getAgeRating());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setLanguage(request.getLanguage());
        movie.setStatus(request.getStatus());

        movie.getGenres().clear();
        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            List<Genre> genres = genreRepository.findAllById(request.getGenreIds());
            movie.getGenres().addAll(genres);
        }

        return movieRepository.save(movie);
    }

    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phim với ID: " + id));

        long showtimeCount = movieRepository.countShowtimesByMovieId(id);
        if (showtimeCount > 0) {
            throw new BusinessException(
                "Không thể xóa phim đang có " + showtimeCount + " suất chiếu. Hãy chuyển trạng thái sang 'Đã kết thúc' thay vì xóa.");
        }

        movieRepository.delete(movie);
    }

    public MovieRequest toRequest(Movie movie) {
        MovieRequest request = new MovieRequest();
        request.setTitle(movie.getTitle());
        request.setDescription(movie.getDescription());
        request.setDurationMinutes(movie.getDurationMinutes());
        request.setAgeRating(movie.getAgeRating());
        request.setPosterUrl(movie.getPosterUrl());
        request.setTrailerUrl(movie.getTrailerUrl());
        request.setReleaseDate(movie.getReleaseDate());
        request.setLanguage(movie.getLanguage());
        request.setStatus(movie.getStatus());
        request.setGenreIds(movie.getGenres().stream()
                .map(Genre::getId)
                .collect(Collectors.toList()));
        return request;
    }
}
