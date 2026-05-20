package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.LoginRequest;
import com.re.smart_cinema_booking_system.dto.RegisterRequest;
import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.entity.User;
import com.re.smart_cinema_booking_system.entity.UserProfile;
import com.re.smart_cinema_booking_system.enums.UserRole;
import com.re.smart_cinema_booking_system.enums.UserStatus;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    @Transactional
    public UserSessionDTO register(RegisterRequest request) {
        // 1. Validate confirm password
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp");
        }

        // 2. Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã được sử dụng");
        }

        // 3. Hash password with BCrypt
        String passwordHash = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt(10));

        // 4. Create User entity
        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordHash)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        // 5. Create UserProfile entity
        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null && !request.getPhone().isBlank()
                        ? request.getPhone().trim() : null)
                .build();

        user.setProfile(profile);

        // 6. Save (cascade saves profile too)
        userRepository.save(user);

        // 7. Return session DTO
        return UserSessionDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .role(user.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public UserSessionDTO login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BusinessException("Email không tồn tại trong hệ thống"));

        // 2. Check account status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản đã bị khóa hoặc vô hiệu hóa");
        }

        // 3. Verify password
        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu không chính xác");
        }

        // 4. Build session DTO (access profile within transaction to avoid LazyInit)
        String fullName = (user.getProfile() != null && user.getProfile().getFullName() != null)
                ? user.getProfile().getFullName()
                : user.getEmail();

        return UserSessionDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(fullName)
                .role(user.getRole())
                .build();
    }
}
