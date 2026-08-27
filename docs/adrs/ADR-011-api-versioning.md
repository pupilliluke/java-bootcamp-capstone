# ADR-011: The REST API is versioned in the path, starting at /api/v1

- **Status:** Accepted
- **Date:** 2026-08-28
- **Deciders:** Team
- **Related backlog:** Issue #83

## Context

The Kafka side of the system has been versioned from the start — `version: 1`
in the event payload, `.v1` in the topic name, and ADR-010's rule for when
each moves. The REST surface had no equivalent: routes lived at `/api/...`
with no version, so there was no honest answer to "what happens to callers
when this contract changes." Labs 48, 49 and 51 pin the create route as
`POST /api/v1/interactions`, and Lab 48 asks for the compatibility rule to be
written down, not implied. The longer the prefix waited, the more it cost:
the demo script, the smoke checks, and the Q&A cards all quote URLs.

## Decision

Every application route moves under `/api/v1`: customers, interactions, auth,
and admin together. One surface, one version — a caller that can see the API
sees exactly one contract, and `auth` is not carved out as "infrastructure"
because a login response body is as much a contract as a customer row.
Actuator stays unversioned at `/actuator/...`; it is Spring Boot's contract
with the platform's probes, not ours with API callers.

The prefix is written per-controller in each `@RequestMapping`, not injected
by a server-level `PathMatchConfigurer`. Reading a controller tells you its
real route — with a configured prefix, `CustomerController` would say
`/api/customers` while serving `/api/v1/customers`, and every new reader
would have to learn why. The version also has to appear literally in
`SecurityConfig`'s matchers regardless, so central configuration would not
even buy a single point of change.

**Compatibility rule** (the REST mirror of ADR-010's event rule): additive
changes stay in v1 — a new endpoint, a new optional request field, a new
response field a caller is free to ignore. Any change an existing caller
would misread — renaming or removing a response field, retyping one,
tightening what a request accepts, changing a status code a client branches
on — ships as `/api/v2` beside `/api/v1`, and v1 keeps serving until its
callers are gone. Nothing mutates the meaning of a v1 route in place.
Tightening validation of what was always *documented* as invalid (issue #82's
channel enum) is not a break; the contract did not change, its enforcement
did.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Header or media-type versioning | URLs never change | Invisible in a browser bar, curl needs flags, smoke checks and demo script can no longer quote a URL that says its version | The course pins `/api/v1/...` paths, and a URL that carries its version is self-evidencing |
| B: Server-level prefix via `PathMatchConfigurer` | One place to change | The route a controller declares stops being the route it serves; the prefix still has to be repeated in security matchers | Explicit beats clever; there is no single point of change to be had |
| C: Keep `/api/...` and dual-map both prefixes during a transition | No flag-day for callers | Two names for every route, and matchers to keep in step for both | Nothing external depends on the old paths — the UI, tests, scripts and smoke checks all live in this repository and moved in the same commit |

## Consequences

- `POST /api/v1/interactions` is the create route everywhere: controllers,
  security matchers, the frontend client, the Playwright journey,
  `verify-setup.mjs`, `k8s/smoke.sh`, and the demo script quote the same
  string.
- The deployed course-cluster instance keeps answering on the old routes
  until its image is updated to a digest containing this change — update the
  deployment and the laptop UI together, or the Vite proxy will send `/api/v1`
  requests to a pod that only knows `/api`.
- Dated records (deploy evidence, verification transcripts, the rollback
  rehearsal) still quote `/api/...`. They are history and stay as written;
  only living documents moved.
- A future breaking change has a named procedure instead of a debate: build
  `/api/v2` beside v1, migrate callers, retire v1. The version bump is the
  visible artifact of the break, exactly as `.v2` would be on the topic.
