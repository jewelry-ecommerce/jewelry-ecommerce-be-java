package com.pk.core.business.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;

/** Reject unsafe cloud settings instead of silently using local DB or temporary keys. */
@Component
@Profile("cloud")
public class CloudDeploymentGuard {
    public CloudDeploymentGuard(Environment environment,
                                @Value("${spring.datasource.url}") String databaseUrl,
                                @Value("${app.jwt.private-key}") String privateKey,
                                @Value("${app.jwt.public-key}") String publicKey,
                                @Value("${app.cors.allowed-origins}") String origins) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new IllegalStateException("Do not activate dev and cloud together");
        }
        if (!databaseUrl.startsWith("jdbc:postgresql://") || !databaseUrl.matches(".*[?&]sslmode=(require|verify-full|verify-ca)(&.*)?$")) {
            throw new IllegalStateException("Cloud database requires a PostgreSQL JDBC URL with TLS");
        }
        if (privateKey.isBlank() || publicKey.isBlank()) {
            throw new IllegalStateException("Configure stable JWT_PRIVATE_KEY and JWT_PUBLIC_KEY");
        }
        if (origins.isBlank()) throw new IllegalStateException("Configure explicit HTTPS CORS origins");
        for (String origin : origins.split(",")) validateOrigin(origin.trim());
    }

    private static void validateOrigin(String value) {
        URI origin = URI.create(value);
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getHost().contains("*")) {
            throw new IllegalStateException("Cloud CORS must contain exact HTTPS origins, without paths");
        }
    }
}
