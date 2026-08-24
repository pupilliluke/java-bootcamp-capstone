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

    @Test
    void wrongRoleUpdateIsForbidden() throws Exception {
        mockMvc.perform(put("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRoleDeleteIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER")))
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

    @Test
    void deletingAnUnknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/customers/CUS-9999")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isNotFound());
    }
}
