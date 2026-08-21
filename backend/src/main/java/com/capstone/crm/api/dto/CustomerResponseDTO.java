package com.capstone.crm.api.dto;

import com.capstone.crm.entity.CustomerStatus;

import java.time.LocalDateTime;

public record CustomerResponseDTO(
        String customerId,
        String fullName,
        String email,
        String phone,
        CustomerStatus status,
        LocalDateTime createdAt
) {}
