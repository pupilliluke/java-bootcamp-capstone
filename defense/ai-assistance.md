# Containerising the backend: where AI helped, and where I rejected it

Lab 10's ask, for the container work on the `docker-image` branch. The assistant
was Claude Code. Written as the order it happened in, because that is the point.

## The short version

1. **It was green.** Container built, ran as non-root, reported healthy. Looked done.
2. **Green was not proof.** Nothing had checked anything I would claim at a defense.
3. **I asked for tests and rejected the first answer.** A shell script checks a
   container once. Four questions need four tools: the Dockerfile (hadolint), the
   image (container-structure-test), its packages (Trivy), its behaviour
   (Testcontainers).
4. **It went red: 16 of 23.**
5. **Half those failures were the tests' fault, half were real.** Telling them
   apart was the work.
6. **The real ones mattered:** an image that couldn't be traced to a commit, a
   comment in our own Dockerfile that was untrue, and a readiness probe that said
   UP with the database deleted.
7. **Fixed both sides, verified by measurement.** 22/22, 15/15, 60/60.

One sentence: *going green is easy; the value was making it go red on purpose and
finding out which half was lying.*

## Whose fault was each failure

**Mine (generated tests that were wrong):**

- `metadataTest.env:` — wrong key, it's `envVars`.
- `expectedOutput: ['^10001$']` failed against output that was `10001`. Go's `$`
  matches end of text, not before a trailing newline. Needed `(?m)`.
- `exitCode: 1` for `command -v javac`, which returned `127` — `/bin/sh` is dash,
  and dash exits 127 where bash exits 1.
- A `*.pem` secret sweep that flagged ~150 public CA certificates.
- `trivy-action@0.28.0` and `hadolint-action@v3.1.0`, both written from memory,
  both stale.

**Real (nothing else would have caught these):**

- `Metadata Test — FAIL: revision value 'unknown'`. Built without
  `--build-arg GIT_SHA`, so the image couldn't be traced to a commit.
- `no HTTP client in the runtime image — FAIL: stdout /usr/bin/curl`. I had that
  assertion written from our own Dockerfile comment, which claimed the `/dev/tcp`
  healthcheck avoided installing curl. `eclipse-temurin:21-jre` already ships it.
  A hand-written comment had been wrong and unchallenged.
- `readinessFollowsTheDatabase — ERROR: HttpTimeout`. Led to the finding below.

## What I fixed in the product

- **Readiness couldn't see the database.** Proved it by deleting the Postgres
  container from under a healthy app — readiness still said `UP`. Added `db` to
  the readiness group, and deliberately *not* to liveness: failing liveness
  restarts the container, and restarting never fixes a database.
- **The fixed probe was slow.** Measured `503` at **31.0s**, HikariCP's 30s
  default. Now 10s, with the measurement in the comment.
- **Lab 41's "pinned digest rather than a tag" was unmet.** Both stages now pin
  by SHA256, verified with `docker manifest inspect` first.
- **A test edit cost a 10-minute rebuild.** `COPY src ./src` included `src/test`,
  and `-DskipTests` still compiles tests. Excluded it, switched to
  `-Dmaven.test.skip=true`: **10 min → 2.6s**.
- **The pipeline would have broken the team.** I had Trivy failing the build, but
  the image carries 8 critical / 41 high, nearly all from Spring Boot 3.3.5 being
  18 months old — every PR would have gone red on merge day. Report-only until
  the version bump.

## How I verified

- **Measured, didn't assert** — 31.0s, 2.6s, 94 kB context are numbers I took.
- **Looked up, didn't remember** — action versions against the GitHub releases
  API; base image digests against the registry.
- **Proved the tests can fail** — pointed them at an image that doesn't exist and
  confirmed they error with something actionable instead of skipping.

## Reproducing it

```
docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t crm-api:test backend
docker run --rm -i hadolint/hadolint hadolint - < backend/Dockerfile
container-structure-test test --image crm-api:test --config backend/container-structure-test.yaml
cd backend && ./mvnw verify -Pimage-tests

# and failing on purpose:
cd backend && ./mvnw verify -Pimage-tests -Dcrm.image=crm-api:no-such-tag
```
