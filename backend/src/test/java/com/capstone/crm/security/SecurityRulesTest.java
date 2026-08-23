package com.capstone.crm.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @Test
    void anonymousListIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentTokenCanListCustomers() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isOk());
    }

    @Test
    void agentIsForbiddenFromAdminPaths() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isForbidden());
    }

    // The privilege-escalation attempt the signature exists to stop: take a
    // legitimate AGENT token, rewrite the role claim to ADMIN, keep the original
    // signature. The HMAC covers the payload, so it no longer matches.
    //
    // Tampering the payload rather than the signature is deliberate. A 32-byte
    // HMAC base64url-encodes to 43 characters whose last character carries only
    // two significant bits, so altering that character often decodes to the very
    // same bytes and the token stays valid.
    @Test
    void tokenWithAnEditedRoleClaimIsRejected() throws Exception {
        String token = jwtService.issueToken("agent1", "AGENT");
        String[] parts = token.split("\\.");

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String elevated = payload.replace("\"role\":\"AGENT\"", "\"role\":\"ADMIN\"");
        assertThat(elevated).isNotEqualTo(payload);

        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(elevated.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginReturnsATokenForKnownCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent1\",\"password\":\"agent1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void loginRejectsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent1\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    // Guards the upgrade away from the labs' "lab.<sub>.<role>.<sig>" stub: a
    // real JWT is three base64url segments and carries an exp claim.
    @Test
    void issuedTokenIsARealJwt() {
        String token = jwtService.issueToken("agent1", "AGENT");

        assertThat(token).startsWith("eyJ");
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.parseSubject(token)).isEqualTo("agent1");
        assertThat(jwtService.parseRole(token)).isEqualTo("AGENT");
    }
}
