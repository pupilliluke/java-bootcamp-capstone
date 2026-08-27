package com.capstone.crm.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Negative authentication
@SpringBootTest
@AutoConfigureMockMvc
class ForgedTokenSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @Value("${crm.security.jwt.secret}")
    String secret;

    private static final String ISSUER = "capstone-crm";

    @Test
    void aGenuineTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + jwtService.issueToken("agent1", "AGENT")))
                .andExpect(status().isOk());
    }

    // The HMAC check is the whole defence.
    @Test
    void aTokenSignedWithADifferentKeyIsRejected() throws Exception {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "an-entirely-different-secret-that-is-long-enough".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("agent1")
                .claim("role", "ADMIN")
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(attackerKey, SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // The alg:none downgrade
    @Test
    void anUnsignedAlgNoneTokenIsRejected() throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(
                ("{\"iss\":\"" + ISSUER + "\",\"sub\":\"agent1\",\"role\":\"ADMIN\"}")
                        .getBytes(StandardCharsets.UTF_8));
        String forged = header + "." + payload + ".";

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // A real token with its signature segment overwritten by junk.
    @Test
    void aTokenWithAGarbledSignatureIsRejected() throws Exception {
        String token = jwtService.issueToken("agent1", "AGENT");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("not-the-real-signature".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // Correctly signed, but exp is in the past.
    @Test
    void anExpiredButOtherwiseValidTokenIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("agent1")
                .claim("role", "AGENT")
                .setIssuedAt(Date.from(past.minusSeconds(60)))
                .setExpiration(Date.from(past))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // Correctly signed with the right key, but minted under a different issuer.
    @Test
    void aTokenFromAnotherIssuerIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String wrongIssuer = Jwts.builder()
                .setIssuer("some-other-service")
                .setSubject("agent1")
                .claim("role", "AGENT")
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());
    }

    // A bearer value that is not a JWT at all is turned away, not met with a 500.
    @Test
    void aBearerHeaderThatIsNotAJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
