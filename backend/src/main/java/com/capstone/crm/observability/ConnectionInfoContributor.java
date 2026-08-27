package com.capstone.crm.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What this instance is connected to, served on /actuator/info for the
 * Settings connection panel. The build block (artifact, version, time) comes
 * from build-info.properties; this adds the half a build cannot know —
 * which profile is active, which database and broker this process is actually
 * pointed at, and which commit it was built from.
 *
 * Everything here is deliberately curated, never a raw dump. The JDBC URL in
 * particular is sanitized down to scheme, host, port and database name:
 * credentials never appear in our URLs (they live in separate properties),
 * but query parameters can carry things like a schema or ssl options that say
 * more about the deployment than a diagnostic needs, so they are cut too.
 * The endpoint is authenticated in SecurityConfig for the same reason.
 */
@Component
public class ConnectionInfoContributor implements InfoContributor {

    private final Environment environment;
    private final String datasourceUrl;
    private final String kafkaBootstrap;
    private final String interactionTopic;
    private final String consumerGroup;

    public ConnectionInfoContributor(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.kafka.bootstrap-servers:}") String kafkaBootstrap,
            @Value("${crm.messaging.interaction-topic:}") String interactionTopic,
            @Value("${crm.messaging.consumer-group:}") String consumerGroup) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.kafkaBootstrap = kafkaBootstrap;
        this.interactionTopic = interactionTopic;
        this.consumerGroup = consumerGroup;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> connections = new LinkedHashMap<>();

        // Active if anything was selected, otherwise the default — which is
        // how "local" shows up on a laptop where nothing sets the variable.
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()
                : environment.getDefaultProfiles();
        connections.put("profile", String.join(",", profiles));

        connections.put("database", sanitizeJdbcUrl(datasourceUrl));

        Map<String, Object> kafka = new LinkedHashMap<>();
        kafka.put("bootstrap", kafkaBootstrap);
        kafka.put("topic", interactionTopic);
        kafka.put("consumerGroup", consumerGroup);
        connections.put("kafka", kafka);

        builder.withDetail("connections", connections);

        // Present in a container (Dockerfile sets ENV GIT_SHA from the build
        // argument), absent under mvnw spring-boot:run — omitted rather than
        // reported as a placeholder, so "unknown" can never look like a value.
        String gitSha = environment.getProperty("GIT_SHA");
        if (gitSha != null && !gitSha.isBlank() && !"unknown".equals(gitSha)) {
            builder.withDetail("revision", gitSha);
        }
    }

    /**
     * jdbc:postgresql://host:5432/crm?sslmode=require → postgresql://host:5432/crm
     * jdbc:h2:mem:crm;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 → h2:mem:crm
     *
     * Keeps what identifies the target, cuts everything that configures the
     * connection. Package-private for the unit test.
     */
    static String sanitizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String s = url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
        // Whichever parameter delimiter comes first ends the target.
        for (char delimiter : new char[] {'?', ';'}) {
            int at = s.indexOf(delimiter);
            if (at >= 0) {
                s = s.substring(0, at);
            }
        }
        // Defence in depth: our URLs never carry user:password@host, but if one
        // ever did, the credentials must not survive into a diagnostic.
        int credentialsEnd = s.indexOf('@');
        if (credentialsEnd >= 0) {
            int schemeEnd = s.indexOf("://");
            String scheme = schemeEnd >= 0 ? s.substring(0, schemeEnd + 3) : "";
            s = scheme + s.substring(credentialsEnd + 1);
        }
        return s;
    }
}
