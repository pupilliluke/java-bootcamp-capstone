package com.capstone.crm.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime behaviour of the image {@code backend/Dockerfile} produces.
 *
 * <p>This is one layer of four, and deliberately not the only one. The
 * Dockerfile is linted by hadolint, the contents and config of the built image
 * are asserted by {@code backend/container-structure-test.yaml}, and its
 * packages are scanned by Trivy. Each of those reads a built artefact without
 * running it, which is why none of them can tell you the application actually
 * starts. That is the question left for here, and it is the one Testcontainers
 * is the right tool for: the real image, a real PostgreSQL, over real HTTP.
 *
 * <p>Nothing here skips. A missing Docker daemon or a missing image fails the
 * class immediately with the command that fixes it, because a container test
 * that quietly reports "0 run" is indistinguishable from one that passed, and
 * the whole point of these is to be the gate before an image ships.
 *
 * <p>The image is expected to exist already rather than being built here. The
 * build is the pipeline's job and belongs in front of every one of the four
 * layers, not inside one of them:
 *
 * <pre>
 *   docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t crm-api:test backend
 *   ./mvnw verify -Pimage-tests
 * </pre>
 *
 * <p>Point it at another tag with {@code -Dcrm.image=ghcr.io/org/crm-api:1.2.3}
 * to run the same assertions against what a registry actually holds.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("the published container image")
class ContainerImageIT {

    private static final String IMAGE = System.getProperty("crm.image", "crm-api:test");

    /** At least 32 characters, because HS256 needs a 256-bit key and the app refuses to start on less. */
    private static final String JWT_SECRET = "container-image-it-secret-key-of-sufficient-length";
    private static final String DB_PASSWORD = "throwaway-integration-password";
    private static final String AGENT_PASSWORD = "agent-password";

    /**
     * Generous, and intentionally so. This covers pulling postgres:17 on a cold
     * runner plus Flyway plus context startup, and a timeout that trips on a
     * slow morning is a test nobody trusts.
     */
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(4);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static DockerClient docker;
    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> app;
    private static String baseUrl;

    // -----------------------------------------------------------------------

    @BeforeAll
    static void startTheImage() throws Exception {
        docker = requireDocker();
        requireImage();

        network = Network.newNetwork();

        // A real PostgreSQL rather than the H2 the unit tests use. Flyway's
        // migration is written for Postgres, and an image that cannot migrate
        // is an image that cannot start -- which H2 would not tell us.
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
                .withNetwork(network)
                .withNetworkAliases("db")
                .withDatabaseName("crm")
                .withUsername("crm")
                .withPassword(DB_PASSWORD);
        postgres.start();

        app = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withNetwork(network)
                .withExposedPorts(8080)
                // The same names a Kubernetes ConfigMap and Secret would supply.
                // Nothing is baked into the image, so everything arrives here.
                .withEnv("JWT_SECRET", JWT_SECRET)
                .withEnv("LOCAL_DB_HOST", "db")
                .withEnv("LOCAL_DB_PORT", "5432")
                .withEnv("LOCAL_DB_NAME", "crm")
                .withEnv("LOCAL_DB_USER", "crm")
                .withEnv("LOCAL_DB_PASSWORD", DB_PASSWORD)
                // The image's own HEALTHCHECK, not a wait strategy of our own.
                // Waiting on anything else would leave the healthcheck itself
                // untested, and a broken one means an orchestrator restarts a
                // container that was working perfectly well.
                .waitingFor(Wait.forHealthcheck().withStartupTimeout(STARTUP_TIMEOUT));
        app.start();

        baseUrl = "http://" + app.getHost() + ":" + app.getMappedPort(8080);
        seedAgentAccount();
    }

