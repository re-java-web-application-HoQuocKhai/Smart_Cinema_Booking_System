package com.re.smart_cinema_booking_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CinemaRevenueDTO {
    private String cinemaName;
    private BigDecimal totalRevenue;
    private Long ticketCount;
}
