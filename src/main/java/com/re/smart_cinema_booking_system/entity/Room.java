package com.re.smart_cinema_booking_system.entity;

import com.re.smart_cinema_booking_system.enums.RoomType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type")
    private RoomType roomType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "cleanup_duration_minutes")
    private Integer cleanupDurationMinutes;

    @Column
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
