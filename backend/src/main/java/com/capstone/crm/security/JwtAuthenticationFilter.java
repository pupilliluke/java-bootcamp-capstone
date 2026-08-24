package com.capstone.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                var authentication = new UsernamePasswordAuthenticationToken(
                        jwtService.parseSubject(token),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + jwtService.parseRole(token))));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Recorded here because Spring Security clears the context before
                // the outermost filter's finally block runs, so the request logger
                // cannot read it directly. MDC is per-thread and survives.
                MDC.put("user", jwtService.parseSubject(token));
            } catch (RuntimeException ignored) {
                // A bad token leaves the request anonymous rather than failing it
                // here; the filter chain then answers 401 through the entry point.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
