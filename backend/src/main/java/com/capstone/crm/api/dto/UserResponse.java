package com.capstone.crm.api.dto;

import com.capstone.crm.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        boolean enabled,
        Instant createdAt
) {}