package com.re.smart_cinema_booking_system.dto;

import com.re.smart_cinema_booking_system.enums.ShowtimeStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeRequest {

    @NotNull(message = "Vui lòng chọn phim")
    private Long movieId;

    @NotNull(message = "Vui lòng chọn phòng chiếu")
    private Long roomId;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @NotNull(message = "Trạng thái không được để trống")
    private ShowtimeStatus status;

    @NotNull(message = "Giá vé không được để trống")
    @DecimalMin(value = "0", message = "Giá vé phải >= 0")
    private BigDecimal basePrice;
}
