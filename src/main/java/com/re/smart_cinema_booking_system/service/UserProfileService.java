package com.re.smart_cinema_booking_system.service;

import com.re.smart_cinema_booking_system.dto.ProfileUpdateRequest;
import com.re.smart_cinema_booking_system.dto.UserSessionDTO;
import com.re.smart_cinema_booking_system.entity.User;
import com.re.smart_cinema_booking_system.entity.UserProfile;
import com.re.smart_cinema_booking_system.exception.BusinessException;
import com.re.smart_cinema_booking_system.repository.UserProfileRepository;
import com.re.smart_cinema_booking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public ProfileUpdateRequest getProfile(Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ người dùng"));

        ProfileUpdateRequest dto = new ProfileUpdateRequest();
        dto.setFullName(profile.getFullName());
        dto.setPhone(profile.getPhone());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setGender(profile.getGender());
        return dto;
    }

    @Transactional
    public UserSessionDTO updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            user.setProfile(profile);
        }

        profile.setFullName(request.getFullName().trim());
        profile.setPhone(request.getPhone() != null && !request.getPhone().isBlank()
                ? request.getPhone().trim() : null);
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setGender(request.getGender());

        userRepository.save(user);

        return UserSessionDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .role(user.getRole())
                .build();
    }
}
