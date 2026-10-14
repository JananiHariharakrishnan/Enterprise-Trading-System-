package com.tradingsystem.service.Impl;

import com.tradingsystem.service.PasswordVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServicePasswordVerifier implements PasswordVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuthServicePasswordVerifier.class);

    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final boolean bypassOnUnreachable;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthServicePasswordVerifier(
            @Value("${auth.service.url:${AUTH_SERVICE_URL:${AUTH_URL:http://localhost:3000}}}") String authServiceUrl,
            @Value("${auth.service.bypass-on-unreachable:false}") boolean bypassOnUnreachable
    ) {
        this.restTemplate = new RestTemplate();
        this.authServiceUrl = authServiceUrl;
        this.bypassOnUnreachable = bypassOnUnreachable;
    }

    public AuthServicePasswordVerifier(
            RestTemplate restTemplate,
            String authServiceUrl,
            boolean bypassOnUnreachable
    ) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.bypassOnUnreachable = bypassOnUnreachable;
    }

    @Override
    public boolean verifyPassword(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        String url = authServiceUrl.replaceAll("/+$", "") + "/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            log.warn("Auth service rejected credentials for user {}: status {}", username, ex.getStatusCode());
            return false;
        } catch (ResourceAccessException ex) {
            log.warn("Auth service unreachable at {}: {}", url, ex.getMessage());
            if (bypassOnUnreachable) {
                log.warn("bypassOnUnreachable is active; allowing credential verification in local dev/mock mode");
                return true;
            }
            throw new IllegalStateException("Authentication service is currently unavailable. Please try again later.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error validating password for user {}: {}", username, ex.getMessage());
            return false;
        }
    }
}
