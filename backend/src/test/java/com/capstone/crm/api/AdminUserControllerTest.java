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

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void usersEndpointEnforcesAnonymousAgentAdminAccessMatrix() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        String agentToken = login("agent1", "agent1", "AGENT");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        String adminToken = login("admin1", "admin1", "ADMIN");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin1')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'agent1')]").exists());
    }

    @Test
    void adminCanCreateUserAndPasswordIsStoredOnlyAsAHash() throws Exception {
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(post("/api/admin/users")
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

        mockMvc.perform(post("/api/admin/users")
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
    void adminCanGetAndUpdateAnotherUser() throws Exception {
        AppUser target = saveUser("controller-update", "before@example.test", UserRole.AGENT);
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(get("/api/admin/users/{userId}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("controller-update"));

        mockMvc.perform(put("/api/admin/users/{userId}", target.getId())
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
    void adminCanDeleteAnotherUser() throws Exception {
        AppUser target = saveUser("controller-delete", "controller-delete@example.test", UserRole.AGENT);
        String adminToken = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(delete("/api/admin/users/{userId}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(users.findById(target.getId())).isEmpty();
    }

    private AppUser saveUser(String username, String email, UserRole role) {
        return users.saveAndFlush(new AppUser(
                username,
                email,
                passwordEncoder.encode("password123"),
                role));
    }

    private String login(String username, String password, String expectedRole) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
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
