package com.re.smart_cinema_booking_system.entity;

import com.re.smart_cinema_booking_system.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status")
    @Builder.Default
    private TicketStatus ticketStatus = TicketStatus.BOOKED;

    @Column(name = "cinema_name_snapshot")
    private String cinemaNameSnapshot;

    @Column(name = "movie_title_snapshot")
    private String movieTitleSnapshot;

    @Column(name = "room_name_snapshot", length = 100)
    private String roomNameSnapshot;

    @Column(name = "seat_label_snapshot", length = 20)
    private String seatLabelSnapshot;

    @Column(name = "seat_type_snapshot", length = 50)
    private String seatTypeSnapshot;

    @Column(name = "showtime_snapshot")
    private LocalDateTime showtimeSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
