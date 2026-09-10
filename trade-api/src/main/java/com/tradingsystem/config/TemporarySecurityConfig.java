package com.tradingsystem.config;

import com.tradingsystem.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TemporarySecurityConfig {

    @Bean
    public CurrentUser currentUser() {
        return () -> 1L;
    }
}