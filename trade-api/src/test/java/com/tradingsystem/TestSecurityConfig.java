package com.tradingsystem;

import com.tradingsystem.security.CurrentUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    CurrentUser currentUser() {
        return () -> 1001L;
    }
}