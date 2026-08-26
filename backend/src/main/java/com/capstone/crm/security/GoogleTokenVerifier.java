package com.capstone.crm.security;

/**
 * Verifies a Google Sign-In ID token and returns the identity it carries.
 *
 * <p>Exists as an interface so the Google client library sits behind one seam:
 * {@code GoogleAuthService} depends on this, not on the SDK, which lets the
 * provisioning and token-issuing logic be unit tested without a network call or
 * a real Google token. The production implementation is
 * {@link GoogleIdTokenVerifierAdapter}.
 */
public interface GoogleTokenVerifier {

    /**
     * @param idToken the raw ID token string presented by the browser
     * @return the verified identity
     * @throws com.capstone.crm.exception.InvalidCredentialsException if the token
     *         is missing, malformed, expired, or fails signature/issuer/audience
     *         verification
     */
    GoogleIdentity verify(String idToken);
}
