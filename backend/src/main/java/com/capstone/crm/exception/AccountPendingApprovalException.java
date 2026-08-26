package com.capstone.crm.exception;

/**
 * A caller authenticated successfully with an external provider, but the matching
 * account is not enabled — either it was just provisioned by a first-time Google
 * sign-in, or it is still awaiting an administrator's approval. Distinct from
 * {@link InvalidCredentialsException} on purpose: the credentials were fine, so
 * this maps to 403 (approval outstanding), not 401 (who are you).
 */
public class AccountPendingApprovalException extends RuntimeException {
    public AccountPendingApprovalException(String message) {
        super(message);
    }
}
