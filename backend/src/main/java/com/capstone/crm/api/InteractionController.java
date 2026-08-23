package com.capstone.crm.api;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;


@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping
    public ResponseEntity<InteractionEvent> create(
            @Valid @RequestBody CreateInteractionRequest request,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId
    ) {
        String resolvedCorrelationId = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId;
        InteractionEvent event = interactionService.createAndPublish(request, resolvedCorrelationId);
        return ResponseEntity.accepted()
                .header(CORRELATION_ID_HEADER, resolvedCorrelationId)
                .body(event);
    }
}