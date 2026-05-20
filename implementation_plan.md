# Phân tích Schema SQL - Smart Cinema Booking System

## 1. Tổng quan đánh giá

Schema bạn thiết kế **khá tốt và khá hoàn chỉnh**, bao gồm đầy đủ các thực thể cốt lõi mà SRS yêu cầu. Bạn đã mở rộng vượt xa yêu cầu cơ bản bằng cách thêm các module cho:
- 🔄 Seat holds (chống double booking) 
- 🍿 Food/Combo 
- 💳 Payment integration 
- 💰 Refunds

Điều này cho thấy bạn đã thiết kế theo hướng **nhóm xuất sắc** (theo SRS mục 4 & 5).

---

## 2. Đánh giá từng nhóm bảng

### ✅ 2.1. USERS & AUTH (`users`, `user_profiles`)
**Đánh giá: Tốt**

- Tách `users` và `user_profiles` đúng theo gợi ý SRS (mục 3)
- `password_hash` — đáp ứng CORE-01 (băm mật khẩu)
- `role ENUM('CUSTOMER', 'STAFF', 'ADMIN')` — đáp ứng CORE-02 (phân quyền)
- `user_profiles` chứa thông tin cá nhân — đáp ứng CORE-03

> [!TIP]
> Có thể thêm trường `email_verified BOOLEAN DEFAULT FALSE` nếu muốn hỗ trợ flow xác thực email trong tương lai.

---

### ✅ 2.2. CINEMA / ROOM / SEAT (`cinemas`, `rooms`, `seats`, `seat_types`)

**Đánh giá: Rất tốt**

- Bảng `cinemas` — hợp lý, hỗ trợ multi-cinema (mở rộng)
- Bảng `rooms` có `room_type`, `capacity`, `cleanup_duration_minutes` — **rất tốt**, đáp ứng CORE-05 (kiểm tra xung đột, tính cả thời gian dọn phòng 15 phút như ví dụ SRS)
- Bảng `seat_types` với `price_multiplier` — thiết kế linh hoạt cho pricing (NORMAL, VIP, COUPLE)
- Bảng `seats` có `row_name`, `seat_number`, `seat_label` — đầy đủ

> [!NOTE]
> SRS mục CORE-04 nói rõ: "Phòng chiếu" và "Thể loại phim" sẽ được **hardcode (seed data)** trực tiếp trong Database. Vậy file `data.sql` cần có seed data cho `cinemas`, `rooms`, `seats`, `seat_types`, `genres`.

---

### ✅ 2.3. MOVIES (`movies`, `genres`, `movie_genres`)

**Đánh giá: Tốt**

- Quan hệ Many-to-Many giữa `movies` và `genres` qua `movie_genres` — chuẩn 3NF
- `movies` có đầy đủ thông tin: `title`, `description`, `duration_minutes`, `age_rating`, `poster_url`, `trailer_url`, `release_date`, `language`, `status`
- CORE-04: Admin quản lý CRUD cho phim ✅

---

### ✅ 2.4. SHOWTIME (`showtimes`)

**Đánh giá: Tốt**

- `start_time`, `end_time` — cho phép kiểm tra xung đột phòng (CORE-05)
- `base_price` — giá cơ bản cho suất chiếu
- `status` với nhiều trạng thái — đáp ứng CORE-08 (kiểm soát trạng thái suất chiếu)

> [!IMPORTANT]
> **Câu hỏi 1:** Trường `end_time` có được tự động tính từ `start_time + movie.duration_minutes + room.cleanup_duration_minutes` ở tầng Service không? Hay Admin phải nhập tay? Theo SRS CORE-05, `end_time` nên được tự động tính để đảm bảo logic chống xung đột chính xác.

---

### ✅ 2.5. SEAT HOLD (`seat_holds`)

**Đánh giá: Rất tốt - Thiết kế quan trọng**

- `UNIQUE(showtime_id, seat_id)` — chống double booking ✅
- `expired_at` — hỗ trợ giữ ghế tạm thời ✅
- Đáp ứng CORE-06 (tính toàn vẹn dữ liệu) và mở rộng Hướng 3 (Background Task/Cron Job quét ghế hết hạn)

