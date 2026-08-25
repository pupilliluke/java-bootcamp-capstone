package com.capstone.crm.security;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppUserAuthTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired CrmUserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void seederCreatesBothDemoAccountsWithHashedPasswords() {
        AppUser agent = users.findByUsername("agent1").orElseThrow();
        AppUser admin = users.findByUsername("admin1").orElseThrow();

        assertThat(agent.getRole()).isEqualTo(UserRole.AGENT);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        // The raw password must never be recoverable from the row.
        assertThat(agent.getPasswordHash()).isNotEqualTo("agent1").startsWith("$2");
    }

    @Test
    void rolesComeFromTheDatabaseRatherThanHardCodedNames() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin1");

        assertThat(admin.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void unknownUsernameIsRejectedByTheLookup() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void adminAccountIsNotForbiddenFromAdminOnlyPaths() throws Exception {
        String token = login("admin1", "admin1", "ADMIN");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void accountLookupIsByEmailForFederatedSignIn() {
        assertThat(users.findByEmailIgnoreCase("ADMIN1@EXAMPLE.TEST"))
                .isPresent()
                .get()
                .extracting(AppUser::getRole)
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        AppUser disabled = saveUser("disabled-login", "disabled-login@example.test", "password123", UserRole.AGENT);
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled-login\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsRejectedAfterAccountIsDisabled() throws Exception {
        AppUser user = saveUser("disabled-token", "disabled-token@example.test", "password123", UserRole.AGENT);
        String token = login("disabled-token", "password123", "AGENT");

        user.setEnabled(false);
        users.saveAndFlush(user);

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsRejectedAfterAccountIsDeleted() throws Exception {
        AppUser user = saveUser("deleted-token", "deleted-token@example.test", "password123", UserRole.AGENT);
        String token = login("deleted-token", "password123", "AGENT");

        users.delete(user);
        users.flush();

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingAdminTokenUsesCurrentDatabaseRoleAfterDemotion() throws Exception {
        AppUser user = saveUser("demoted-admin", "demoted-admin@example.test", "password123", UserRole.ADMIN);
        String token = login("demoted-admin", "password123", "ADMIN");

        user.setRole(UserRole.AGENT);
        users.saveAndFlush(user);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private AppUser saveUser(
            String username,
            String email,
            String rawPassword,
            UserRole role) {
        return users.saveAndFlush(new AppUser(
                username,
                email,
                passwordEncoder.encode(rawPassword),
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
