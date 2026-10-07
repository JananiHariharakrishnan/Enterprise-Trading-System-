package com.tradingsystem.service.impl;

import com.tradingsystem.service.Impl.AuthServicePasswordVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordVerifierTest {

    @Mock
    private RestTemplate restTemplate;

    private AuthServicePasswordVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new AuthServicePasswordVerifier(restTemplate, "http://localhost:3000", false);
    }

    @Test
    void verifyPassword_validCredentials_returnsTrue() {
        when(restTemplate.postForEntity(eq("http://localhost:3000/auth/login"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"accessToken\":\"token\"}", HttpStatus.OK));

        assertTrue(verifier.verifyPassword("Admin123", "password123456"));
    }

    @Test
    void verifyPassword_invalidCredentials_returnsFalse() {
        when(restTemplate.postForEntity(eq("http://localhost:3000/auth/login"), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid password"));

        assertFalse(verifier.verifyPassword("Admin123", "wrongPassword"));
    }

    @Test
    void verifyPassword_nullOrEmptyInput_returnsFalse() {
        assertFalse(verifier.verifyPassword(null, "pass"));
        assertFalse(verifier.verifyPassword("user", null));
        assertFalse(verifier.verifyPassword("user", "   "));
    }
}
