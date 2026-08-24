package com.capstone.crm.security;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppUserAuthTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired CrmUserDetailsService userDetailsService;

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
        String token = login("admin1", "admin1");

        int status = mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();

        // Nothing is mapped under /api/admin yet, so the exact status is decided
        // by error handling rather than by security. The property under test is
        // that an ADMIN role from the database clears the rule that stops an
        // AGENT with 403 (see SecurityRulesTest.agentIsForbiddenFromAdminPaths).
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    void accountLookupIsByEmailForFederatedSignIn() {
        assertThat(users.findByEmailIgnoreCase("ADMIN1@EXAMPLE.TEST"))
                .isPresent()
                .get()
                .extracting(AppUser::getRole)
                .isEqualTo(UserRole.ADMIN);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
