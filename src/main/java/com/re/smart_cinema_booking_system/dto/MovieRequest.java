package com.re.smart_cinema_booking_system.dto;

import com.re.smart_cinema_booking_system.enums.MovieStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieRequest {

    @NotBlank(message = "Tên phim không được để trống")
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull(message = "Thời lượng không được để trống")
    @Min(value = 1, message = "Thời lượng phải lớn hơn 0")
    @Max(value = 600, message = "Thời lượng tối đa 600 phút")
    private Integer durationMinutes;

    private String ageRating;

    @Size(max = 500)
    private String posterUrl;

    @Size(max = 500)
    private String trailerUrl;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseDate;

    private String language;

    @NotNull(message = "Trạng thái không được để trống")
    private MovieStatus status;

    private List<Long> genreIds;
}
