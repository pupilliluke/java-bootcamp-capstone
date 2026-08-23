package com.capstone.crm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final String issuer;
    private final long expirationMinutes;

    public JwtService(
            @Value("${crm.security.jwt.secret}") String secret,
            @Value("${crm.security.jwt.issuer}") String issuer,
            @Value("${crm.security.jwt.expiration-minutes}") long expirationMinutes) {
        // Throws WeakKeyException for a secret under 256 bits, so a short secret
        // is a startup failure rather than a quietly forgeable token.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public String issueToken(String subject, String role) {
        if (subject == null || subject.isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("subject and role required");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(subject)
                .claim(ROLE_CLAIM, role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String parseSubject(String token) {
        return parse(token).getSubject();
    }

    public String parseRole(String token) {
        String role = parse(token).get(ROLE_CLAIM, String.class);
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("token carries no role claim");
        }
        return role;
    }

    // One pass verifies the signature, the issuer and exp. jjwt raises
    // JwtException for a forged signature and ExpiredJwtException once exp has
    // passed; both are wrapped so callers only handle one type.
    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing token");
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException ex) {
            throw new IllegalArgumentException("invalid token", ex);
        }
    }
}
