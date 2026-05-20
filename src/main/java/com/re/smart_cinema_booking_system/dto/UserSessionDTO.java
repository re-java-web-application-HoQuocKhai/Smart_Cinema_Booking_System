package com.re.smart_cinema_booking_system.dto;

import com.re.smart_cinema_booking_system.enums.UserRole;
import lombok.*;

import java.io.Serializable;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String email;
    private String fullName;
    private UserRole role;
}
