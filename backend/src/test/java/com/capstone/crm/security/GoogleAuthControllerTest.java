package com.capstone.crm.security;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.exception.InvalidCredentialsException;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The Google sign-in endpoint end to end through the security filter chain, with
// the Google SDK replaced by a mock so no network call or real token is needed.
//
// Deliberately NOT @Transactional: the provisioning path must be observed after
// it commits, exactly as production sees it. A test transaction would let the
// assertion read the service's own uncommitted insert and hide a rollback bug,
// which is precisely what once slipped through. Accounts created here are cleaned
// up explicitly instead.
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAuthControllerTest {

    private static final String PROVISIONED_EMAIL = "newcomer@example.test";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;

    @AfterEach
    void removeProvisionedAccount() {
        users.findByEmailIgnoreCase(PROVISIONED_EMAIL).ifPresent(users::delete);
    }

    // Replaces GoogleIdTokenVerifierAdapter in the context, so verification is
    // whatever each test says it is.
    @MockBean GoogleTokenVerifier verifier;

    private static String body(String idToken) {
        return "{\"idToken\":\"" + idToken + "\"}";
    }

    private static GoogleIdentity identity(String email, boolean verified) {
        return new GoogleIdentity("sub-1", email, verified, "Test Person");
    }

    @Test
    void aVerifiedTokenForASeededAccountReturnsAnAccessToken() throws Exception {
        when(verifier.verify(anyString())).thenReturn(identity("agent1@example.test", true));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("google-id-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("agent1"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aFirstTimeSignInProvisionsADisabledAgentAndIsForbidden() throws Exception {
        when(verifier.verify(anyString())).thenReturn(identity(PROVISIONED_EMAIL, true));
        assertThat(users.findByEmailIgnoreCase(PROVISIONED_EMAIL)).isEmpty();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("google-id-token")))
                .andExpect(status().isForbidden());

        // Read after the request completes and its transaction commits: the
        // account must actually be there for an admin to approve, not merely have
        // existed inside a since-rolled-back transaction.
        AppUser created = users.findByEmailIgnoreCase(PROVISIONED_EMAIL).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.AGENT);
        assertThat(created.isEnabled()).isFalse();
        assertThat(created.getPasswordHash()).isNull();
    }

    @Test
    void anUnverifiedEmailIsUnauthorized() throws Exception {
        when(verifier.verify(anyString())).thenReturn(identity("unverified@example.test", false));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("google-id-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anInvalidTokenIsUnauthorized() throws Exception {
        when(verifier.verify(anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid Google token"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingIdTokenIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
    }
}
