package com.capstone.crm.api;

import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.messaging.producer.InteractionEventProducer;
import com.capstone.crm.repository.InteractionRepository;
import com.capstone.crm.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionControllerTest {

    private static final String CREATE_BODY = """
            {"customerId":"CUS-1001","channel":"EMAIL","notes":"Renewal follow-up"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired InteractionRepository interactions;

    @MockBean InteractionEventProducer producer;

    @BeforeEach
    void clearInteractions() {
        interactions.deleteAll();
    }

    @Test
    void agentCanCreateAndReadBackAnInteraction() throws Exception {
        String token = jwtService.issueToken("agent1", "AGENT");

        mockMvc.perform(post("/api/interactions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlation-Id", "journey-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-Id", "journey-test"))
                .andExpect(jsonPath("$.interactionId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("CUS-1001"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.notes").value("Renewal follow-up"));

        assertThat(interactions.count()).isEqualTo(1);
        verify(producer).publish(argThat((InteractionEvent event) ->
                event.customerId().equals("CUS-1001")
                        && event.channel().equals("EMAIL")
                        && event.notes().equals("Renewal follow-up")));

        mockMvc.perform(get("/api/customers/CUS-1001/interactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].interactionId").isNotEmpty())
                .andExpect(jsonPath("$[0].customerId").value("CUS-1001"))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].notes").value("Renewal follow-up"))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty());
    }

    @Test
    void anonymousUserCannotReadCustomerInteractions() throws Exception {
        mockMvc.perform(get("/api/customers/CUS-1001/interactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void interactionsForAnUnknownCustomerAreNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/CUS-9999/interactions")
                        .header("Authorization", "Bearer "
                                + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found: CUS-9999"));
    }
}
