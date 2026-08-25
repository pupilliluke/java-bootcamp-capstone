package com.capstone.crm.api;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers issue #16 end to end: someone registers, is refused at login, an
 * administrator approves them, and only then can they sign in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerTest {

    private static final String NEW_ACCOUNT = """
            {
              "username": "hopeful-agent",
              "email": "hopeful-agent@example.test",
              "password": "correct-horse-battery"
            }
            """;

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;

    @Test
    void anonymousCanRegisterAndTheAccountStartsDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ACCOUNT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("hopeful-agent"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                // The response carries no id and no timestamp. id is an identity
                // column, so two registrations and a subtraction would tell an
                // anonymous caller how many accounts exist.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());

        // Asserted against the stored row rather than the echoed body: what
        // matters is what was persisted, not what the endpoint chose to say.
        AppUser saved = users.findByUsername("hopeful-agent").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.AGENT);
        assertThat(saved.isEnabled()).isFalse();
        // The raw password must never be what is stored.
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery");
    }

    /** The approval gate: registering is not the same as being let in. */
    @Test
    void aRegisteredButUnapprovedAccountCannotSignIn() throws Exception {
        register();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hopeful-agent","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRoleCannotBeChosenByTheCaller() throws Exception {
        // An extra "role" field is not part of the contract; the account must
        // still come out as an AGENT rather than an ADMIN.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "sneaky-user",
                                  "email": "sneaky@example.test",
                                  "password": "correct-horse-battery",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated());

        // The role a caller asked for is discarded, and the check is on the row.
        AppUser saved = users.findByUsername("sneaky-user").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.AGENT);
        assertThat(saved.isEnabled()).isFalse();
    }

    @Test
    void rejectsADuplicateUsername() throws Exception {
        register();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ACCOUNT))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAPasswordShorterThanTwelveCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "short-pass",
                                  "email": "short-pass@example.test",
                                  "password": "tooshort"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentCannotApproveAnAccount() throws Exception {
        register();
        Long pendingId = users.findByUsername("hopeful-agent").orElseThrow().getId();
        String agentToken = login("agent1", "agent1");

        mockMvc.perform(patch("/api/admin/users/{userId}/enable", pendingId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users/pending")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotApproveAnAccount() throws Exception {
        register();
        Long pendingId = users.findByUsername("hopeful-agent").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/users/{userId}/enable", pendingId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesThePendingAccountApprovesItAndThenItCanSignIn() throws Exception {
        register();
        Long pendingId = users.findByUsername("hopeful-agent").orElseThrow().getId();
        String adminToken = login("admin1", "admin1");

        mockMvc.perform(get("/api/admin/users/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'hopeful-agent')]").exists());

        mockMvc.perform(patch("/api/admin/users/{userId}/enable", pendingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // The whole point of the issue: approval is what turns a 401 into a 200.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hopeful-agent","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));

        // ...and once approved it leaves the queue.
        mockMvc.perform(get("/api/admin/users/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'hopeful-agent')]").doesNotExist());
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ACCOUNT))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
