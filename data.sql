-- ================================================================
-- SMART CINEMA BOOKING SYSTEM - DATABASE SCHEMA & SEED DATA
-- ================================================================
-- Version : 1.0
-- Database: MySQL 8.x
-- Charset : utf8mb4
-- Date    : 2026-05-20
-- ==============================================   ==================
-- Ghi chú thiết kế:
--   • end_time trong showtimes: auto-calculate ở Service
--     (start_time + movie.duration_minutes + room.cleanup_duration_minutes)
--     rồi persist xuống DB.
--   • seat_holds: giữ UNIQUE(showtime_id, seat_id), Service sẽ xóa
--     record EXPIRED/CANCELLED trước khi INSERT mới, chỉ validate
--     status = 'HOLDING'.
--   • payment_transactions.provider: nullable, cho phép log internal
--     transaction cho CASH.
-- ================================================================

DROP DATABASE IF EXISTS smart_cinema_db;
CREATE DATABASE smart_cinema_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smart_cinema_db;

-- ================================================================
-- 1. USERS & AUTH
-- ================================================================

CREATE TABLE users (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('CUSTOMER', 'STAFF', 'ADMIN') NOT NULL,
    status          ENUM('ACTIVE', 'BLOCKED', 'DELETED') DEFAULT 'ACTIVE',
    email_verified  BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_profiles (
    user_id         BIGINT PRIMARY KEY,
    full_name       VARCHAR(255),
    phone           VARCHAR(20),
    date_of_birth   DATE,
    gender          ENUM('MALE', 'FEMALE', 'OTHER'),
    avatar_url      VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_profile_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ================================================================
-- 2. CINEMA / ROOM / SEAT
-- ================================================================

CREATE TABLE cinemas (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    address     TEXT,
    status      ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rooms (
    id                          BIGINT PRIMARY KEY AUTO_INCREMENT,
    cinema_id                   BIGINT NOT NULL,
    name                        VARCHAR(100) NOT NULL,
    room_type                   ENUM('STANDARD', 'IMAX', 'VIP', 'FOUR_DX') DEFAULT 'STANDARD',
    capacity                    INT NOT NULL,
    cleanup_duration_minutes    INT DEFAULT 15,
    status                      ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_cinema
        FOREIGN KEY (cinema_id) REFERENCES cinemas(id)
);

CREATE TABLE seat_types (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    code                VARCHAR(50) UNIQUE,
    name                VARCHAR(100),
    price_multiplier    DECIMAL(5,2) DEFAULT 1.00
);

CREATE TABLE seats (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id         BIGINT NOT NULL,
    row_name        VARCHAR(10),
    seat_number     INT,
    seat_label      VARCHAR(20),
    seat_type_id    BIGINT NOT NULL,
    status          ENUM('ACTIVE', 'DISABLED') DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_seat_room
        FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_seat_type
        FOREIGN KEY (seat_type_id) REFERENCES seat_types(id)
);

-- ================================================================
-- 3. MOVIES
-- ================================================================

CREATE TABLE genres (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE movies (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    duration_minutes    INT NOT NULL,
    age_rating          VARCHAR(20),
    poster_url          VARCHAR(500),
    trailer_url         VARCHAR(500),
    release_date        DATE,
    language            VARCHAR(100),
    status              ENUM('COMING_SOON', 'NOW_SHOWING', 'ENDED') DEFAULT 'COMING_SOON',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE movie_genres (
    movie_id    BIGINT,
    genre_id    BIGINT,

    PRIMARY KEY (movie_id, genre_id),

    FOREIGN KEY (movie_id) REFERENCES movies(id),
    FOREIGN KEY (genre_id) REFERENCES genres(id)
);

-- ================================================================
-- 4. SHOWTIME
-- ================================================================

CREATE TABLE showtimes (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    movie_id        BIGINT NOT NULL,
    room_id         BIGINT NOT NULL,
    start_time      DATETIME NOT NULL,
    end_time        DATETIME NOT NULL,  -- auto-calculated by Service: start_time + duration + cleanup
    status          ENUM(
                        'SCHEDULED',
                        'BOOKING_OPEN',
                        'SOLD_OUT',
                        'STARTED',
                        'FINISHED',
                        'CANCELLED'
                    ) DEFAULT 'SCHEDULED',
    base_price      DECIMAL(12,2) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_showtime_movie
        FOREIGN KEY (movie_id) REFERENCES movies(id),
    CONSTRAINT fk_showtime_room
        FOREIGN KEY (room_id) REFERENCES rooms(id)
);

-- ================================================================
-- 5. SEAT HOLD / CONCURRENCY
-- ================================================================
-- Bảng chống double booking + giữ ghế tạm 5-15 phút.
-- Strategy: giữ UNIQUE(showtime_id, seat_id).
-- Service sẽ DELETE record EXPIRED/CANCELLED trước khi INSERT mới.
-- Chỉ validate status = 'HOLDING' khi kiểm tra ghế còn trống.

CREATE TABLE seat_holds (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    showtime_id     BIGINT NOT NULL,
    seat_id         BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    status          ENUM('HOLDING', 'EXPIRED', 'CONFIRMED', 'CANCELLED') DEFAULT 'HOLDING',
    expired_at      DATETIME NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (showtime_id, seat_id),

    FOREIGN KEY (showtime_id) REFERENCES showtimes(id),
    FOREIGN KEY (seat_id)     REFERENCES seats(id),
    FOREIGN KEY (user_id)     REFERENCES users(id)
);

-- ================================================================
-- 6. BOOKING / ORDER
-- ================================================================

CREATE TABLE bookings (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_code        VARCHAR(50) UNIQUE NOT NULL,
    user_id             BIGINT NOT NULL,
    status              ENUM(
                            'PENDING_PAYMENT',
                            'CONFIRMED',
                            'CANCELLED',
                            'EXPIRED',
                            'REFUNDED'
                        ) DEFAULT 'PENDING_PAYMENT',
    subtotal_amount     DECIMAL(12,2) NOT NULL,
    discount_amount     DECIMAL(12,2) DEFAULT 0,
    total_amount        DECIMAL(12,2) NOT NULL,
    payment_status      ENUM('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED') DEFAULT 'PENDING',
    payment_method      ENUM('VNPAY', 'MOMO', 'ZALOPAY', 'CASH', 'BANKING'),
    transaction_id      VARCHAR(255),
    paid_at             DATETIME,
    cancelled_at        DATETIME,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ================================================================
-- 7. TICKETS (SNAPSHOT)
-- ================================================================
-- Lưu snapshot để lịch sử không bị ảnh hưởng khi data gốc thay đổi.

CREATE TABLE tickets (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id              BIGINT NOT NULL,
    showtime_id             BIGINT NOT NULL,
    seat_id                 BIGINT NOT NULL,
    ticket_status           ENUM('BOOKED', 'USED', 'CANCELLED', 'REFUNDED') DEFAULT 'BOOKED',
    cinema_name_snapshot    VARCHAR(255),
    movie_title_snapshot    VARCHAR(255),
    room_name_snapshot      VARCHAR(100),
    seat_label_snapshot     VARCHAR(20),
    seat_type_snapshot      VARCHAR(50),
    showtime_snapshot       DATETIME,
    unit_price_snapshot     DECIMAL(12,2) NOT NULL,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (booking_id)  REFERENCES bookings(id),
    FOREIGN KEY (showtime_id) REFERENCES showtimes(id),
    FOREIGN KEY (seat_id)     REFERENCES seats(id)
);

-- ================================================================
-- 8. FOOD / COMBO
-- ================================================================

CREATE TABLE products (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    product_type    ENUM('POPCORN', 'DRINK', 'COMBO', 'SNACK'),
    description     TEXT,
    current_price   DECIMAL(12,2) NOT NULL,
    image_url       VARCHAR(500),
    status          ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE booking_food_items (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id              BIGINT NOT NULL,
    product_id              BIGINT NOT NULL,
    quantity                INT NOT NULL,
    product_name_snapshot   VARCHAR(255),
    unit_price_snapshot     DECIMAL(12,2),
    subtotal_snapshot       DECIMAL(12,2),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- ================================================================
-- 9. PAYMENT LOGS
-- ================================================================
-- provider nullable: CASH và BANKING có thể log internal transaction
-- với provider = NULL.

CREATE TABLE payment_transactions (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id          BIGINT NOT NULL,
    provider            ENUM('VNPAY', 'MOMO', 'ZALOPAY') NULL DEFAULT NULL,
    transaction_code    VARCHAR(255),
    request_payload     TEXT,
    response_payload    TEXT,
    status              ENUM('PENDING', 'SUCCESS', 'FAILED'),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

-- ================================================================
-- 10. REFUND
-- ================================================================

CREATE TABLE refunds (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id      BIGINT NOT NULL,
    refund_amount   DECIMAL(12,2),
    refund_reason   TEXT,
    refund_status   ENUM('PENDING', 'SUCCESS', 'FAILED'),
    refunded_at     DATETIME,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

-- ================================================================
-- 11. INDEXES
-- ================================================================

-- Indexes gốc
CREATE INDEX idx_showtime_room_time     ON showtimes(room_id, start_time);
CREATE INDEX idx_ticket_showtime        ON tickets(showtime_id);
CREATE INDEX idx_booking_user           ON bookings(user_id);
CREATE INDEX idx_hold_expired           ON seat_holds(expired_at);

-- Indexes bổ sung
CREATE INDEX idx_movie_status           ON movies(status);
CREATE INDEX idx_showtime_movie         ON showtimes(movie_id);
CREATE INDEX idx_showtime_status        ON showtimes(status);
CREATE INDEX idx_hold_showtime_status   ON seat_holds(showtime_id, seat_id, status);
CREATE INDEX idx_booking_status         ON bookings(status);

-- ================================================================
-- ================================================================
--                        SEED DATA
-- ================================================================
-- ================================================================

-- ================================================================
-- SEAT TYPES
-- ================================================================

INSERT INTO seat_types (code, name, price_multiplier) VALUES
    ('NORMAL', 'Ghế Thường',    1.00),
    ('VIP',    'Ghế VIP',       1.30),
    ('COUPLE', 'Ghế Đôi',       2.20);

-- ================================================================
-- GENRES (Hardcode theo CORE-04)
-- ================================================================

INSERT INTO genres (name) VALUES
    ('Hành Động'),      -- 1
    ('Hài Hước'),       -- 2
    ('Tình Cảm'),       -- 3
    ('Kinh Dị'),        -- 4
    ('Khoa Học Viễn Tưởng'), -- 5
    ('Lãng Mạn'),       -- 6
    ('Hoạt Hình'),      -- 7
    ('Phiêu Lưu'),      -- 8
    ('Giả Tưởng'),      -- 9
    ('Tài Liệu'),       -- 10
    ('Tâm Lý'),         -- 11
    ('Gia Đình');        -- 12

-- ================================================================
-- CINEMAS (Hardcode)
-- ================================================================

INSERT INTO cinemas (name, address, status) VALUES
    ('CGV Vincom Center',   '72 Lê Thánh Tôn, Quận 1, TP. Hồ Chí Minh',    'ACTIVE'),
    ('Lotte Cinema Cantavil', '1 Điện Biên Phủ, Quận Bình Thạnh, TP. Hồ Chí Minh', 'ACTIVE');

-- ================================================================
-- ROOMS (Hardcode theo CORE-04)
-- ================================================================
-- Cinema 1 (CGV Vincom): 3 phòng
-- Cinema 2 (Lotte): 2 phòng

INSERT INTO rooms (cinema_id, name, room_type, capacity, cleanup_duration_minutes, status) VALUES
    -- CGV Vincom Center
    (1, 'Phòng 1', 'STANDARD', 40, 15, 'ACTIVE'),  -- id=1: 5 rows x 8 seats
    (1, 'Phòng 2', 'VIP',      18, 20, 'ACTIVE'),   -- id=2: 3 rows (A-B: 6 VIP, C: 3 COUPLE x2)
    (1, 'Phòng 3', 'IMAX',     60, 15, 'ACTIVE'),   -- id=3: 6 rows x 10 seats

    -- Lotte Cinema Cantavil
    (2, 'Phòng 1', 'STANDARD', 40, 15, 'ACTIVE'),   -- id=4: 5 rows x 8 seats
    (2, 'Phòng 2', 'FOUR_DX',  32, 20, 'ACTIVE');   -- id=5: 4 rows x 8 seats

-- ================================================================
-- SEATS (Hardcode)
-- ================================================================
-- seat_type_id: 1=NORMAL, 2=VIP, 3=COUPLE

-- ------------------------------------------------
-- Room 1 (CGV - Standard): Rows A-D NORMAL, Row E VIP
-- ------------------------------------------------
INSERT INTO seats (room_id, row_name, seat_number, seat_label, seat_type_id) VALUES
(1,'A',1,'A1',1),(1,'A',2,'A2',1),(1,'A',3,'A3',1),(1,'A',4,'A4',1),(1,'A',5,'A5',1),(1,'A',6,'A6',1),(1,'A',7,'A7',1),(1,'A',8,'A8',1),
(1,'B',1,'B1',1),(1,'B',2,'B2',1),(1,'B',3,'B3',1),(1,'B',4,'B4',1),(1,'B',5,'B5',1),(1,'B',6,'B6',1),(1,'B',7,'B7',1),(1,'B',8,'B8',1),
(1,'C',1,'C1',1),(1,'C',2,'C2',1),(1,'C',3,'C3',1),(1,'C',4,'C4',1),(1,'C',5,'C5',1),(1,'C',6,'C6',1),(1,'C',7,'C7',1),(1,'C',8,'C8',1),
(1,'D',1,'D1',1),(1,'D',2,'D2',1),(1,'D',3,'D3',1),(1,'D',4,'D4',1),(1,'D',5,'D5',1),(1,'D',6,'D6',1),(1,'D',7,'D7',1),(1,'D',8,'D8',1),
(1,'E',1,'E1',2),(1,'E',2,'E2',2),(1,'E',3,'E3',2),(1,'E',4,'E4',2),(1,'E',5,'E5',2),(1,'E',6,'E6',2),(1,'E',7,'E7',2),(1,'E',8,'E8',2);

-- ------------------------------------------------
-- Room 2 (CGV - VIP): Rows A-B VIP (6 ghế/hàng), Row C COUPLE (3 ghế đôi)
-- Capacity = 12 + 6 = 18 người, 15 seat units
-- ------------------------------------------------
INSERT INTO seats (room_id, row_name, seat_number, seat_label, seat_type_id) VALUES
(2,'A',1,'A1',2),(2,'A',2,'A2',2),(2,'A',3,'A3',2),(2,'A',4,'A4',2),(2,'A',5,'A5',2),(2,'A',6,'A6',2),
(2,'B',1,'B1',2),(2,'B',2,'B2',2),(2,'B',3,'B3',2),(2,'B',4,'B4',2),(2,'B',5,'B5',2),(2,'B',6,'B6',2),
(2,'C',1,'C1',3),(2,'C',2,'C2',3),(2,'C',3,'C3',3);

-- ------------------------------------------------
-- Room 3 (CGV - IMAX): Rows A-E NORMAL (10 ghế/hàng), Row F VIP (10 ghế)
-- ------------------------------------------------
INSERT INTO seats (room_id, row_name, seat_number, seat_label, seat_type_id) VALUES
(3,'A',1,'A1',1),(3,'A',2,'A2',1),(3,'A',3,'A3',1),(3,'A',4,'A4',1),(3,'A',5,'A5',1),(3,'A',6,'A6',1),(3,'A',7,'A7',1),(3,'A',8,'A8',1),(3,'A',9,'A9',1),(3,'A',10,'A10',1),
(3,'B',1,'B1',1),(3,'B',2,'B2',1),(3,'B',3,'B3',1),(3,'B',4,'B4',1),(3,'B',5,'B5',1),(3,'B',6,'B6',1),(3,'B',7,'B7',1),(3,'B',8,'B8',1),(3,'B',9,'B9',1),(3,'B',10,'B10',1),
(3,'C',1,'C1',1),(3,'C',2,'C2',1),(3,'C',3,'C3',1),(3,'C',4,'C4',1),(3,'C',5,'C5',1),(3,'C',6,'C6',1),(3,'C',7,'C7',1),(3,'C',8,'C8',1),(3,'C',9,'C9',1),(3,'C',10,'C10',1),
(3,'D',1,'D1',1),(3,'D',2,'D2',1),(3,'D',3,'D3',1),(3,'D',4,'D4',1),(3,'D',5,'D5',1),(3,'D',6,'D6',1),(3,'D',7,'D7',1),(3,'D',8,'D8',1),(3,'D',9,'D9',1),(3,'D',10,'D10',1),
(3,'E',1,'E1',1),(3,'E',2,'E2',1),(3,'E',3,'E3',1),(3,'E',4,'E4',1),(3,'E',5,'E5',1),(3,'E',6,'E6',1),(3,'E',7,'E7',1),(3,'E',8,'E8',1),(3,'E',9,'E9',1),(3,'E',10,'E10',1),
(3,'F',1,'F1',2),(3,'F',2,'F2',2),(3,'F',3,'F3',2),(3,'F',4,'F4',2),(3,'F',5,'F5',2),(3,'F',6,'F6',2),(3,'F',7,'F7',2),(3,'F',8,'F8',2),(3,'F',9,'F9',2),(3,'F',10,'F10',2);

-- ------------------------------------------------
-- Room 4 (Lotte - Standard): Rows A-D NORMAL, Row E VIP
-- ------------------------------------------------
INSERT INTO seats (room_id, row_name, seat_number, seat_label, seat_type_id) VALUES
(4,'A',1,'A1',1),(4,'A',2,'A2',1),(4,'A',3,'A3',1),(4,'A',4,'A4',1),(4,'A',5,'A5',1),(4,'A',6,'A6',1),(4,'A',7,'A7',1),(4,'A',8,'A8',1),
(4,'B',1,'B1',1),(4,'B',2,'B2',1),(4,'B',3,'B3',1),(4,'B',4,'B4',1),(4,'B',5,'B5',1),(4,'B',6,'B6',1),(4,'B',7,'B7',1),(4,'B',8,'B8',1),
(4,'C',1,'C1',1),(4,'C',2,'C2',1),(4,'C',3,'C3',1),(4,'C',4,'C4',1),(4,'C',5,'C5',1),(4,'C',6,'C6',1),(4,'C',7,'C7',1),(4,'C',8,'C8',1),
(4,'D',1,'D1',1),(4,'D',2,'D2',1),(4,'D',3,'D3',1),(4,'D',4,'D4',1),(4,'D',5,'D5',1),(4,'D',6,'D6',1),(4,'D',7,'D7',1),(4,'D',8,'D8',1),
(4,'E',1,'E1',2),(4,'E',2,'E2',2),(4,'E',3,'E3',2),(4,'E',4,'E4',2),(4,'E',5,'E5',2),(4,'E',6,'E6',2),(4,'E',7,'E7',2),(4,'E',8,'E8',2);

-- ------------------------------------------------
-- Room 5 (Lotte - 4DX): Rows A-C NORMAL, Row D VIP
-- ------------------------------------------------
INSERT INTO seats (room_id, row_name, seat_number, seat_label, seat_type_id) VALUES
(5,'A',1,'A1',1),(5,'A',2,'A2',1),(5,'A',3,'A3',1),(5,'A',4,'A4',1),(5,'A',5,'A5',1),(5,'A',6,'A6',1),(5,'A',7,'A7',1),(5,'A',8,'A8',1),
(5,'B',1,'B1',1),(5,'B',2,'B2',1),(5,'B',3,'B3',1),(5,'B',4,'B4',1),(5,'B',5,'B5',1),(5,'B',6,'B6',1),(5,'B',7,'B7',1),(5,'B',8,'B8',1),
(5,'C',1,'C1',1),(5,'C',2,'C2',1),(5,'C',3,'C3',1),(5,'C',4,'C4',1),(5,'C',5,'C5',1),(5,'C',6,'C6',1),(5,'C',7,'C7',1),(5,'C',8,'C8',1),
(5,'D',1,'D1',2),(5,'D',2,'D2',2),(5,'D',3,'D3',2),(5,'D',4,'D4',2),(5,'D',5,'D5',2),(5,'D',6,'D6',2),(5,'D',7,'D7',2),(5,'D',8,'D8',2);

-- ================================================================
-- MOVIES
-- ================================================================

INSERT INTO movies (title, description, duration_minutes, age_rating, poster_url, trailer_url, release_date, language, status) VALUES
(
    'Huyền Thoại Biển Xanh',
    'Câu chuyện phiêu lưu kỳ thú về một thợ lặn trẻ tuổi khám phá bí mật dưới đáy đại dương, nơi ẩn chứa nền văn minh cổ đại đã bị lãng quên.',
    135, 'P', '/images/movies/huyen-thoai-bien-xanh.jpg', 'https://youtube.com/watch?v=example1',
    '2026-05-01', 'Tiếng Việt', 'NOW_SHOWING'
),
(
    'Mắt Biếc 2: Ngày Trở Về',
    'Phần tiếp theo của câu chuyện tình yêu đầy day dứt. Ngạn trở về quê hương sau 20 năm xa cách, đối mặt với quá khứ và tìm kiếm sự bình yên.',
    125, 'T13', '/images/movies/mat-biec-2.jpg', 'https://youtube.com/watch?v=example2',
    '2026-05-10', 'Tiếng Việt', 'NOW_SHOWING'
),
(
    'Siêu Anh Hùng Sài Gòn',
    'Khi thành phố bị đe dọa bởi thế lực bóng tối, một nhóm thanh niên bình thường phát hiện sức mạnh phi thường và trở thành những siêu anh hùng đầu tiên của Việt Nam.',
    145, 'T13', '/images/movies/sieu-anh-hung-sai-gon.jpg', 'https://youtube.com/watch?v=example3',
    '2026-05-05', 'Tiếng Việt', 'NOW_SHOWING'
),
(
    'Đảo Kỳ Bí',
    'Một nhóm du khách bị mắc kẹt trên hòn đảo hoang sau khi tàu bị đắm. Họ phải đối mặt với những sinh vật kỳ lạ và bí ẩn rùng rợn của hòn đảo.',
    118, 'T16', '/images/movies/dao-ky-bi.jpg', 'https://youtube.com/watch?v=example4',
    '2026-04-20', 'Tiếng Việt', 'NOW_SHOWING'
),
(
    'Robot Nhí Phiêu Lưu Ký',
    'Một chú robot nhỏ tên là Bin cùng cô bé Lan bắt đầu hành trình phiêu lưu xuyên Việt Nam, học về tình bạn, lòng dũng cảm và ý nghĩa của gia đình.',
    95, 'P', '/images/movies/robot-nhi.jpg', 'https://youtube.com/watch?v=example5',
    '2026-05-15', 'Tiếng Việt', 'NOW_SHOWING'
),
(
    'Kẻ Săn Bóng Đêm',
    'Một thám tử tư trẻ tuổi lần theo dấu vết của kẻ giết người hàng loạt bí ẩn hoạt động trong đêm tối Sài Gòn. Sự thật cuối cùng khiến anh rùng mình.',
    130, 'T18', '/images/movies/ke-san-bong-dem.jpg', 'https://youtube.com/watch?v=example6',
    '2026-06-01', 'Tiếng Việt', 'COMING_SOON'
),
(
    'Vượt Thời Gian',
    'Một nhà khoa học thiên tài vô tình phát minh ra cỗ máy thời gian. Anh phải quay về quá khứ để ngăn chặn thảm họa sẽ xảy ra trong tương lai.',
    150, 'T13', '/images/movies/vuot-thoi-gian.jpg', 'https://youtube.com/watch?v=example7',
    '2026-06-15', 'Tiếng Việt', 'COMING_SOON'
),
(
    'Tình Yêu Trong Mưa',
    'Câu chuyện lãng mạn giữa một họa sĩ đường phố và một nữ bác sĩ. Họ gặp nhau trong một chiều mưa Hà Nội và bắt đầu mối tình đẹp nhưng đầy thử thách.',
    110, 'P', '/images/movies/tinh-yeu-trong-mua.jpg', 'https://youtube.com/watch?v=example8',
    '2026-06-20', 'Tiếng Việt', 'COMING_SOON'
);

-- ================================================================
-- MOVIE_GENRES (Many-to-Many)
-- ================================================================
-- Genres: 1=Hành Động, 2=Hài Hước, 3=Tình Cảm, 4=Kinh Dị, 5=Khoa Học Viễn Tưởng
--         6=Lãng Mạn, 7=Hoạt Hình, 8=Phiêu Lưu, 9=Giả Tưởng, 10=Tài Liệu
--         11=Tâm Lý, 12=Gia Đình

INSERT INTO movie_genres (movie_id, genre_id) VALUES
    -- Huyền Thoại Biển Xanh: Phiêu Lưu + Giả Tưởng
    (1, 8), (1, 9),
    -- Mắt Biếc 2: Tình Cảm + Tâm Lý
    (2, 3), (2, 11),
    -- Siêu Anh Hùng Sài Gòn: Hành Động + Khoa Học Viễn Tưởng
    (3, 1), (3, 5),
    -- Đảo Kỳ Bí: Phiêu Lưu + Kinh Dị
    (4, 8), (4, 4),
    -- Robot Nhí Phiêu Lưu Ký: Hoạt Hình + Gia Đình + Hài Hước
    (5, 7), (5, 12), (5, 2),
    -- Kẻ Săn Bóng Đêm: Kinh Dị + Tâm Lý
    (6, 4), (6, 11),
    -- Vượt Thời Gian: Khoa Học Viễn Tưởng + Hành Động
    (7, 5), (7, 1),
    -- Tình Yêu Trong Mưa: Lãng Mạn + Tình Cảm
    (8, 6), (8, 3);

-- ================================================================
-- PRODUCTS (Food / Combo)
-- ================================================================

INSERT INTO products (name, product_type, description, current_price, image_url, status) VALUES
    ('Bắp Rang Bơ Nhỏ',    'POPCORN', 'Bắp rang bơ size nhỏ (32oz)',             35000.00, '/images/products/popcorn-s.jpg', 'ACTIVE'),
    ('Bắp Rang Bơ Lớn',    'POPCORN', 'Bắp rang bơ size lớn (64oz)',             55000.00, '/images/products/popcorn-l.jpg', 'ACTIVE'),
    ('Bắp Phô Mai',        'POPCORN', 'Bắp rang phô mai đặc biệt (64oz)',        65000.00, '/images/products/popcorn-cheese.jpg', 'ACTIVE'),
    ('Coca Cola Lớn',       'DRINK',   'Coca Cola size lớn (32oz)',                32000.00, '/images/products/coca-l.jpg', 'ACTIVE'),
    ('Trà Đào',             'DRINK',   'Trà đào cam sả tươi mát',                 39000.00, '/images/products/tra-dao.jpg', 'ACTIVE'),
    ('Nước Suối',           'DRINK',   'Nước suối Aquafina 500ml',                 15000.00, '/images/products/water.jpg', 'ACTIVE'),
    ('Combo Couple',        'COMBO',   '2 Bắp Lớn + 2 Coca Lớn + 1 Snack Box',  149000.00, '/images/products/combo-couple.jpg', 'ACTIVE'),
    ('Combo Solo',          'COMBO',   '1 Bắp Nhỏ + 1 Coca Lớn',                  59000.00, '/images/products/combo-solo.jpg', 'ACTIVE'),
    ('Combo Family',        'COMBO',   '2 Bắp Lớn + 4 Coca + Nachos',            219000.00, '/images/products/combo-family.jpg', 'ACTIVE'),
    ('Nachos Phô Mai',      'SNACK',   'Nachos giòn tan với sốt phô mai',          49000.00, '/images/products/nachos.jpg', 'ACTIVE'),
    ('Hotdog Classic',      'SNACK',   'Hotdog xúc xích Đức với sốt mù tạt',      45000.00, '/images/products/hotdog.jpg', 'ACTIVE');

-- ================================================================
-- USERS
-- ================================================================
-- Mật khẩu test cho tất cả tài khoản: 123456
-- BCrypt hash (cost=10) của "123456" — đã verify bằng BCrypt.checkpw() = true
-- Hash được generate bằng: BCrypt.hashpw("123456", BCrypt.gensalt(10))
--
-- LƯU Ý: Trong production, cần generate hash bằng BCryptPasswordEncoder
-- của Spring Security. Hash dưới đây chỉ dùng cho development/testing.

INSERT INTO users (email, password_hash, role, status, email_verified) VALUES
    ('admin@cinema.vn',       '$2a$10$i6zOtszQ2FrJHPlWh4lekePI4TN/UtZBOGCi0UHvdpkYIav02QxzS', 'ADMIN',    'ACTIVE', TRUE),
    ('staff01@cinema.vn',     '$2a$10$i6zOtszQ2FrJHPlWh4lekePI4TN/UtZBOGCi0UHvdpkYIav02QxzS', 'STAFF',    'ACTIVE', TRUE),
    ('nguyenvana@gmail.com',  '$2a$10$i6zOtszQ2FrJHPlWh4lekePI4TN/UtZBOGCi0UHvdpkYIav02QxzS', 'CUSTOMER', 'ACTIVE', TRUE),
    ('tranthib@gmail.com',    '$2a$10$i6zOtszQ2FrJHPlWh4lekePI4TN/UtZBOGCi0UHvdpkYIav02QxzS', 'CUSTOMER', 'ACTIVE', TRUE),
    ('levanc@gmail.com',      '$2a$10$i6zOtszQ2FrJHPlWh4lekePI4TN/UtZBOGCi0UHvdpkYIav02QxzS', 'CUSTOMER', 'ACTIVE', FALSE);

-- ================================================================
-- USER PROFILES
-- ================================================================

INSERT INTO user_profiles (user_id, full_name, phone, date_of_birth, gender) VALUES
    (1, 'Quản Trị Viên',   '0901000001', '1990-01-15', 'MALE'),
    (2, 'Nhân Viên Quầy',  '0901000002', '1995-06-20', 'FEMALE'),
    (3, 'Nguyễn Văn A',    '0912345678', '1998-03-10', 'MALE'),
    (4, 'Trần Thị B',      '0923456789', '2000-11-25', 'FEMALE'),
    (5, 'Lê Văn C',        '0934567890', '1997-07-05', 'MALE');

-- ================================================================
-- SHOWTIMES
-- ================================================================
-- end_time được tính: start_time + movie.duration_minutes + room.cleanup_duration_minutes
-- Ví dụ: Movie 1 (135 phút) + Room 1 (cleanup 15 phút) = 150 phút sau start_time
--
-- Phim 1 (Huyền Thoại Biển Xanh): 135 phút
-- Phim 2 (Mắt Biếc 2): 125 phút
-- Phim 3 (Siêu Anh Hùng Sài Gòn): 145 phút
-- Phim 4 (Đảo Kỳ Bí): 118 phút
-- Phim 5 (Robot Nhí): 95 phút

INSERT INTO showtimes (movie_id, room_id, start_time, end_time, status, base_price) VALUES
    -- === Suất chiếu ĐÃ QUA (FINISHED) - dùng để test lịch sử ===

    -- 17/05: Huyền Thoại Biển Xanh - Room 1 (Standard, cleanup 15p)
    -- end = 08:00 + 135 + 15 = 10:30
    (1, 1, '2026-05-17 08:00:00', '2026-05-17 10:30:00', 'FINISHED', 75000.00),   -- id=1

    -- 17/05: Mắt Biếc 2 - Room 1 (Standard, cleanup 15p) - suất chiều
    -- end = 14:00 + 125 + 15 = 16:20
    (2, 1, '2026-05-17 14:00:00', '2026-05-17 16:20:00', 'FINISHED', 85000.00),   -- id=2

    -- 18/05: Siêu Anh Hùng Sài Gòn - Room 3 (IMAX, cleanup 15p)
    -- end = 19:00 + 145 + 15 = 21:40
    (3, 3, '2026-05-18 19:00:00', '2026-05-18 21:40:00', 'FINISHED', 120000.00),  -- id=3

    -- === Suất chiếu HÔM NAY & SẮP TỚI (BOOKING_OPEN) ===

    -- 20/05: Huyền Thoại Biển Xanh - Room 3 (IMAX) - suất tối
    -- end = 20:00 + 135 + 15 = 22:30
    (1, 3, '2026-05-20 20:00:00', '2026-05-20 22:30:00', 'BOOKING_OPEN', 120000.00), -- id=4

    -- 21/05: Đảo Kỳ Bí - Room 1 (Standard) - suất sáng
    -- end = 09:30 + 118 + 15 = 11:43
    (4, 1, '2026-05-21 09:30:00', '2026-05-21 11:43:00', 'BOOKING_OPEN', 75000.00),  -- id=5

    -- 21/05: Robot Nhí - Room 1 (Standard) - suất chiều (sau Đảo Kỳ Bí)
    -- end = 13:00 + 95 + 15 = 14:50
    (5, 1, '2026-05-21 13:00:00', '2026-05-21 14:50:00', 'BOOKING_OPEN', 65000.00),  -- id=6

    -- 22/05: Mắt Biếc 2 - Room 2 (VIP, cleanup 20p)
    -- end = 19:30 + 125 + 20 = 21:55
    (2, 2, '2026-05-22 19:30:00', '2026-05-22 21:55:00', 'BOOKING_OPEN', 150000.00), -- id=7

    -- 22/05: Siêu Anh Hùng Sài Gòn - Room 5 (4DX Lotte, cleanup 20p)
    -- end = 18:00 + 145 + 20 = 20:45
    (3, 5, '2026-05-22 18:00:00', '2026-05-22 20:45:00', 'BOOKING_OPEN', 140000.00), -- id=8

    -- === Suất chiếu TƯƠNG LAI (SCHEDULED) ===

    -- 25/05: Huyền Thoại Biển Xanh - Room 4 (Standard Lotte, cleanup 15p)
    -- end = 10:00 + 135 + 15 = 12:30
    (1, 4, '2026-05-25 10:00:00', '2026-05-25 12:30:00', 'SCHEDULED', 80000.00),  -- id=9

    -- 25/05: Robot Nhí - Room 4 (Standard Lotte) - suất chiều (sau Huyền Thoại)
    -- end = 14:00 + 95 + 15 = 15:50
    (5, 4, '2026-05-25 14:00:00', '2026-05-25 15:50:00', 'SCHEDULED', 65000.00);  -- id=10

-- ================================================================
-- SAMPLE BOOKINGS (Dữ liệu mẫu cho test)
-- ================================================================

-- Booking 1: Nguyễn Văn A (user_id=3) đặt 2 vé + 1 combo cho suất chiếu đã qua (id=1)
-- Ghế A1, A2 (NORMAL) trong Room 1 Standard
-- Giá vé: 75000 × 1.0 × 2 = 150000
-- Combo Solo: 59000
-- Tổng: 209000

INSERT INTO bookings (booking_code, user_id, status, subtotal_amount, discount_amount, total_amount, payment_status, payment_method, transaction_id, paid_at) VALUES
    ('BK20260517001', 3, 'CONFIRMED', 209000.00, 0.00, 209000.00, 'SUCCESS', 'VNPAY', 'VNP14265078', '2026-05-17 07:35:00');

-- Tickets cho Booking 1
-- Room 1, seats: A1 (seat id=1), A2 (seat id=2) — xem lại seat IDs dựa trên INSERT order
INSERT INTO tickets (booking_id, showtime_id, seat_id, ticket_status,
                     cinema_name_snapshot, movie_title_snapshot, room_name_snapshot,
                     seat_label_snapshot, seat_type_snapshot, showtime_snapshot, unit_price_snapshot) VALUES
    (1, 1, 1, 'USED', 'CGV Vincom Center', 'Huyền Thoại Biển Xanh', 'Phòng 1', 'A1', 'NORMAL', '2026-05-17 08:00:00', 75000.00),
    (1, 1, 2, 'USED', 'CGV Vincom Center', 'Huyền Thoại Biển Xanh', 'Phòng 1', 'A2', 'NORMAL', '2026-05-17 08:00:00', 75000.00);

-- Food items cho Booking 1
INSERT INTO booking_food_items (booking_id, product_id, quantity, product_name_snapshot, unit_price_snapshot, subtotal_snapshot) VALUES
    (1, 8, 1, 'Combo Solo', 59000.00, 59000.00);

-- Payment transaction cho Booking 1
INSERT INTO payment_transactions (booking_id, provider, transaction_code, request_payload, response_payload, status) VALUES
    (1, 'VNPAY', 'VNP14265078', '{"amount":209000,"orderInfo":"BK20260517001"}', '{"responseCode":"00","message":"Success"}', 'SUCCESS');

-- ------------------------------------------------

-- Booking 2: Trần Thị B (user_id=4) đặt 3 vé cho suất chiếu IMAX đã qua (id=3)
-- Ghế F1, F2, F3 (VIP) trong Room 3 IMAX
-- Giá vé: 120000 × 1.3 × 3 = 468000
-- Combo Couple: 149000
-- Tổng: 617000

INSERT INTO bookings (booking_code, user_id, status, subtotal_amount, discount_amount, total_amount, payment_status, payment_method, transaction_id, paid_at) VALUES
    ('BK20260518001', 4, 'CONFIRMED', 617000.00, 0.00, 617000.00, 'SUCCESS', 'MOMO', 'MOMO98765432', '2026-05-18 18:20:00');

-- Tickets cho Booking 2
-- Room 3 IMAX, Row F (VIP seats): F1=seat 91, F2=seat 92, F3=seat 93
-- (Room 3 starts at seat id 56, rows A-E = 50 NORMAL seats, F starts at id 106)
-- Thực tế seat id phụ thuộc vào thứ tự INSERT, Room 1 = 40 seats (id 1-40),
-- Room 2 = 15 seats (id 41-55), Room 3 bắt đầu id 56
-- Room 3: A=56-65, B=66-75, C=76-85, D=86-95, E=96-105, F=106-115
INSERT INTO tickets (booking_id, showtime_id, seat_id, ticket_status,
                     cinema_name_snapshot, movie_title_snapshot, room_name_snapshot,
                     seat_label_snapshot, seat_type_snapshot, showtime_snapshot, unit_price_snapshot) VALUES
    (2, 3, 106, 'USED', 'CGV Vincom Center', 'Siêu Anh Hùng Sài Gòn', 'Phòng 3', 'F1', 'VIP', '2026-05-18 19:00:00', 156000.00),
    (2, 3, 107, 'USED', 'CGV Vincom Center', 'Siêu Anh Hùng Sài Gòn', 'Phòng 3', 'F2', 'VIP', '2026-05-18 19:00:00', 156000.00),
    (2, 3, 108, 'USED', 'CGV Vincom Center', 'Siêu Anh Hùng Sài Gòn', 'Phòng 3', 'F3', 'VIP', '2026-05-18 19:00:00', 156000.00);

-- Food items cho Booking 2
INSERT INTO booking_food_items (booking_id, product_id, quantity, product_name_snapshot, unit_price_snapshot, subtotal_snapshot) VALUES
    (2, 7, 1, 'Combo Couple', 149000.00, 149000.00);

-- Payment transaction cho Booking 2
INSERT INTO payment_transactions (booking_id, provider, transaction_code, request_payload, response_payload, status) VALUES
    (2, 'MOMO', 'MOMO98765432', '{"amount":617000,"orderInfo":"BK20260518001"}', '{"resultCode":0,"message":"Thành công"}', 'SUCCESS');

-- ------------------------------------------------

-- Booking 3: Nguyễn Văn A (user_id=3) đặt vé cho suất chiếu sắp tới (id=4) - PENDING_PAYMENT
-- Ghế A5 (NORMAL) trong Room 3 IMAX
-- Giá vé: 120000 × 1.0 = 120000

INSERT INTO bookings (booking_code, user_id, status, subtotal_amount, discount_amount, total_amount, payment_status, payment_method) VALUES
    ('BK20260520001', 3, 'PENDING_PAYMENT', 120000.00, 0.00, 120000.00, 'PENDING', 'VNPAY');

-- Ticket cho Booking 3 (pending)
-- Room 3 IMAX, A5 = seat id 60 (56 + 4)
INSERT INTO tickets (booking_id, showtime_id, seat_id, ticket_status,
                     cinema_name_snapshot, movie_title_snapshot, room_name_snapshot,
                     seat_label_snapshot, seat_type_snapshot, showtime_snapshot, unit_price_snapshot) VALUES
    (3, 4, 60, 'BOOKED', 'CGV Vincom Center', 'Huyền Thoại Biển Xanh', 'Phòng 3', 'A5', 'NORMAL', '2026-05-20 20:00:00', 120000.00);

-- Seat hold cho Booking 3 (đang giữ ghế)
INSERT INTO seat_holds (showtime_id, seat_id, user_id, status, expired_at) VALUES
    (4, 60, 3, 'HOLDING', '2026-05-20 07:45:00');

-- Payment transaction (pending)
INSERT INTO payment_transactions (booking_id, provider, transaction_code, status) VALUES
    (3, 'VNPAY', NULL, 'PENDING');

-- ------------------------------------------------

-- Booking 4: Lê Văn C (user_id=5) đặt vé CASH tại quầy cho suất chiếu (id=5)
-- Ghế B3 (NORMAL) trong Room 1 Standard
-- Giá vé: 75000 × 1.0 = 75000

INSERT INTO bookings (booking_code, user_id, status, subtotal_amount, discount_amount, total_amount, payment_status, payment_method, paid_at) VALUES
    ('BK20260521001', 5, 'CONFIRMED', 75000.00, 0.00, 75000.00, 'SUCCESS', 'CASH', '2026-05-20 10:00:00');

-- Ticket cho Booking 4
-- Room 1, B3 = seat id 11 (8 + 3)
INSERT INTO tickets (booking_id, showtime_id, seat_id, ticket_status,
                     cinema_name_snapshot, movie_title_snapshot, room_name_snapshot,
                     seat_label_snapshot, seat_type_snapshot, showtime_snapshot, unit_price_snapshot) VALUES
    (4, 5, 11, 'BOOKED', 'CGV Vincom Center', 'Đảo Kỳ Bí', 'Phòng 1', 'B3', 'NORMAL', '2026-05-21 09:30:00', 75000.00);

-- Payment transaction cho CASH (provider = NULL, log internal)
INSERT INTO payment_transactions (booking_id, provider, transaction_code, request_payload, status) VALUES
    (4, NULL, 'CASH-BK20260521001', '{"method":"CASH","cashier":"staff01@cinema.vn"}', 'SUCCESS');

-- ================================================================
-- END OF SEED DATA
-- ================================================================

-- Update posterUrl từ local paths sang internet URLs
-- Chạy script này trong MySQL để cập nhật poster URLs
UPDATE movies SET poster_url = 'https://picsum.photos/seed/ocean-legend/400/600' WHERE poster_url = '/images/movies/huyen-thoai-bien-xanh.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/dreamy-eyes/400/600' WHERE poster_url = '/images/movies/mat-biec-2.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/superhero-city/400/600' WHERE poster_url = '/images/movies/sieu-anh-hung-sai-gon.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/mysterious-island/400/600' WHERE poster_url = '/images/movies/dao-ky-bi.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/little-robot/400/600' WHERE poster_url = '/images/movies/robot-nhi.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/night-hunter/400/600' WHERE poster_url = '/images/movies/ke-san-bong-dem.jpg';
UPDATE movies SET poster_url = 'https://picsum.photos/seed/rain-love/400/600' WHERE poster_url = '/images/movies/tinh-yeu-trong-mua.jpg';