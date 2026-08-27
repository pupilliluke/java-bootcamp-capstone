package com.capstone.crm.api;

import com.capstone.crm.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        mockMvc.perform(put("/api/v1/customers/CUS-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDeleteIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/CUS-1001"))
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
        mockMvc.perform(put("/api/v1/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWithATokenForAnUnknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("viewer1", "VIEWER")))
                .andExpect(status().isUnauthorized());
    }

    // The difference between 401 and 403 in one pair of tests: agent1 is a real,
    // enabled account and is told who they are — they simply may not do this.
    // Deleting is admin-only; everything else on /api/v1/customers is not.
    @Test
    void agentIsForbiddenFromDeletingACustomer() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/CUS-1001")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCanUpdateAnExistingCustomer() throws Exception {
        mockMvc.perform(put("/api/v1/customers/CUS-1001")
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
        mockMvc.perform(put("/api/v1/customers/CUS-9999")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingACustomerRemovesTheRow() throws Exception {
        String token = jwtService.issueToken("admin1", "ADMIN");
        String customerId = createCustomer(token);

        mockMvc.perform(delete("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // admin1, not agent1: authorization is checked before the controller runs, so
    // an agent token would be refused with 403 and this would never reach the
    // lookup it is meant to test.
    @Test
    void deletingAnUnknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/CUS-9999")
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
        String customerId = createCustomer(agent);

        mockMvc.perform(put("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Case Study", randomEmail(), "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void agentCanStillEditACustomerThatIsAlreadyClosed() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String customerId = createCustomer(agent);

        mockMvc.perform(put("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Case Study", randomEmail(), "CLOSED")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Renamed While Closed", randomEmail(), "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed While Closed"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // --- customer ids are server-assigned and canonically upper case -----------
    //
    // customer_id is a case-sensitive primary key. CustomerService generates it
    // upper case from the start (see customer_number_seq), and the caller never
    // supplies one, but a URL path is still free-typed, so "cus-1010" and
    // "CUS-1010" must resolve to the same row rather than looking like two
    // customers. ck_customer_id_upper in V3__customer.sql is the backstop.
    @Test
    void aGeneratedCustomerIdIsAddressableInEitherCase() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String customerId = createCustomer(agent);

        mockMvc.perform(get("/api/v1/customers/" + customerId.toLowerCase())
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId));

        mockMvc.perform(get("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId));
    }

    // Replaces a case-collision test that no longer applies: a client can no
    // longer choose a customer_id to collide with, so this instead pins the
    // property that actually matters now — every created customer gets its own
    // distinct, sequential id.
    @Test
    void consecutiveCreatesGetDistinctSequentialIds() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");

        String first = createCustomer(agent);
        String second = createCustomer(agent);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("CUS-\\d+");
        assertThat(second).matches("CUS-\\d+");
        assertThat(Long.parseLong(second.substring(4)))
                .isGreaterThan(Long.parseLong(first.substring(4)));
    }

    // --- filtering the list by status ------------------------------------------
    //
    // Containment rather than exact lists, for the reason the block above
    // gives: one H2 database is shared by every test class in the JVM, so the
    // customer table holds the demo seed plus whatever the other tests have
    // created. What is provable is where the rows *this* test made show up, and
    // that no CLOSED customer from any source appears where none should.

    @Test
    void listExcludesClosedCustomersByDefault() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String open = createCustomer(agent);
        String closed = closedCustomer(agent);

        String body = listWith(agent, "?size=100");

        assertThat(statusesIn(body)).doesNotContain("CLOSED");
        assertThat(idsIn(body)).contains(open).doesNotContain(closed);
    }

    @Test
    void askingForClosedReturnsOnlyClosedCustomers() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String open = createCustomer(agent);
        String closed = closedCustomer(agent);

        String body = listWith(agent, "?status=CLOSED&size=100");

        assertThat(statusesIn(body)).containsOnly("CLOSED");
        assertThat(idsIn(body)).contains(closed).doesNotContain(open);
    }

    // The headline case from the issue: repeating the parameter asks for both
    // groups and gets exactly those two.
    @Test
    void repeatingTheStatusParameterReturnsBothGroups() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String active = createCustomer(agent);
        String closed = closedCustomer(agent);

        String body = listWith(agent, "?status=ACTIVE&status=CLOSED&size=100");

        assertThat(statusesIn(body)).containsOnly("ACTIVE", "CLOSED");
        assertThat(idsIn(body)).contains(active, closed);
    }

    // --- paging (Lab 39: bounded Pageable, sort allow-list) ----------------

    // The cap is the point: a caller asking for everything gets a page, not the
    // whole book. Without it one request can pull every row in the database.
    @Test
    void oversizedPageRequestIsCappedRatherThanHonoured() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer(agent);

        String body = listWith(agent, "?size=100000");

        assertThat((int) JsonPath.read(body, "$.size")).isEqualTo(100);
    }

    // Page metadata is what lets the UI render a pager without a second call.
    @Test
    void pageResponseCarriesTheTotalsAPagerNeeds() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer(agent);
        createCustomer(agent);

        String body = listWith(agent, "?page=0&size=1");

        assertThat((int) JsonPath.read(body, "$.page")).isZero();
        assertThat((int) JsonPath.read(body, "$.size")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(body, "$.totalElements")).longValue()).isGreaterThanOrEqualTo(2);
        assertThat((int) JsonPath.read(body, "$.totalPages")).isGreaterThanOrEqualTo(2);
        assertThat(idsIn(body)).hasSize(1);
    }

    // A page past the end is an empty page, not an error: deleting the last
    // customer on page nine should not turn the next refresh into a 500.
    @Test
    void pageBeyondTheEndIsEmptyRatherThanAnError() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer(agent);

        String body = listWith(agent, "?page=9999&size=20");

        assertThat(idsIn(body)).isEmpty();
    }

    // Sort is an allow-list. An unknown property is the caller's mistake, so
    // 400 -- letting it reach JPA as a property path would surface as a 500.
    @Test
    void unknownSortPropertyIsRejectedAsABadRequest() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");

        mockMvc.perform(get("/api/v1/customers?sort=passwordHash")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowedSortPropertyIsAccepted() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        createCustomer(agent);

        mockMvc.perform(get("/api/v1/customers?sort=fullName&direction=desc")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk());
    }

    // Search runs in the database, not the browser: filtering a page
    // client-side only ever searches the rows that page happens to hold.
    @Test
    void searchMatchesAcrossTheWholeBookNotJustOnePage() throws Exception {
        String agent = jwtService.issueToken("agent1", "AGENT");
        String wanted = createNamed(agent, "Zzyzx Findable");
        for (int i = 0; i < 3; i++) createCustomer(agent);

        String body = listWith(agent, "?q=zzyzx&size=5");

        assertThat(idsIn(body)).containsExactly(wanted);
    }

    // 400 rather than 500. Nothing in CustomerController produces this: Spring
    // cannot convert "VIP" to a CustomerStatus and raises
    // MethodArgumentTypeMismatchException, which GlobalExceptionHandler maps to
    // Bad Request. The endpoint depends on that mapping without stating it, so
    // this is here to fail if the handler is ever removed.
    @Test
    void anUnknownStatusValueIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/customers?status=VIP")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isBadRequest());
    }

    private String listWith(String token, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/customers" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> idsIn(String body) {
        // The list endpoint pages, so the rows are under content rather than at
        // the root of the response.
        return JsonPath.read(body, "$.content[*].customerId");
    }

    private static List<String> statusesIn(String body) {
        return JsonPath.read(body, "$.content[*].status");
    }

    // Created and then closed through update(), because that is how a customer
    // becomes CLOSED in this application — see the block above. The update body
    // carries a fresh email for the same reason createCustomer does.
    private String closedCustomer(String token) throws Exception {
        String customerId = createCustomer(token);
        mockMvc.perform(put("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus("Closed Customer", randomEmail(), "CLOSED")))
                .andExpect(status().isOk());
        return customerId;
    }

    // Emails are randomised rather than shared, because V3__customer.sql
    // declares uq_customer_email — a shared address makes the second insert a
    // 409, and customer_id is no longer available to derive one from.
    private String createCustomer(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Case Study","email":"%s","phone":"555-0000","status":"ACTIVE"}
                                """.formatted(randomEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.customerId");
    }

    // Same as createCustomer, with a name the search test can look for.
    private String createNamed(String token, String fullName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus(fullName, randomEmail(), "ACTIVE")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.customerId");
    }

    private static String bodyWithStatus(String fullName, String email, String status) {
        return """
                {"fullName":"%s","email":"%s","phone":"555-0000","status":"%s"}
                """.formatted(fullName, email, status);
    }

    private static String randomEmail() {
        return "customer-" + UUID.randomUUID() + "@example.test";
    }
}
