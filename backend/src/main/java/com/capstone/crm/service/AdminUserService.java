package com.capstone.crm.service;

import com.capstone.crm.api.dto.CreateUserRequest;
import com.capstone.crm.api.dto.UpdateUserRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.entity.AppUser;
import com.capstone.crm.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        AppUser user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        AppUser user = new AppUser(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()
        );

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(
            Long userId,
            UpdateUserRequest request,
            String actingUsername) {
        AppUser user = findUser(userId);

        if (user.getUsername().equals(actingUsername)) {
            throw new IllegalArgumentException(
                    "Administrators cannot update their own account");
        }

        user.setEmail(request.email());
        user.setRole(request.role());
        user.setEnabled(request.enabled());

        if (request.newPassword() != null
                && !request.newPassword().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(request.newPassword()));
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long userId, String actingUsername) {
        AppUser user = findUser(userId);

        if (user.getUsername().equals(actingUsername)) {
            throw new IllegalArgumentException(
                    "Administrators cannot delete their own account");
        }

        userRepository.delete(user);
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found: " + userId));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}