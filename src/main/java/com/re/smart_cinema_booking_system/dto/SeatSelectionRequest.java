package com.re.smart_cinema_booking_system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class SeatSelectionRequest {

    @NotNull(message = "Vui lòng chọn suất chiếu")
    private Long showtimeId;

    @NotEmpty(message = "Vui lòng chọn ít nhất một ghế")
    private List<Long> seatIds;
}
