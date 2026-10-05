package com.tradingsystem.config;

import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.security.JwtAuthenticationFilter;
import com.tradingsystem.security.JwtTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

/**
 * Security configuration that provides ThreadLocal-based CurrentUser bean
 * and registers the JWT authentication filter.
 * The AccountId is populated by JwtAuthenticationFilter for each request.
 */
@Configuration
public class SecurityConfig {

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${JWT_ISSUER:trade-api}")
    private String jwtIssuer;

    /**
     * Provide JwtTokenVerifier as a singleton bean.
     * Validates HS256 JWT tokens issued by the auth service.
     */
    @Bean
    public JwtTokenVerifier jwtTokenVerifier() {
        return new JwtTokenVerifier(jwtSecret, jwtIssuer);
    }

    /**
     * Provide CurrentUser as a request-scoped bean with a scoped proxy.
     * The scoped proxy allows singleton beans (like services) to safely inject
     * this request-scoped bean without errors.
     * The proxy delegates to the actual request-scoped instance during runtime.
     */
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public CurrentUser currentUser() {
        return () -> AuthContextHolder.getAccountId()
                .orElseThrow(() -> new IllegalStateException(
                        "CurrentUser accessed outside of authenticated request context"
                ));
    }

    /**
     * Register JwtAuthenticationFilter in the servlet filter chain.
     * This filter validates JWT tokens and populates AuthContextHolder
     * for all /api/v1/* requests.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(new JwtAuthenticationFilter(tokenVerifier));
        registration.setUrlPatterns(List.of("/api/v1/*"));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}