package com.capstone.crm.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One line per request: who, what, the status, and how long it took.
 *
 * Spring logs nothing about individual requests by default, so before this a 404
 * from the front end left no trace at all on the server — the only evidence a
 * request had ever arrived was DispatcherServlet initialising on the first one.
 *
 * Sits just inside CorrelationIdFilter so every line it writes already carries
 * the id, and outside security so rejected requests are logged too. A 401 or 403
 * is exactly the kind of thing worth seeing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("request");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startedAt = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            int status = response.getStatus();
            String query = request.getQueryString();

            // Read from MDC rather than the security context: by the time this
            // finally block runs, Spring Security has already cleared the context,
            // so every request would otherwise be logged as anonymous.
            String who = java.util.Optional.ofNullable(org.slf4j.MDC.get("user")).orElse("anonymous");

            String message = "{} {}{} -> {} as {} in {}ms";
            Object[] args = {
                    request.getMethod(),
                    request.getRequestURI(),
                    query == null ? "" : "?" + query,
                    status,
                    who,
                    tookMs,
            };

            // Client mistakes are worth noticing but are not our fault; server
            // errors are. Splitting them keeps a log filtered to WARN useful.
            if (status >= 500) {
                log.error(message, args);
            } else if (status >= 400) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
    }

    /** Health probes fire every few seconds and would drown everything else. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
}
