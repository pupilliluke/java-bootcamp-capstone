package com.capstone.crm.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/google}. The single field is the ID token the
 * browser received from Google Identity Services (the {@code credential} in the
 * sign-in callback).
 */
public record GoogleLoginRequest(
        @NotBlank(message = "idToken is required") String idToken) {}
