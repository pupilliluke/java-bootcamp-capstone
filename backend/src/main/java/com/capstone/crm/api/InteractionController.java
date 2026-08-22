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

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping
    public ResponseEntity<InteractionEvent> create(
            @Valid @RequestBody CreateInteractionRequest request
    ) {
        InteractionEvent event = interactionService.createAndPublish(request);
        return ResponseEntity.accepted().body(event);
    }
}