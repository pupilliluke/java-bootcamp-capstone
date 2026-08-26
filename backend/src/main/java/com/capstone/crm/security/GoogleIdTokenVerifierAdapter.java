package com.capstone.crm.security;

import com.capstone.crm.exception.InvalidCredentialsException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Production {@link GoogleTokenVerifier}, backed by Google's own
 * {@link GoogleIdTokenVerifier}. That verifier checks the token's RS256
 * signature against Google's public keys (which it fetches and caches, honouring
 * their rotation), the {@code iss}, the {@code exp}, and — because an audience is
 * configured — that the token was minted for our OAuth client and not some other
 * app that also uses Google Sign-In.
 */
@Component
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierAdapter(@Value("${crm.security.google.client-id}") String clientId) {
        // Construction is offline; keys are fetched lazily on the first verify.
        // Setting the audience is what makes a token minted for a different
        // client id fail here, so it is not optional.
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidCredentialsException("Missing Google token");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            // verify() returns null — rather than throwing — when the token is
            // well formed but does not check out (bad signature, wrong audience,
            // expired). Treat that the same as any other invalid token.
            if (token == null) {
                throw new InvalidCredentialsException("Invalid Google token");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            return new GoogleIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    (String) payload.get("name"));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            // IllegalArgumentException covers a string that is not even a JWS, so
            // a garbage value is a rejected login rather than a 500.
            throw new InvalidCredentialsException("Could not verify Google token");
        }
    }
}
