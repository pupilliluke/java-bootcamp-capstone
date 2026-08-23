package com.capstone.crm.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    // Flipping the last character invalidates the HMAC, which is the whole point
    // of signing: the role claim cannot be edited by whoever holds the token.
    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = jwtService.issueToken("agent1", "AGENT");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + tampered))
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
