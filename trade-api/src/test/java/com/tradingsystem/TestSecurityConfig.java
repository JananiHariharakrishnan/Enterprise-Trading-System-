package com.tradingsystem;

import com.tradingsystem.security.CurrentUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.tradingsystem.security.JwtTokenVerifier;

/**
 * Minimal test security configuration.
 *
 * Provides a primary CurrentUser bean under a test-specific name so that it
 * does not register a bean with the same name as production configuration,
 * avoiding BeanDefinitionOverrideException while still overriding by type.
 */
@TestConfiguration
public class TestSecurityConfig {

    private static final Long DEFAULT_TEST_ACCOUNT_ID = 1001L;

    static {
        // Ensure JWT-related environment properties exist so application test context can create security beans.
        if (System.getProperty("JWT_SECRET") == null) {
            System.setProperty("JWT_SECRET", "test-secret");
        }
        if (System.getProperty("JWT_ISSUER") == null) {
            System.setProperty("JWT_ISSUER", "test-issuer");
        }
    }

    /**
     * NOTE: method name is different from the production bean name to avoid
     * bean-name collisions during configuration class processing.
     *
     * Marked @Primary so autowiring by type prefers this bean in tests.
     */
    @Bean("testCurrentUser")
    @Primary
    public CurrentUser testCurrentUser() {
        return () -> DEFAULT_TEST_ACCOUNT_ID;
    }

    @Bean
    @Primary
    public JwtTokenVerifier testJwtTokenVerifier() {
        // Provide a simple non-blank secret so JwtTokenVerifier doesn't fail during context load.
        return new JwtTokenVerifier("test-secret","test-issuer");
    }
}