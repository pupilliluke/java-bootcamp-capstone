package com.capstone.crm.api;

import com.capstone.crm.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerTest {

    private static final String UPDATE_BODY = """
            {"fullName":"Amina K. Khan","email":"amina.khan@example.test","phone":"555-0199","status":"SUSPENDED"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @Test
    void anonymousUpdateIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/customers/CUS-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDeleteIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-1001"))
                .andExpect(status().isUnauthorized());
    }

    // These two used to expect 403, on the reasoning that a token carrying an
    // unknown role would authenticate and then be refused by the route rule.
    // JwtAuthenticationFilter now loads the account from the database instead of
    // trusting the token's claims, so "viewer1" — which is signed correctly but
    // names nobody — never becomes an authenticated principal at all. 401 is the
    // stronger answer: identity comes from the database, not from the bearer.
    //
    @Test
    void updateWithATokenForAnUnknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWithATokenForAnUnknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER")))
                .andExpect(status().isUnauthorized());
    }

    // The difference between 401 and 403 in one pair of tests: agent1 is a real,
    // enabled account and is told who they are — they simply may not do this.
    // Deleting is admin-only; everything else on /api/customers is not.
    @Test
    void agentIsForbiddenFromDeletingACustomer() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCanUpdateAnExistingCustomer() throws Exception {
        mockMvc.perform(put("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUS-1001"))
                .andExpect(jsonPath("$.fullName").value("Amina K. Khan"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updatingAnUnknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(put("/api/customers/CUS-9999")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingACustomerSoftDeletesRatherThanRemovingTheRow() throws Exception {
        String token = jwtService.issueToken("admin1", "ADMIN");

        mockMvc.perform(delete("/api/customers/CUS-1002")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customers/CUS-1002")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // admin1, not agent1: authorization is checked before the controller runs, so
    // an agent token would be refused with 403 and this would never reach the
    // lookup it is meant to test.
    @Test
    void deletingAnUnknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-9999")
                        .header("Authorization", "Bearer " + jwtService.issueToken("admin1", "ADMIN")))
                .andExpect(status().isNotFound());
    }
}
