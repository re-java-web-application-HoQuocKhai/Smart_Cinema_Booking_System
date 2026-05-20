package com.re.smart_cinema_booking_system.dto;

import com.re.smart_cinema_booking_system.enums.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class BookingConfirmRequest {

    @NotNull(message = "Vui lòng chọn suất chiếu")
    private Long showtimeId;

    @NotEmpty(message = "Danh sách ghế không được để trống")
    private List<Long> seatIds;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;
}
