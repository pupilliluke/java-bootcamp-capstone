package com.capstone.crm.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The health probes are the one unauthenticated surface
@SpringBootTest
@AutoConfigureMockMvc
class ProbeEndpointsTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // show-details is when-authorized: an anonymous probe learns the app is up and nothing more.
    @Test
    void anonymousHealthHidesComponentDetail() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    // The same endpoint, called with a token, does show the components
    @Test
    void authenticatedHealthShowsComponentDetail() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db").exists());
    }

    // Only the three probe paths are public. A per-component health path is not.
    @Test
    void healthComponentPathIsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }

    // Every other actuator endpoint stays off the anonymous surface entirely.
    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().is4xxClientError());
    }
}
