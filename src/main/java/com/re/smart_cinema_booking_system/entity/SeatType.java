package com.re.smart_cinema_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "seat_types")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(length = 100)
    private String name;

    @Column(name = "price_multiplier", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal priceMultiplier = BigDecimal.valueOf(1.00);
}
