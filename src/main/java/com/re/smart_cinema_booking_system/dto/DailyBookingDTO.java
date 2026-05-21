package com.re.smart_cinema_booking_system.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBookingDTO {
    private LocalDate date;
    private Long bookingCount;
    private BigDecimal totalRevenue;

    public String getDateLabel() {
        return String.format("%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
    }
}
