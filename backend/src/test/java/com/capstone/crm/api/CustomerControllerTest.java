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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // --- closing is ADMIN-only however you reach it ---------------------------
    //
    // delete() is a soft delete: it sets CLOSED and keeps the record. So the
    // ADMIN rule on DELETE only holds if PUT cannot reach the same state, and
    // before this it could — the edit form offers CLOSED in its status dropdown
    // and any agent could pick it. These three pin the rule down: the transition
    // is refused, an admin may make it, and an already-closed customer stays
    // editable so the guard does not freeze the record.
    //
    // Each test makes its own customer. The repository is an in-memory map shared
    // across every test in this context, so reusing CUS-1001 would make the
    // result depend on which test ran first.
    @Test
    void agentIsForbiddenFromClosingACustomerThroughUpdate() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer("CUS-7001", agent);

        mockMvc.perform(put("/api/customers/CUS-7001")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Case Study", "CLOSED")))
                .andExpect(status().isForbidden())
                // The body matters, not just the status: the edit form renders
                // this string, so an agent sees why the save was refused rather
                // than a form that silently does nothing.
                .andExpect(jsonPath("$.message").value("Only an administrator can close a customer"));

        mockMvc.perform(get("/api/customers/CUS-7001")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void adminCanCloseACustomerThroughUpdate() throws Exception {
        String admin = jwtService.issueToken("admin1", "ADMIN");
        createCustomer("CUS-7002", admin);

        mockMvc.perform(put("/api/customers/CUS-7002")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Case Study", "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void agentCanStillEditACustomerThatIsAlreadyClosed() throws Exception {
        String admin = jwtService.issueToken("admin1", "ADMIN");
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer("CUS-7003", admin);

        mockMvc.perform(delete("/api/customers/CUS-7003")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/customers/CUS-7003")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Renamed While Closed", "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed While Closed"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private void createCustomer(String customerId, String token) throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","fullName":"Case Study","email":"case@example.test",\
                                "phone":"555-0000","status":"ACTIVE"}
                                """.formatted(customerId)))
                .andExpect(status().isCreated());
    }

    private static String bodyWithStatus(String fullName, String status) {
        return """
                {"fullName":"%s","email":"case@example.test","phone":"555-0000","status":"%s"}
                """.formatted(fullName, status);
    }
}