> [!IMPORTANT]
> **Câu hỏi 2:** Constraint `UNIQUE(showtime_id, seat_id)` chỉ cho phép **1 record duy nhất** cho mỗi cặp (showtime, seat). Khi seat hold hết hạn (`status = 'EXPIRED'`), record cũ vẫn tồn tại và sẽ **block** người khác giữ ghế đó. Bạn có kế hoạch **xóa record cũ** hay **thay đổi thiết kế** (ví dụ: bỏ UNIQUE constraint, thay bằng partial unique index hoặc check ở tầng Service)?
>
> **Đề xuất:** Thêm điều kiện lọc ở tầng Service: chỉ kiểm tra `status = 'HOLDING'` khi validate, hoặc xóa record khi `EXPIRED`/`CANCELLED`.

---

### ✅ 2.6. BOOKING / ORDER (`bookings`)

**Đánh giá: Tốt**

- `booking_code` UNIQUE — mã đơn hàng cho staff tra cứu (CORE-07, mô tả Actor nhân viên)
- `subtotal_amount`, `discount_amount`, `total_amount` — tính toán đầy đủ
- `payment_status`, `payment_method`, `transaction_id`, `paid_at`, `cancelled_at` — hỗ trợ thanh toán và hủy vé

> [!NOTE]
> CORE-09 yêu cầu hủy vé trước 24h trước giờ chiếu. Logic này sẽ được xử lý ở tầng Service, không cần thêm trường trong DB.

---

### ✅ 2.7. TICKETS (`tickets`)

**Đánh giá: Rất tốt**

- Các trường `_snapshot` (`movie_title_snapshot`, `room_name_snapshot`, `seat_label_snapshot`, `seat_type_snapshot`, `showtime_snapshot`, `unit_price_snapshot`) — **thiết kế xuất sắc!** Đây là pattern "Event Sourcing / Snapshot" để đảm bảo lịch sử không bị ảnh hưởng khi data gốc thay đổi.
- Đáp ứng CORE-07 (tra cứu lịch sử với thông tin hoàn chỉnh)

> [!TIP]
> Có thể thêm `cinema_name_snapshot VARCHAR(255)` để hiển thị đầy đủ hơn trong hóa đơn lịch sử.

---

### ✅ 2.8. FOOD / COMBO (`products`, `booking_food_items`)

**Đánh giá: Tốt — Module mở rộng**

- `products` với `product_type ENUM` — linh hoạt
- `booking_food_items` có các trường `_snapshot` — nhất quán với thiết kế tickets

---

### ✅ 2.9. PAYMENT LOGS (`payment_transactions`)

**Đánh giá: Tốt — Module mở rộng Hướng 1**

- Hỗ trợ log request/response payload từ cổng thanh toán
- Đáp ứng Hướng 1 mở rộng (Payment Integration)

> [!WARNING]
> **Câu hỏi 3:** `payment_method` trong bảng `bookings` chứa `'CASH'` và `'BANKING'`, nhưng `provider` trong `payment_transactions` chỉ có `'VNPAY', 'MOMO', 'ZALOPAY'`. Thanh toán `CASH` và `BANKING` có cần log transaction không? Nếu có, nên thêm vào ENUM của `provider`, hoặc cho phép `provider` là NULL cho thanh toán offline.

---

### ✅ 2.10. REFUND (`refunds`)

**Đánh giá: Tốt**

- Hỗ trợ CORE-09 (hủy vé) với thông tin hoàn tiền chi tiết.

---

### ✅ 2.11. INDEXES

**Đánh giá: Tốt, nhưng có thể bổ sung**

```sql
-- Đã có:
CREATE INDEX idx_showtime_room_time ON showtimes(room_id, start_time);  -- Kiểm tra xung đột phòng
CREATE INDEX idx_ticket_showtime ON tickets(showtime_id);               -- Tra cứu vé theo suất chiếu
CREATE INDEX idx_booking_user ON bookings(user_id);                     -- Lịch sử đặt vé
CREATE INDEX idx_hold_expired ON seat_holds(expired_at);                -- Quét ghế hết hạn
```

