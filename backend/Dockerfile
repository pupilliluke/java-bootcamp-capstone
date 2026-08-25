# syntax=docker/dockerfile:1
#
# Build context is backend/, because that is where pom.xml lives and the first
# COPY expects it at the context root:
#
#   docker build -t crm-api:dev backend
#
# See .dockerignore beside this file for what is kept out of that context.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# The committed wrapper rather than a maven: base image. The wrapper pins the
# exact Maven the enforcer requires and the one CI and every laptop already use,
# so the image is built by the same tool as everything else. A maven: image would
# supply whatever version that tag happens to carry this month.
#
# Wrapper and pom first, source second, deliberately. Docker invalidates every
# layer after the first change, and dependencies change far less often than
# source: copying src before go-offline would re-resolve the whole tree on every
# code edit.
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -ntp -q -DskipTests dependency:go-offline

COPY src ./src

# package, not verify. verify runs AppUserRepositoryIT against a real PostgreSQL
# and InteractionMessagingIT against an embedded broker, and `docker build` has
# no route to either. That is not the same as untested: CI runs the full
# `mvnw clean verify` outside the image with the service container up, and the
# image is only built after that job passes.
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre

# Non-root. UID 10001 sits above the host system-account range, so it cannot
# collide with a real account if a volume is ever shared into the container.
RUN useradd --system --uid 10001 --create-home spring
WORKDIR /app

# The artifact only. target/ around it holds classes, test classes and surefire
# reports, none of which the runtime needs. --chown during the COPY rather than a
# later RUN chown, which would add a second layer duplicating the whole jar.
COPY --from=build --chown=spring:spring /workspace/target/*-SNAPSHOT.jar app.jar

USER 10001
EXPOSE 8080

# A percentage rather than a fixed -Xmx: the JVM then sizes the heap against
# whatever cgroup limit the container was given. A fixed -Xmx tuned on a laptop
# ignores the limit, the kernel kills the process at it, and the exit is 137 with
# no Java stack trace to explain why.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# No secrets here, and none baked at build time. At run time the container needs
# JWT_SECRET plus either the LOCAL_DB_* or the AZURE_DB_* set, and optionally
# KAFKA_BOOTSTRAP_SERVERS. All are declared without defaults in the profile yml,
# so a missing value stops startup rather than falling back to something.
#
#   docker run --rm -p 8080:8080 --env-file .env crm-api:dev
#
# In Kubernetes the same names arrive from a ConfigMap and a Secret instead.

# /dev/tcp rather than curl or wget: it is a bash builtin, so the runtime image
# needs no extra package -- and every package added is another artifact for the
# Dependency-Check scan to find. start-period covers Flyway plus context startup,
# which is roughly 10-24 seconds on this application.
#
# Worth knowing: /actuator/health/readiness currently reports UP even when
# PostgreSQL is unreachable, because the readiness group contains only
# readinessState. Until that group also includes db, this healthcheck proves the
# process is answering rather than that it can serve.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && cat <&3 | grep -q UP"]

# Labels belong in the runtime stage. Metadata attached to the build stage is
# discarded with it, so a LABEL up there would never reach the image anyone pulls.
#
# GIT_SHA arrives as a build argument because .dockerignore excludes .git/, so
# nothing inside the build can read it:
#
#   docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t crm-api:dev backend
#
# Declared last because a changed ARG invalidates every layer after the
# instruction that references it. The SHA changes on every commit and the jar
# does not, so the reference belongs below the COPY rather than above it.
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="capstone-crm-api" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}"

ENTRYPOINT ["java","-jar","/app/app.jar"]
