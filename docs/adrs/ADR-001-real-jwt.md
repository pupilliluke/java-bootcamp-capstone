# ADR-001: Real JWT instead of the lab's stub

- **Status:** Proposed | Accepted | Superseded
- **Date:** 08-24-2026
- **Deciders:** Luke Pupilli
- **Related backlog:** Issue #32

## Context

What force is driving this decision? (scale, consistency, security, ops, team skill, timebox)

The capstone needs authentication evidence that can withstand direct API calls. The server must reject forged, expired, malformed, and incorrectly issued tokens. Authorization also needs current account information so a disabled user or changed role takes effect during an existing session.

The React frontend needs a bearer token that it can attach to protected API requests. The current application does not require a distributed login session store.

_____

## Decision

We will issue signed JSON Web Tokens using JJWT and HS256.

## Alternatives considered

| Option                                          | Pros                                                         | Cons                                                                                           | Why not                                                 |
|-------------------------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| A — Lab token stub                              | Small implementation and easy local debugging                | Callers can construct or change tokens without a valid signature                               | It cannot prove authentication or protect role claims   |
| B — Server-side sessions                        | Central session invalidation and small browser cookies       | Requires session state, cookie configuration, and a shared session store for multiple replicas | The API and frontend already use bearer-token requests  |
| C — External OAuth2 or OpenID Connect provider  | Delegates authentication and security to an identity service | Requires provider configuration, callback handling, and environment-specific credentials       | The capstone schedule prioritizes the CRM delivery flow |                                                              | _____ |

## Consequences

- **Positive:** Forged tokens fail signature verification. Expired tokens fail during parsing.The issuer check prevents tokens from another issuer from being accepted. Disabled accounts and role changes take effect because the filter reloads the user.
- **Negative / follow-ups:** Rotating `JWT_SECRET` invalidates every token issued with the previous key. The application has no refresh-token flow. Users sign in again after expiration. Every deployed instance must receive the same signing secret.
- **NFR impact:** Supports authentication, authorization, and traceability requirements.
- **Evidence later labs will need:** Successful login, protected-route access, anonymous 401, wrong-role 403, expired-token rejection, and forged-token rejection.

## Links

- JWT Backend Service: `backend/src/main/java/com/capstone/crm/security/JwtService.java`
- Authentication Filter: `backend/src/main/java/com/capstone/crm/security/JwtAuthenticationFilter.java`
- Security Configuration: `backend/src/main/java/com/capstone/crm/config/SecurityConfig.java`
- Security Rules Test: `backend/src/test/java/com/capstone/crm/security/SecurityRulesTest.java`
- User Authentication test: `backend/src/test/java/com/capstone/crm/security/AppUserAuthTest.java`
- Pom.xml: `backend/pom.xml`
