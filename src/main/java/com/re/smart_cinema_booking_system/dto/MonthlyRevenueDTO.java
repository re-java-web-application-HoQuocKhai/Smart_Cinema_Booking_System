package com.re.smart_cinema_booking_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyRevenueDTO {
    private Integer year;
    private Integer month;
    private BigDecimal totalRevenue;
    private Long bookingCount;

    public String getMonthLabel() {
        return String.format("T%d/%d", month, year);
    }
}
