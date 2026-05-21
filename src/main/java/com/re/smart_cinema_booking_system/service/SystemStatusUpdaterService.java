package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemStatusUpdaterService {

    private final ShowtimeRepository showtimeRepository;

    /**
     * Chạy định kỳ mỗi phút một lần (60,000 milliseconds).
     * Tự động cập nhật trạng thái các suất chiếu dựa trên thời gian thực tế.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoUpdateShowtimeStatuses() {
        LocalDateTime now = LocalDateTime.now();

        // Cập nhật các suất chiếu đã đến giờ chiếu -> STARTED
        int startedCount = showtimeRepository.updateStatusToStarted(now);

        // Cập nhật các suất chiếu đã chiếu xong -> FINISHED
        int finishedCount = showtimeRepository.updateStatusToFinished(now);

        if (startedCount > 0 || finishedCount > 0) {
            log.info("System Auto-Update: {} showtimes STARTED, {} showtimes FINISHED.", startedCount, finishedCount);
        }
    }
}
