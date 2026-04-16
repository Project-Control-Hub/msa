package com.pch.auth.service;

import com.pch.auth.domain.User;
import com.pch.auth.dto.UpdateProfileRequest;
import com.pch.auth.dto.UserProfileResponse;
import com.pch.auth.repository.UserRepository;
import com.pch.common.dto.UserSummaryDto;
import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user = findById(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findById(userId);
        user.updateProfile(request.name(), request.profileImageUrl());
        return UserProfileResponse.from(user);
    }

    // ── Internal API 용 ──

    public UserSummaryDto getSummary(Long userId) {
        User user = findById(userId);
        return new UserSummaryDto(user.getId(), user.getEmail(), user.getName());
    }

    public List<UserSummaryDto> getSummariesByIds(List<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getEmail(), u.getName()))
                .toList();
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
