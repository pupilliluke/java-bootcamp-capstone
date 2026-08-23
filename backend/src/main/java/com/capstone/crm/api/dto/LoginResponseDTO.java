package com.capstone.crm.api.dto;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        String username,
        String role
) {}
