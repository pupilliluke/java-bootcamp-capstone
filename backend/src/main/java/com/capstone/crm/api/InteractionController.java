package com.capstone.crm.api;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.api.dto.InteractionResponseDTO;
import com.capstone.crm.observability.CorrelationIdFilter;
import com.capstone.crm.service.InteractionService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.security.Principal;


@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    // correlationId comes from the filter's MDC; the actor comes from the
    // authenticated principal Spring injects. Every request here is behind the
    // JWT filter, so principal is never null -- an unauthenticated caller is
    // rejected before reaching the controller.
    @PostMapping
    public ResponseEntity<InteractionResponseDTO> create(
            @Valid @RequestBody CreateInteractionRequest request, Principal principal) {
        InteractionResponseDTO response = interactionService.createAndPublish(
                request, MDC.get(CorrelationIdFilter.MDC_KEY), principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
