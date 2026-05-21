package com.re.smart_cinema_booking_system.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieRevenueDTO {
    private Long movieId;
    private String movieTitle;
    private String posterUrl;
    private BigDecimal totalRevenue;
    private Long ticketCount;
}
