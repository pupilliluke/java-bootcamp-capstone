package com.capstone.crm.api;

import com.capstone.crm.api.dto.InteractionResponseDTO;
import com.capstone.crm.service.InteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/interactions")
public class CustomerInteractionController {

    private final InteractionService interactionService;

    public CustomerInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping
    public ResponseEntity<List<InteractionResponseDTO>> list(@PathVariable String customerId) {
        return ResponseEntity.ok(interactionService.listForCustomer(customerId));
    }
}
