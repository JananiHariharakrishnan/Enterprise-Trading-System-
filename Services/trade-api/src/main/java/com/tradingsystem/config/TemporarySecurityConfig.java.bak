package com.tradingsystem.config;

import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.security.JwtAuthenticationFilter;
import com.tradingsystem.security.JwtTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.util.List;

@Configuration
public class TemporarySecurityConfig {

    @Bean
    public CurrentUser currentUser() {
        return () -> AuthContextHolder.getAccountId().orElse(null);
    }

    @Bean
    public JwtTokenVerifier jwtTokenVerifier(
            @Value("${auth.jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${auth.jwt.issuer:${JWT_ISSUER:auth-stub}}") String issuer) {

        return new JwtTokenVerifier(secret, issuer);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenVerifier jwtTokenVerifier) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(
                new JwtAuthenticationFilter(
                        jwtTokenVerifier
                )
        );
        registration.setUrlPatterns(List.of("/api/v1/*"));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}