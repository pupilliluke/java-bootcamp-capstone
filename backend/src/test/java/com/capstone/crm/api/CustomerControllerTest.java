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
    void deletingACustomerRemovesTheRow() throws Exception {
        String token = jwtService.issueToken("admin1", "ADMIN");
        createCustomer("CUS-7004", token);

        mockMvc.perform(delete("/api/customers/CUS-7004")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customers/CUS-7004")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
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

    // --- closing is an ordinary edit; deleting is not --------------------------
    //
    // delete() is a hard delete, admin-only (SecurityConfig gates the DELETE
    // verb). Closing a customer is a completely different operation — it is
    // just update() setting status to CLOSED — and is not role-restricted: any
    // agent can do it, the same as any other field edit.
    //
    // Each test makes its own customer. Customers are a real table now, shared by
    // every test class in the JVM through the H2 database, so reusing CUS-1001
    // would make the result depend on which test ran first.
    @Test
    void agentCanCloseACustomerThroughUpdate() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer("CUS-7001", agent);

        mockMvc.perform(put("/api/customers/CUS-7001")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("CUS-7001", "Case Study", "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void agentCanStillEditACustomerThatIsAlreadyClosed() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer("CUS-7003", agent);

        mockMvc.perform(put("/api/customers/CUS-7003")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("CUS-7003", "Case Study", "CLOSED")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/customers/CUS-7003")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("CUS-7003", "Renamed While Closed", "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed While Closed"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // --- customer ids are canonically upper case ------------------------------
    //
    // customer_id is a case-sensitive primary key, so "cus-8001" and "CUS-8001"
    // would otherwise be two customers that look like one on screen — and
    // interaction rows key off the same value, so history would split between
    // them. CustomerService upper-cases on the way in; ck_customer_id_upper in
    // V3__customer.sql is the backstop if anything ever bypasses it.
    @Test
    void aLowerCaseCustomerIdIsStoredAndAddressableInUpperCase() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cus-8001","fullName":"Lower Case","email":"cus-8001@example.test",\
                                "phone":"555-0000","status":"ACTIVE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("CUS-8001"));

        // Reachable by either spelling, and it is the same single record.
        mockMvc.perform(get("/api/customers/cus-8001")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUS-8001"));

        mockMvc.perform(get("/api/customers/CUS-8001")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUS-8001"));
    }

    @Test
    void theSameIdInAnotherCaseIsADuplicateRatherThanASecondCustomer() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer("CUS-8002", agent);

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cus-8002","fullName":"Case Twin","email":"twin@example.test",\
                                "phone":"555-0000","status":"ACTIVE"}
                                """))
                .andExpect(status().isConflict());
    }

    // Emails are derived from the customer id rather than shared, because
    // V3__customer.sql declares uq_customer_email. These customers used to live
    // in a ConcurrentHashMap that had no opinion about duplicates; against a real
    // table a shared address makes the second insert a 409.
    private void createCustomer(String customerId, String token) throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","fullName":"Case Study","email":"%s",\
                                "phone":"555-0000","status":"ACTIVE"}
                                """.formatted(customerId, emailFor(customerId))))
                .andExpect(status().isCreated());
    }

    private static String bodyWithStatus(String customerId, String fullName, String status) {
        return """
                {"fullName":"%s","email":"%s","phone":"555-0000","status":"%s"}
                """.formatted(fullName, emailFor(customerId), status);
    }

    private static String emailFor(String customerId) {
        return customerId.toLowerCase() + "@example.test";
    }
}
