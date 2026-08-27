package com.capstone.crm.api;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

    private static final String VALID_CREATE_REQUEST = """
            {
              "username": "matrix-user",
              "email": "matrix-user@example.test",
              "password": "password123",
              "role": "AGENT"
            }
            """;

    private static final String VALID_UPDATE_REQUEST = """
            {
              "email": "matrix-updated@example.test",
              "role": "ADMIN",
              "enabled": true
            }
            """;

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void anonymousCannotAccessAnyAdminUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/users/{userId}", 1L))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_REQUEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/admin/users/{userId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_REQUEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/users/{userId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentCannotAccessAnyAdminUserEndpoint() throws Exception {
        String agentToken = login("agent1", "agent1", "AGENT");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users/{userId}", 1L)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/users/{userId}", 1L)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/users/{userId}", 1L)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin1')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'agent1')]").exists());
    }

    @Test
    void adminCanCreateUserAndPasswordIsStoredOnlyAsAHash() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "controller-created",
                                  "email": "controller-created@example.test",
                                  "password": "password123",
                                  "role": "AGENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("controller-created"))
                .andExpect(jsonPath("$.email").value("controller-created@example.test"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser saved = users.findByUsername("controller-created").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void invalidCreateRequestReturnsBadRequestWithoutSaving() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "email": "not-an-email",
                                  "password": "short",
                                  "role": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Validation failed")));

        assertThat(users.findByUsername("controller-created")).isEmpty();
    }

    @Test
    void malformedRoleReturnsBadRequest() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "invalid-role",
                                  "email": "invalid-role@example.test",
                                  "password": "password123",
                                  "role": "SUPERUSER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void duplicateUserReturnsConflict() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ADMIN1",
                                  "email": "unique@example.test",
                                  "password": "password123",
                                  "role": "AGENT"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Username is already in use")));
    }

    @Test
    void missingUserReturnsNotFound() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/users/{userId}", 999_999)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found: 999999"));
    }

    @Test
    void adminCanGetAndUpdateAnotherUser() throws Exception {
        AppUser target = saveUser("controller-update", "before@example.test", UserRole.AGENT);
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/users/{userId}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("controller-update"));

        mockMvc.perform(put("/api/v1/admin/users/{userId}", target.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "after@example.test",
                                  "role": "ADMIN",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("after@example.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(false));

        AppUser updated = users.findById(target.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("after@example.test");
        assertThat(updated.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void adminCanPromoteEnabledAgentToAdmin() throws Exception {
        AppUser agent = saveUser(
                "promotion-target",
                "promotion-target@example.test",
                UserRole.AGENT);
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(put("/api/v1/admin/users/{userId}", agent.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "promotion-target@example.test",
                                  "role": "ADMIN",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("promotion-target"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true));

        AppUser promoted = users.findById(agent.getId()).orElseThrow();
        assertThat(promoted.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(promoted.isEnabled()).isTrue();
    }

    @Test
    void adminCanDeleteAnotherUser() throws Exception {
        AppUser target = saveUser("controller-delete", "controller-delete@example.test", UserRole.AGENT);
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(delete("/api/v1/admin/users/{userId}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(users.findById(target.getId())).isEmpty();
    }

    @Test
    void adminCannotUpdateOrDeleteOwnAccount() throws Exception {
        AppUser admin = users.findByUsername("admin1").orElseThrow();
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(put("/api/v1/admin/users/{userId}", admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin1@example.test",
                                  "role": "ADMIN",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Administrators cannot update their own account"));

        mockMvc.perform(delete("/api/v1/admin/users/{userId}", admin.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Administrators cannot delete their own account"));
    }

    private AppUser saveUser(String username, String email, UserRole role) {
        return users.saveAndFlush(new AppUser(
                username,
                email,
                passwordEncoder.encode("password123"),
                role));
    }

    private String login(String username, String password, String expectedRole) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
