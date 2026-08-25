package com.capstone.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CrmUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CrmUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;

    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                String username = jwtService.parseSubject(token);
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (!user.isEnabled()) {
                    throw new DisabledException("Account disabled");
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Recorded here because Spring Security clears the context before
                // the outermost filter's finally block runs, so the request logger
                // cannot read it directly. MDC is per-thread and survives.
                MDC.put("user", username);
            } catch (RuntimeException ignored) {
                // A bad token leaves the request anonymous rather than failing it
                // here; the filter chain then answers 401 through the entry point.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
