package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.BookingConfirmRequest;
import com.re.smart_cinema_booking_system.entity.*;
import com.re.smart_cinema_booking_system.enums.*;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(readOnly = true)
    public List<Showtime> getAvailableShowtimes() {
        return showtimeRepository.findAllActiveShowtimes(ShowtimeStatus.BOOKING_OPEN, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Showtime getShowtimeById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));
        // Initialize lazy relationships
        showtime.getMovie().getTitle();
        showtime.getRoom().getName();
        showtime.getRoom().getCinema().getName();
        return showtime;
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = getShowtimeById(showtimeId);
        return seatRepository.findByRoomIdAndStatusOrderByRowNameAscSeatNumberAsc(
                showtime.getRoom().getId(), "ACTIVE"
        );
    }

    @Transactional(readOnly = true)
    public Set<Long> getBookedSeatIds(Long showtimeId) {
        List<Long> ids = ticketRepository.findBookedSeatIds(
                showtimeId, List.of(TicketStatus.BOOKED, TicketStatus.USED)
        );
        return new HashSet<>(ids);
    }

    @Transactional(readOnly = true)
    public Map<Long, SeatHold> getActiveHoldsMap(Long showtimeId) {
        List<SeatHold> activeHolds = seatHoldRepository.findActiveHolds(
                showtimeId, SeatHoldStatus.HOLDING, LocalDateTime.now()
        );
        return activeHolds.stream()
                .collect(Collectors.toMap(sh -> sh.getSeat().getId(), sh -> sh));
    }

    @Transactional
    public void holdSeats(Long userId, Long showtimeId, List<Long> seatIds) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));

        if (showtime.getStatus() != ShowtimeStatus.BOOKING_OPEN) {
            throw new BusinessException("Suất chiếu hiện không mở bán vé");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));

        LocalDateTime now = LocalDateTime.now();

        // 1. Clean up expired holds first
        seatHoldRepository.deleteExpiredHolds(now);

        // 2. Release previous holds of this user for this showtime (if any)
        List<SeatHold> oldHolds = seatHoldRepository.findByShowtimeIdAndUserIdAndStatus(
                showtimeId, userId, SeatHoldStatus.HOLDING
        );
        if (!oldHolds.isEmpty()) {
            List<Long> oldSeatIds = oldHolds.stream().map(sh -> sh.getSeat().getId()).toList();
            seatHoldRepository.deleteByShowtimeIdAndSeatIdAndStatusIn(
                    showtimeId, oldSeatIds, List.of(SeatHoldStatus.HOLDING, SeatHoldStatus.EXPIRED, SeatHoldStatus.CANCELLED)
            );
        }

        // 3. Check and hold new seats
        List<Seat> seatsToHold = seatRepository.findAllById(seatIds);
        if (seatsToHold.size() != seatIds.size()) {
            throw new BusinessException("Một số ghế được chọn không tồn tại");
        }

        Set<Long> bookedSeatIds = getBookedSeatIds(showtimeId);
        Map<Long, SeatHold> activeHolds = getActiveHoldsMap(showtimeId);

        List<SeatHold> newHolds = new ArrayList<>();
        for (Seat seat : seatsToHold) {
            if (bookedSeatIds.contains(seat.getId())) {
                throw new BusinessException("Ghế " + seat.getSeatLabel() + " đã được bán. Vui lòng chọn ghế khác.");
            }
            if (activeHolds.containsKey(seat.getId())) {
                SeatHold activeHold = activeHolds.get(seat.getId());
                if (!activeHold.getUser().getId().equals(userId)) {
                    throw new BusinessException("Ghế " + seat.getSeatLabel() + " đang được giữ bởi người khác.");
                }
            }

            // Create new hold for 10 minutes
            SeatHold hold = SeatHold.builder()
                    .showtime(showtime)
                    .seat(seat)
                    .user(user)
                    .status(SeatHoldStatus.HOLDING)
                    .expiredAt(now.plusMinutes(10))
                    .build();
            newHolds.add(hold);
        }

        try {
            seatHoldRepository.saveAll(newHolds);
            seatHoldRepository.flush();
        } catch (Exception e) {
            log.error("Error holding seats: {}", e.getMessage());
            throw new BusinessException("Ghế vừa bị người khác chọn. Vui lòng tải lại trang và chọn ghế khác.");
        }
    }

    @Transactional(readOnly = true)
    public List<SeatHold> getUserHeldSeats(Long userId, Long showtimeId) {
        return seatHoldRepository.findByShowtimeIdAndUserIdAndStatus(showtimeId, userId, SeatHoldStatus.HOLDING)
                .stream()
                .filter(sh -> sh.getExpiredAt().isAfter(LocalDateTime.now()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePrice(Long showtimeId, List<Long> seatIds) {
        Showtime showtime = getShowtimeById(showtimeId);
        List<Seat> seats = seatRepository.findAllById(seatIds);
        
        BigDecimal total = BigDecimal.ZERO;
        for (Seat seat : seats) {
            BigDecimal multiplier = seat.getSeatType().getPriceMultiplier();
            total = total.add(showtime.getBasePrice().multiply(multiplier));
        }
        return total;
    }

    @Transactional
    public Booking confirmBooking(Long userId, BookingConfirmRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Clean up expired holds
        seatHoldRepository.deleteExpiredHolds(now);

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));

        // 2. Verify holds exist and are still active
        List<SeatHold> userHolds = seatHoldRepository.findByShowtimeIdAndUserIdAndStatus(
                request.getShowtimeId(), userId, SeatHoldStatus.HOLDING
        );
        Set<Long> heldSeatIds = userHolds.stream()
                .filter(sh -> sh.getExpiredAt().isAfter(now))
                .map(sh -> sh.getSeat().getId())
                .collect(Collectors.toSet());

        for (Long requestedSeatId : request.getSeatIds()) {
            if (!heldSeatIds.contains(requestedSeatId)) {
                throw new BusinessException("Thời gian giữ ghế đã hết hạn hoặc ghế chưa được chọn. Vui lòng thực hiện lại.");
            }
        }

        // 3. Verify no tickets have been sold for these seats in this showtime
        Set<Long> bookedSeatIds = getBookedSeatIds(request.getShowtimeId());
        for (Long seatId : request.getSeatIds()) {
            if (bookedSeatIds.contains(seatId)) {
                throw new BusinessException("Ghế đã bị người khác đặt mua. Giao dịch bị hủy.");
            }
        }

        // 4. Calculate amount
        List<Seat> seats = seatRepository.findAllById(request.getSeatIds());
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Ticket> tickets = new ArrayList<>();

        // 5. Create Booking
        String bookingCode = generateBookingCode();
        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .user(user)
                .status(BookingStatus.CONFIRMED)
                .subtotalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.SUCCESS)
                .paymentMethod(request.getPaymentMethod())
                .paidAt(now)
                .build();

        for (Seat seat : seats) {
            BigDecimal seatPrice = showtime.getBasePrice().multiply(seat.getSeatType().getPriceMultiplier());
            totalAmount = totalAmount.add(seatPrice);

            Ticket ticket = Ticket.builder()
                    .booking(booking)
                    .showtime(showtime)
                    .seat(seat)
                    .ticketStatus(TicketStatus.BOOKED)
                    .cinemaNameSnapshot(showtime.getRoom().getCinema().getName())
                    .movieTitleSnapshot(showtime.getMovie().getTitle())
                    .roomNameSnapshot(showtime.getRoom().getName())
                    .seatLabelSnapshot(seat.getSeatLabel())
                    .seatTypeSnapshot(seat.getSeatType().getName())
                    .showtimeSnapshot(showtime.getStartTime())
                    .unitPriceSnapshot(seatPrice)
                    .build();
            tickets.add(ticket);
        }

        booking.setSubtotalAmount(totalAmount);
        booking.setTotalAmount(totalAmount);
        booking.setTickets(tickets);

        // 6. Update SeatHold status
        for (SeatHold sh : userHolds) {
            if (request.getSeatIds().contains(sh.getSeat().getId())) {
                sh.setStatus(SeatHoldStatus.CONFIRMED);
            }
        }

        // 7. Save Booking (Cascade will save Tickets)
        bookingRepository.save(booking);

        // 8. Create Payment Transaction Log (CASH / internal log)
        PaymentTransaction tx = PaymentTransaction.builder()
                .booking(booking)
                .provider(null)
                .transactionCode(request.getPaymentMethod() == PaymentMethod.CASH ? "CASH-" + bookingCode : "BANKING-" + bookingCode)
                .requestPayload(String.format("{\"method\":\"%s\",\"amount\":%s}", request.getPaymentMethod(), totalAmount))
                .responsePayload("{\"status\":\"SUCCESS\",\"message\":\"Giao dịch tiền mặt thành công\"}")
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentTransactionRepository.save(tx);

        return booking;
    }

    @Transactional(readOnly = true)
    public Booking getBookingByCode(String code) {
        Booking booking = bookingRepository.findByBookingCode(code)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin đặt vé"));
        // Force load lazy associations
        booking.getUser().getEmail();
        booking.getTickets().forEach(t -> {
            t.getSeat().getSeatLabel();
        });
        return booking;
    }

    @Transactional(readOnly = true)
    public boolean isSoldOut(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy suất chiếu"));
        long totalSeats = seatRepository.countByRoomIdAndStatus(showtime.getRoom().getId(), "ACTIVE");
        long bookedSeats = ticketRepository.countByShowtimeIdAndTicketStatusIn(
                showtimeId, List.of(TicketStatus.BOOKED, TicketStatus.USED)
        );
        return bookedSeats >= totalSeats;
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> getSoldOutMap(List<Showtime> showtimes) {
        Map<Long, Boolean> map = new HashMap<>();
        for (Showtime showtime : showtimes) {
            map.put(showtime.getId(), isSoldOut(showtime.getId()));
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<Booking> getUserBookings(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        bookings.forEach(b -> {
            b.getTickets().forEach(t -> {
                t.getSeat().getSeatLabel();
            });
        });
        return bookings;
    }

    @Transactional
    public void cancelBooking(Long userId, String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin đặt vé"));

        if (!booking.getUser().getId().equals(userId)) {
            throw new BusinessException("Bạn không có quyền hủy đơn đặt vé này");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Đơn đặt vé không ở trạng thái có thể hủy");
        }

        if (booking.getTickets().isEmpty()) {
            throw new BusinessException("Đơn đặt vé không hợp lệ");
        }

        // Check if startTime is at least 24 hours in the future
        LocalDateTime showtimeStart = booking.getTickets().get(0).getShowtimeSnapshot();
        if (showtimeStart == null) {
            showtimeStart = booking.getTickets().get(0).getShowtime().getStartTime();
        }

        if (LocalDateTime.now().plusHours(24).isAfter(showtimeStart)) {
            throw new BusinessException("Chỉ được phép hủy vé trước giờ chiếu ít nhất 24 giờ");
        }

        // 1. Update booking status
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);
        booking.setCancelledAt(LocalDateTime.now());

        // 2. Update tickets status
        for (Ticket ticket : booking.getTickets()) {
            ticket.setTicketStatus(TicketStatus.CANCELLED);
        }

        // 3. Release seat holds (status = CONFIRMED)
        Long showtimeId = booking.getTickets().get(0).getShowtime().getId();
        List<Long> seatIds = booking.getTickets().stream()
                .map(t -> t.getSeat().getId())
                .collect(Collectors.toList());

        seatHoldRepository.deleteByShowtimeIdAndSeatIdAndStatusIn(
                showtimeId, seatIds, List.of(SeatHoldStatus.CONFIRMED)
        );

        // 4. Create Refund Payment Transaction Log
        PaymentTransaction tx = PaymentTransaction.builder()
                .booking(booking)
                .provider(null)
                .transactionCode("REFUND-" + bookingCode)
                .requestPayload(String.format("{\"method\":\"%s\",\"amount\":%s}", booking.getPaymentMethod(), booking.getTotalAmount()))
                .responsePayload("{\"status\":\"SUCCESS\",\"message\":\"Hoàn tiền giao dịch thành công\"}")
                .status(PaymentStatus.REFUNDED)
                .build();
        paymentTransactionRepository.save(tx);
    }

    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        seatHoldRepository.deleteExpiredHolds(now);
    }

    private String generateBookingCode() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = new Random().nextInt(900) + 100; // 3 digit random
        return "BK" + dateStr + rand;
    }
}