    @AfterAll
    static void stopEverything() {
        if (app != null) app.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    // -----------------------------------------------------------------------
    // Startup
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("reaches healthy through the HEALTHCHECK baked into the image")
    void reportsHealthy() {
        String status = docker.inspectContainerCmd(app.getContainerId())
                .exec().getState().getHealth().getStatus();

        assertThat(status)
                .as("the image's own HEALTHCHECK, as Docker evaluated it")
                .isEqualTo("healthy");
    }

    @Test
    @DisplayName("runs the application as uid 10001 and not as root")
    void runsAsNonRoot() throws Exception {
        Container.ExecResult whoami = app.execInContainer("id", "-u");
        assertThat(whoami.getStdout().trim()).isEqualTo("10001");

        // The uid of the java process itself, not merely of a shell we started.
        // A container can be entered as one user and still run its workload as
        // another, and it is the workload that matters.
        Container.ExecResult javaOwner = app.execInContainer(
                "bash", "-c", "ps -o user= -p 1 | tr -d ' '");
        assertThat(javaOwner.getStdout().trim()).isEqualTo("spring");
    }

    @Test
    @DisplayName("sizes the heap against the container's memory limit")
    void heapFollowsTheCgroupLimit() throws Exception {
        long limitBytes = 512L * 1024 * 1024;

        // A separate container, memory-limited, with the entrypoint swapped for
        // sleep so the JVM under test is one we start by hand.
        try (GenericContainer<?> limited = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withEntrypoint("sleep");
                    cmd.getHostConfig().withMemory(limitBytes);
                })
                .withCommand("300")
                .withStartupCheckStrategy(new IsRunningStartupCheckStrategy())) {
            limited.start();

            Container.ExecResult flags = limited.execInContainer(
                    "bash", "-c", "java -XX:+PrintFlagsFinal -version | grep -w MaxHeapSize");
            long maxHeap = Long.parseLong(flags.getStdout().replaceAll("[^0-9]", ""));

            // -XX:MaxRAMPercentage=75 against a 512 MB limit. The tolerance is
            // wide because the JVM rounds to its heap alignment; what is being
            // tested is that the limit was read at all. A JVM that ignores it
            // takes the host's memory as its budget and is killed at the cgroup
            // boundary with exit 137 and no stack trace to explain why.
            assertThat(maxHeap)
                    .as("MaxHeapSize against a %d MB container limit", limitBytes / 1024 / 1024)
                    .isBetween((long) (limitBytes * 0.60), (long) (limitBytes * 0.85));
        }
    }

    // -----------------------------------------------------------------------
    // It is genuinely serving
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("serves the health endpoint over the published port")
    void servesHealth() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("answers both orchestrator probes")
    void servesLivenessAndReadiness() throws Exception {
        for (String probe : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> response = get(probe);
            assertThat(response.statusCode()).as(probe).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).path("status").asText()).as(probe).isEqualTo("UP");
        }
    }

    /**
     * Ordered second-to-last because it destroys the database the earlier tests
     * rely on. Only the SIGTERM check, which needs nothing but a live process,
     * is allowed to run after it.
     */
    @Test
    @Order(Integer.MAX_VALUE - 1)
    @DisplayName("turns readiness DOWN, but keeps liveness UP, when the database dies")
    void readinessFollowsTheDatabase() throws Exception {
        // The distinction this pins down is the one that matters to an
        // orchestrator. Readiness gates Service traffic, so it has to fail when
        // the instance cannot serve; liveness triggers a restart, and restarting
        // an application never fixes a database, so it has to hold. Get these the
        // same way round and a database outage becomes a cluster-wide crash loop.
        assertThat(JSON.readTree(get("/actuator/health/readiness").body()).path("status").asText())
                .as("readiness before the database is stopped")
                .isEqualTo("UP");

        postgres.stop();

        // A far longer per-request timeout than the other calls use, and the
        // reason is the thing being measured: the probe does not fail fast. It
        // blocks until Hikari gives up trying to get a connection, so the
        // request itself outlives an ordinary timeout. Measured at 31.0s against
        // Hikari's 30s default, which is what drove connection-timeout down to
        // 10s in application.yml -- keep this ceiling above that setting.
        String readiness = "UP";
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt < 6 && "UP".equals(readiness); attempt++) {
            response = getWithin(Duration.ofSeconds(45), "/actuator/health/readiness");
            readiness = JSON.readTree(response.body()).path("status").asText();
            if ("UP".equals(readiness)) Thread.sleep(2000);
        }

        assertThat(readiness)
                .as("readiness with the database stopped -- UP here means a broken "
                        + "instance would keep receiving traffic")
                .isEqualTo("DOWN");
        assertThat(response.statusCode())
                .as("a failing probe has to be a 503; Kubernetes reads the status code")
                .isEqualTo(503);

        assertThat(JSON.readTree(get("/actuator/health/liveness").body()).path("status").asText())
                .as("liveness with the database stopped -- DOWN here would restart every "
                        + "instance at once and crash-loop until the database returned")
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("keeps health detail from anonymous callers")
    void healthDetailIsNotPublic() throws Exception {
        // show-details: when-authorized. The endpoint has to stay reachable for
        // the orchestrator to probe, and "always" would hand any anonymous
        // caller the database vendor, connection state and free disk -- a free
        // reconnaissance endpoint on the public side of the load balancer.
        JsonNode body = JSON.readTree(get("/actuator/health").body());

        assertThat(body.has("components"))
                .as("component detail must not be served to an anonymous caller")
                .isFalse();
        assertThat(body.toString()).doesNotContain("PostgreSQL", "diskSpace", "validationQuery");
    }

    @Test
    @DisplayName("applies its security response headers")
    void sendsSecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(csp -> assertThat(csp).contains("frame-ancestors 'none'"));
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        // Every response carries a correlation id, so a line in an aggregated
        // log can be traced back to the request that produced it.
        assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
    }

    // -----------------------------------------------------------------------
    // The database is real and the schema is Flyway's
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("runs its Flyway migrations against the real database")
    void migratesTheDatabase() throws Exception {
        try (Connection connection = database();
             Statement statement = connection.createStatement()) {

            try (ResultSet history = statement.executeQuery(
                    "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
                assertThat(history.next()).as("flyway_schema_history has at least one row").isTrue();
                assertThat(history.getString("version")).isEqualTo("1");
                assertThat(history.getBoolean("success")).isTrue();
            }

            // ddl-auto is validate, so a context that started at all proves the
            // entities match. This proves the table the migration promised is
            // the one that exists.
            try (ResultSet table = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'app_user'")) {
                table.next();
                assertThat(table.getInt(1)).isEqualTo(1);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Authentication and authorisation, as shipped
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("rejects an unauthenticated request to the API")
    void apiRequiresAuthentication() throws Exception {
        assertThat(get("/api/v1/customers").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("rejects a request bearing a token it did not sign")
    void refusesAForeignToken() throws Exception {
        // A well-formed JWT signed with a different key. If this were accepted,
        // anyone could mint their own admin. The image reads JWT_SECRET from the
        // environment, so this also proves it is verifying against that value
        // rather than against something compiled in.
        String forged = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJhdHRhY2tlciIsInJvbGUiOiJBRE1JTiJ9"
                + ".Ky0dGCEDDfXhH4gW4nBIWKZ8YQxHhVMDPu5aM7XKn9I";

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/customers"))
                        .header("Authorization", "Bearer " + forged)
                        .timeout(Duration.ofSeconds(15))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("issues a token that opens the API")
    void loginIssuesAWorkingToken() throws Exception {
        String token = login();

        HttpResponse<String> customers = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/customers"))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(15))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(customers.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("enforces its role rules inside the shipped artefact")
    void agentCannotDeleteACustomer() throws Exception {
        // Deleting a customer is ADMIN-only. The rule is tested at the unit
        // level too; asserting it here is the difference between "the rule is
        // written" and "the rule is in the jar that was actually built".
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/customers/1"))
                        .header("Authorization", "Bearer " + login())
                        .timeout(Duration.ofSeconds(15))
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("an AGENT deleting a customer")
                .isEqualTo(403);
    }

    // -----------------------------------------------------------------------
    // How it behaves when things are wrong
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("refuses to start when a required setting is missing")
    void failsFastWithoutConfiguration() throws Exception {
        // JWT_SECRET is declared with no default anywhere in the configuration,
        // so a container started without one has to stop. The alternative -- a
        // committed fallback -- is a weak signing key that ships to production
        // and works, which is the worst of both outcomes.
        CreateContainerResponse created = docker.createContainerCmd(IMAGE).exec();
        try {
            docker.startContainerCmd(created.getId()).exec();
            Integer exitCode = docker.waitContainerCmd(created.getId())
                    .start().awaitStatusCode(3, TimeUnit.MINUTES);

            assertThat(exitCode).as("exit code with no environment supplied").isNotZero();
            assertThat(logsOf(created.getId()))
                    .as("the failure has to name what is missing, or nobody can fix it")
                    .containsAnyOf("JWT_SECRET", "Could not resolve placeholder", "Failed to bind");
        } finally {
            docker.removeContainerCmd(created.getId()).withForce(true).exec();
        }
    }

    /**
     * Last on purpose. It stops the shared container, so anything ordered after
     * it would find nothing to talk to.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    @DisplayName("shuts down on SIGTERM instead of waiting to be killed")
    void stopsOnSigterm() {
        String id = app.getContainerId();

        docker.stopContainerCmd(id).withTimeout(30).exec();
        Integer exitCode = docker.waitContainerCmd(id).start().awaitStatusCode(1, TimeUnit.MINUTES);

        // 143 is SIGTERM honoured through the JVM's shutdown hooks; 0 is a clean
        // exit. 137 is SIGKILL, meaning it ignored the polite request and the
        // runtime had to force it -- which drops in-flight requests on every
        // rolling deploy, and is exactly what a shell-wrapped entrypoint that
        // swallows signals looks like from the outside.
        assertThat(exitCode)
                .as("exit code after docker stop; 137 means it had to be killed")
                .isIn(143, 0);
    }

    // -----------------------------------------------------------------------
    // Preflight and helpers
    // -----------------------------------------------------------------------

    /** Fails the class rather than skipping it: a container test with no Docker has proved nothing. */
    private static DockerClient requireDocker() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(
                    "Docker is not reachable, so the image cannot be tested. Start Docker and run again. "
                            + "This fails rather than skips on purpose: a container test that quietly "
                            + "reports nothing looks exactly like one that passed.");
        }
        return DockerClientFactory.instance().client();
    }

    private static void requireImage() {
        try {
            InspectImageResponse image = docker.inspectImageCmd(IMAGE).exec();
            assertThat(image.getId()).isNotBlank();
        } catch (NotFoundException missing) {
            throw new IllegalStateException(
                    "Image '" + IMAGE + "' does not exist locally. Build it first:\n"
                            + "  docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t " + IMAGE + " backend\n"
                            + "or point the tests at another tag with -Dcrm.image=<tag>.", missing);
        }
    }

    /**
     * The migration seeds nobody, which is correct -- a shipped image with a
     * known account in it is a back door. So the account this test authenticates
     * with is created here, in the throwaway database, and never in the image.
     */
    private static void seedAgentAccount() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(AGENT_PASSWORD);
        try (Connection connection = database();
             var insert = connection.prepareStatement(
                     "INSERT INTO app_user (username, email, password_hash, role, enabled) "
                             + "VALUES (?, ?, ?, 'AGENT', TRUE)")) {
            insert.setString(1, "image-it-agent");
            insert.setString(2, "image-it-agent@example.test");
            insert.setString(3, hash);
            insert.executeUpdate();
        }
    }

    private static Connection database() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"image-it-agent\",\"password\":\"" + AGENT_PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("login as the seeded agent").isEqualTo(200);
        String token = JSON.readTree(response.body()).path("accessToken").asText();
        assertThat(token).as("accessToken in the login response").isNotBlank();
        return token;
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return getWithin(Duration.ofSeconds(15), path);
    }

    private static HttpResponse<String> getWithin(Duration timeout, String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(timeout)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String logsOf(String containerId) throws Exception {
        StringBuilder captured = new StringBuilder();
        docker.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        captured.append(new String(frame.getPayload()));
                    }
                })
                .awaitCompletion(30, TimeUnit.SECONDS);
        return captured.toString();
    }
}
