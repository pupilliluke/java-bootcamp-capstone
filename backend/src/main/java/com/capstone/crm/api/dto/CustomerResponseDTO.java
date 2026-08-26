package com.capstone.crm.api.dto;

import com.capstone.crm.entity.CustomerStatus;

import java.time.Instant;

public record CustomerResponseDTO(
        String customerId,
        String fullName,
        String email,
        String phone,
        CustomerStatus status,
        Instant createdAt
) {}
