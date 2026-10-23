package com.pk.core.test.business;

import com.pk.core.business.config.CloudDeploymentGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class CloudDeploymentGuardTest {
    private static final String DATABASE = "jdbc:postgresql://example.neon.tech/neondb?sslmode=require";
    private static final String ORIGINS = "https://jewelry-ecommerce-admin.vercel.app,https://jewelry-ecommerce-client.vercel.app";

    @Test
    void acceptsTlsDatabaseAndExactHttpsOrigins() {
        assertDoesNotThrow(() -> guard(new MockEnvironment(), DATABASE, "private", "public", ORIGINS));
    }

    @Test
    void rejectsDatabaseWithoutTls() {
        assertThrows(IllegalStateException.class, () -> guard(new MockEnvironment(),
                "jdbc:postgresql://localhost:5432/company", "private", "public", ORIGINS));
    }

    @Test
    void rejectsTemporaryJwtKeys() {
        assertThrows(IllegalStateException.class, () -> guard(new MockEnvironment(), DATABASE, "", "", ORIGINS));
    }

    @Test
    void rejectsWildcardHttpAndPathOrigins() {
        for (String origin : new String[]{"http://localhost:6100", "*", "https://*.vercel.app", "https://example.com/path", ""}) {
            assertThrows(RuntimeException.class, () -> guard(new MockEnvironment(), DATABASE, "private", "public", origin));
        }
    }

    @Test
    void rejectsDevAndCloudTogether() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "cloud");
        assertThrows(IllegalStateException.class, () -> guard(environment, DATABASE, "private", "public", ORIGINS));
    }

    private void guard(MockEnvironment environment, String database, String privateKey, String publicKey, String origins) {
        new CloudDeploymentGuard(environment, database, privateKey, publicKey, origins);
    }
}
