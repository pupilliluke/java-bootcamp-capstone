package com.capstone.crm.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What this instance is, and what it is connected to — served on
 * /actuator/info for the Settings connection panel. The build block comes from
 * build-info.properties; this adds what a build cannot know.
 *
 * Two rules shape everything here:
 *
 * Derived, never declared. The environment is detected — Kubernetes injects
 * KUBERNETES_SERVICE_HOST into every pod and mounts the namespace at a
 * well-known path, so "kubernetes: student02" is read from the platform, not
 * from a label somebody has to remember to set. A declared name would drift
 * the first time a deployment forgot it.
 *
 * Identity, never addresses. The payload names the database, the schema, the
 * topic and the group — never a host or a port. Any signed-in agent can read
 * this panel, and an address is reconnaissance, not diagnostics: the answer
 * to "am I on the shared database" is the schema name, not the server's IP.
 */
@Component
public class ConnectionInfoContributor implements InfoContributor {

    /** Mounted by Kubernetes into every pod, regardless of our manifests. */
    static final Path NAMESPACE_FILE =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private final Environment environment;
    private final String datasourceUrl;
    private final String interactionTopic;
    private final String consumerGroup;

    public ConnectionInfoContributor(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${crm.messaging.interaction-topic:}") String interactionTopic,
            @Value("${crm.messaging.consumer-group:}") String consumerGroup) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.interactionTopic = interactionTopic;
        this.consumerGroup = consumerGroup;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> connections = new LinkedHashMap<>();

        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()
                : environment.getDefaultProfiles();
        String profile = String.join(",", profiles);
        connections.put("profile", profile);
        connections.put("environment",
                environmentName(environment, NAMESPACE_FILE, profile));

        connections.put("database", databaseName(datasourceUrl));
        String schema = schemaParameter(datasourceUrl);
        if (schema != null) {
            connections.put("schema", schema);
        }

        Map<String, Object> kafka = new LinkedHashMap<>();
        kafka.put("topic", interactionTopic);
        kafka.put("consumerGroup", consumerGroup);
        connections.put("kafka", kafka);

        builder.withDetail("connections", connections);
        builder.withDetail("runtime", runtime());

        // Present in a container (Dockerfile sets ENV GIT_SHA from the build
        // argument), absent under mvnw spring-boot:run — omitted rather than
        // reported as a placeholder, so "unknown" can never look like a value.
        String gitSha = environment.getProperty("GIT_SHA");
        if (gitSha != null && !gitSha.isBlank() && !"unknown".equals(gitSha)) {
            builder.withDetail("revision", gitSha);
        }
    }

    /**
     * Where this process runs, read from the platform itself:
     *
     *   kubernetes: [namespace]  — KUBERNETES_SERVICE_HOST is injected into
     *                              every pod, and the namespace file is
     *                              mounted by the platform. On the course
     *                              cluster the namespace (studentNN) IS the
     *                              environment name that matters.
     *   profile: [name]          — no platform signal, so the Spring profile
     *                              is the honest answer: local on a laptop,
     *                              azure against the hosted database, test
     *                              under the suite.
     */
    static String environmentName(Environment env, Path namespaceFile, String profile) {
        if (env.getProperty("KUBERNETES_SERVICE_HOST") != null) {
            try {
                String ns = Files.readString(namespaceFile).trim();
                if (!ns.isEmpty()) {
                    return "kubernetes: " + ns;
                }
            } catch (Exception readFailed) {
                // A pod without the mount is unusual but legal (the token
                // mount can be disabled); bare "kubernetes" is still true.
            }
            return "kubernetes";
        }
        return "profile: " + profile;
    }

    /**
     * The database's name, never its address.
     *   jdbc:postgresql://host:5432/bootcamp?x=y  → bootcamp
     *   jdbc:h2:mem:crm;MODE=PostgreSQL           → crm (in-memory)
     */
    static String databaseName(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String s = url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
        for (char delimiter : new char[] {'?', ';'}) {
            int at = s.indexOf(delimiter);
            if (at >= 0) {
                s = s.substring(0, at);
            }
        }
        if (s.startsWith("h2:mem:")) {
            return s.substring("h2:mem:".length()) + " (in-memory)";
        }
        int lastSlash = s.lastIndexOf('/');
        return lastSlash >= 0 ? s.substring(lastSlash + 1) : s;
    }

    /** The currentSchema query parameter, if the URL carries one. */
    static String schemaParameter(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = Pattern.compile("[?&;]currentSchema=([^&;]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /**
     * The stack, in depth, for the panel's collapsible detail. A curated list,
     * not a classpath dump: these are the versions someone debugging "works on
     * my machine, not on the cluster" actually compares. Optional entries
     * resolve reflectively — the PostgreSQL driver is runtime-scoped and not
     * compilable against, and a diagnostic must not break over an absent jar.
     */
    private Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>();

        Map<String, Object> java = new LinkedHashMap<>();
        java.put("version", System.getProperty("java.version"));
        java.put("vendor", System.getProperty("java.vendor"));
        runtime.put("java", java);

        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("springBoot", SpringBootVersion.getVersion());
        dependencies.put("springFramework", SpringVersion.getVersion());
        putIfResolvable(dependencies, "hibernate",
                () -> org.hibernate.Version.getVersionString());
        putIfResolvable(dependencies, "kafkaClients",
                () -> org.apache.kafka.common.utils.AppInfoParser.getVersion());
        putIfResolvable(dependencies, "flyway", () ->
                org.flywaydb.core.api.MigrationVersion.class.getPackage()
                        .getImplementationVersion());
        putIfResolvable(dependencies, "postgresqlDriver", () ->
                Class.forName("org.postgresql.Driver").getPackage()
                        .getImplementationVersion());
        runtime.put("dependencies", dependencies);

        Map<String, Object> os = new LinkedHashMap<>();
        os.put("name", System.getProperty("os.name"));
        os.put("arch", System.getProperty("os.arch"));
        runtime.put("os", os);

        return runtime;
    }

    private static void putIfResolvable(
            Map<String, Object> target, String key, Callable<String> version) {
        try {
            String value = version.call();
            if (value != null && !value.isBlank()) {
                target.put(key, value);
            }
        } catch (Throwable absent) {
            // Not on the classpath, or no version recorded — omit rather
            // than invent.
        }
    }
}
