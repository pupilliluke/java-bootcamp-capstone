package com.capstone.crm.api.dto;

/**
 * What a public registration returns.
 *
 * Deliberately not UserResponse. That record carries the row id and the creation
 * timestamp, and the id is a database identity column - a monotonic counter. An
 * anonymous caller who registers twice and subtracts the two ids learns how many
 * accounts were created in between, which is not something an unauthenticated
 * endpoint should be willing to tell anyone.
 *
 * A body is still required: the front-end http client returns early only for
 * 204, and parses every other 2xx as JSON. An empty 201 would surface as a parse
 * error to a user whose account was in fact created.
 */
public record RegistrationResponse(String username, String status) {

    public static RegistrationResponse pending(String username) {
        return new RegistrationResponse(username, "PENDING_APPROVAL");
    }
}
