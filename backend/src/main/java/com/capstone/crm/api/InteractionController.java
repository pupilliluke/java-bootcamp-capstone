package com.capstone.crm.api;

import com.capstone.crm.api.dto.CreateInteractionRequest;
import com.capstone.crm.messaging.event.InteractionEvent;
import com.capstone.crm.observability.CorrelationIdFilter;
import com.capstone.crm.service.InteractionService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
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

    // The correlation id comes from CorrelationIdFilter, which runs at
    // HIGHEST_PRECEDENCE and has already resolved it — echoing the caller's
    // value or minting one — put it in the MDC, and set it on the response.
    //
    // This method used to do all of that a second time, and two bugs followed.
    // The browser journey caught the first:
    //
    //   1. ResponseEntity.header() appends rather than replaces, so the
    //      response carried X-Correlation-Id twice and a caller reading the
    //      header got "<id>, <id>".
    //   2. Worse when the caller sends no header. This method and the filter
    //      each fell back to their own UUID.randomUUID(), so the response
    //      advertised one id while the Kafka event carried a different one and
    //      the log line matched neither. An id that does not correlate is worse
    //      than no id, because it gets trusted.
    //
    // Reading the MDC keeps one source of truth: the event and the response now
    // carry the id the filter logged the request under.
    @PostMapping
    public ResponseEntity<InteractionEvent> create(@Valid @RequestBody CreateInteractionRequest request) {
        InteractionEvent event =
                interactionService.createAndPublish(request, MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.accepted().body(event);
    }
}
