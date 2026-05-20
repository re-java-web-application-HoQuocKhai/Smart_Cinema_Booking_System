package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.ShowtimeRequest;
import com.re.smart_cinema_booking_system.entity.Movie;
import com.re.smart_cinema_booking_system.entity.Room;
import com.re.smart_cinema_booking_system.entity.Showtime;
import com.re.smart_cinema_booking_system.enums.MovieStatus;
import com.re.smart_cinema_booking_system.enums.ShowtimeStatus;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.repository.MovieRepository;
import com.re.smart_cinema_booking_system.repository.RoomRepository;
import com.re.smart_cinema_booking_system.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;

    private static final DateTimeFormatter VN_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @Transactional(readOnly = true)
    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAllWithDetails();
    }

    @Transactional(readOnly = true)
    public Showtime getShowtimeById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu với ID: " + id));
        // Force load lazy associations within transaction
        showtime.getMovie().getTitle();
        showtime.getRoom().getName();
        showtime.getRoom().getCinema().getName();
        return showtime;
    }

    @Transactional(readOnly = true)
    public List<Room> getAllActiveRooms() {
        return roomRepository.findAllActiveWithCinema();
    }

    @Transactional(readOnly = true)
    public List<Movie> getAvailableMovies() {
        List<Movie> movies = movieRepository.findAll();
        return movies.stream()
                .filter(m -> m.getStatus() == MovieStatus.COMING_SOON || m.getStatus() == MovieStatus.NOW_SHOWING)
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()))
                .toList();
    }

    @Transactional
    public Showtime createShowtime(ShowtimeRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phim"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng chiếu"));

        // 1. Auto-calculate end_time
        LocalDateTime endTime = calculateEndTime(request.getStartTime(), movie, room);

        // 2. Check room conflict
        checkConflict(room, request.getStartTime(), endTime, null);

        // 3. Save
        Showtime showtime = Showtime.builder()
                .movie(movie)
                .room(room)
                .startTime(request.getStartTime())
                .endTime(endTime)
                .status(request.getStatus())
                .basePrice(request.getBasePrice())
                .build();

        return showtimeRepository.save(showtime);
    }

    @Transactional
    public Showtime updateShowtime(Long id, ShowtimeRequest request) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phim"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng chiếu"));

        // 1. Auto-calculate end_time
        LocalDateTime endTime = calculateEndTime(request.getStartTime(), movie, room);

        // 2. Check room conflict (exclude self)
        checkConflict(room, request.getStartTime(), endTime, id);

        // 3. Update
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setStatus(request.getStatus());
        showtime.setBasePrice(request.getBasePrice());

        return showtimeRepository.save(showtime);
    }

    @Transactional
    public void deleteShowtime(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));
        showtimeRepository.delete(showtime);
        try {
            showtimeRepository.flush();
        } catch (Exception e) {
            throw new BusinessException("Không thể xóa suất chiếu vì đang có vé hoặc booking liên quan.");
        }
    }

    public ShowtimeRequest toRequest(Showtime showtime) {
        ShowtimeRequest request = new ShowtimeRequest();
        request.setMovieId(showtime.getMovie().getId());
        request.setRoomId(showtime.getRoom().getId());
        request.setStartTime(showtime.getStartTime());
        request.setStatus(showtime.getStatus());
        request.setBasePrice(showtime.getBasePrice());
        return request;
    }

    // === Private helpers ===

    private LocalDateTime calculateEndTime(LocalDateTime startTime, Movie movie, Room room) {
        int totalMinutes = movie.getDurationMinutes()
                + (room.getCleanupDurationMinutes() != null ? room.getCleanupDurationMinutes() : 15);
        return startTime.plusMinutes(totalMinutes);
    }

    private void checkConflict(Room room, LocalDateTime startTime, LocalDateTime endTime, Long excludeId) {
        List<Showtime> conflicts = showtimeRepository.findConflicts(
                room.getId(), startTime, endTime, excludeId, ShowtimeStatus.CANCELLED
        );

        if (!conflicts.isEmpty()) {
            Showtime conflict = conflicts.get(0);
            // Force load lazy associations for error message
            String movieTitle = conflict.getMovie().getTitle();
            String roomName = room.getName();
            String cinemaName = room.getCinema().getName();

            String conflictInfo = String.format(
                    "Phòng \"%s\" (%s) đã có suất chiếu \"%s\" từ %s đến %s. Vui lòng chọn thời gian khác.",
                    roomName, cinemaName, movieTitle,
                    conflict.getStartTime().format(VN_FORMATTER),
                    conflict.getEndTime().format(VN_FORMATTER)
            );
            throw new BusinessException(conflictInfo);
        }
    }
}