> [!TIP]
> **Đề xuất thêm indexes:**
> ```sql
> CREATE INDEX idx_movie_status ON movies(status);               -- Lọc phim đang chiếu/sắp chiếu
> CREATE INDEX idx_showtime_movie ON showtimes(movie_id);        -- Tra cứu suất chiếu theo phim
> CREATE INDEX idx_showtime_status ON showtimes(status);         -- Lọc suất chiếu theo trạng thái
> CREATE INDEX idx_hold_showtime_seat ON seat_holds(showtime_id, seat_id, status); -- Query ghế đang giữ
> CREATE INDEX idx_booking_status ON bookings(status);           -- Lọc booking theo trạng thái
> CREATE INDEX idx_booking_code ON bookings(booking_code);       -- Tra cứu nhanh theo mã đơn (đã có UNIQUE)
> ```

---

## 3. Vấn đề cần lưu ý

### 🔴 Vấn đề 1: UNIQUE constraint trên `seat_holds`
Như đã đề cập ở câu hỏi 2, `UNIQUE(showtime_id, seat_id)` sẽ gây vấn đề khi cần re-hold một ghế đã từng được hold rồi expired. Cần strategy xử lý rõ ràng.

### 🟡 Vấn đề 2: Thiếu bảng `cinemas` trong SRS gốc
SRS không đề cập đến `cinemas` (hệ thống đơn rạp), nhưng bạn đã thêm — **đây là điểm cộng** cho khả năng mở rộng multi-cinema.

### 🟡 Vấn đề 3: Thống kê báo cáo
SRS Hướng 4 yêu cầu Dashboard Admin với thống kê doanh thu, Top 5 phim. Schema hiện tại đã đủ dữ liệu để viết các query thống kê (JOIN bookings, tickets, movies). **Không cần thêm bảng.**

---

## 4. Câu hỏi tổng hợp

> [!IMPORTANT]
> 1. **Về `end_time` trong `showtimes`:** Bạn muốn `end_time` được **tự động tính** (start_time + duration + cleanup) ở tầng Service, hay để Admin nhập tay?
> 
> 2. **Về UNIQUE constraint trên `seat_holds(showtime_id, seat_id)`:** Bạn có kế hoạch xóa record expired/cancelled, hay muốn thay đổi strategy? (Ví dụ: xóa record cũ trước khi INSERT mới)
>
> 3. **Về `payment_transactions.provider`:** Có cần hỗ trợ `CASH`/`BANKING` trong provider không? Hay bảng `payment_transactions` chỉ dùng cho thanh toán online?

---

## 5. Kế hoạch tạo file `data.sql`

File `data.sql` sẽ bao gồm:

1. **DROP & CREATE DATABASE** (tùy chọn)
2. **CREATE TABLE** — tất cả 11 nhóm bảng (giữ nguyên thiết kế của bạn, có chỉnh sửa nhỏ nếu được duyệt)
3. **Seed data cho:**
   - `seat_types` (NORMAL, VIP, COUPLE)
   - `genres` (Action, Comedy, Drama, Horror, Sci-Fi, Romance, Animation, Thriller, Fantasy, Documentary)
   - `cinemas` (1-2 rạp mẫu)
   - `rooms` (3-4 phòng mỗi rạp: STANDARD, IMAX, VIP, 4DX)
   - `seats` (ghế cho mỗi phòng, theo row A-J, 10-15 ghế/hàng)
   - `movies` (5-8 phim mẫu với đầy đủ thông tin)
   - `movie_genres` (liên kết phim-thể loại)
   - `products` (5-8 sản phẩm bắp nước/combo)
   - `users` (3 tài khoản: 1 admin, 1 staff, 1-2 customer, mật khẩu đã hash)
   - `user_profiles`
   - `showtimes` (5-10 suất chiếu mẫu)
4. **CREATE INDEX** — tất cả indexes + bổ sung
5. **Dữ liệu mẫu cho test** (bookings, tickets, v.v.) — tùy chọn

> [!IMPORTANT]
> Bạn có muốn tôi:
> - A) Giữ nguyên 100% schema hiện tại và chỉ tạo seed data?
> - B) Áp dụng các sửa đổi nhỏ đã đề xuất rồi tạo file hoàn chỉnh (CREATE TABLE + seed data)?
> - C) Muốn thêm/bớt seed data nào cụ thể?

---

## 6. Verification Plan

### Automated Tests
- Import file `data.sql` vào MySQL và kiểm tra không có lỗi
- Chạy các query JOIN cơ bản để kiểm tra tính toàn vẹn FK

### Manual Verification
- Kiểm tra seed data hiển thị đúng trên ứng dụng
