package com.capstone.crm.security;

/**
 * The few claims we take from a verified Google ID token. Deliberately small:
 * the application identifies an account by its verified email, so nothing beyond
 * what that decision needs is carried past the verification boundary.
 */
public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String name) {}
