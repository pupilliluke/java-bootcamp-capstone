package com.capstone.crm.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a correlation id into the logging context for the life of each request.
 *
 * The log pattern in application.yml already prints %X{correlationId}, and
 * InteractionEventConsumer already fills it on the Kafka side — but nothing ever
 * filled it for HTTP, which is why every web log line reads "[]". This closes
 * that half, so one id follows a request from the browser through the API and
 * on into the consumer that handles its event.
 *
 * Runs first in the chain so the id is present before anything else logs,
 * including security rejections.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        MDC.put(MDC_KEY, correlationId);
        // Echoed back so the caller can quote it when reporting a problem, and so
        // the browser network tab shows the same id that appears in the logs.
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Cleared in a finally because the thread returns to a pool. Leaving it
            // set would stamp the next unrelated request with this id.
            MDC.remove(MDC_KEY);
            MDC.remove("user");
        }
    }
}
