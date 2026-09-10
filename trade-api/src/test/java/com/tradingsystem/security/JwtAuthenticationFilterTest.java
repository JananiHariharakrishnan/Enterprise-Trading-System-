package com.tradingsystem.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "development-only-shared-secret-change-me";
    private static final String ISSUER = "auth-stub";

    private final JwtTokenVerifier verifier = new JwtTokenVerifier(SECRET, ISSUER);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier);

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void doFilter_returnsAuth401_whenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Chain should not execute");
        });

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
    }

    @Test
    void doFilter_returnsAuth401_whenSchemeIsWrong() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Chain should not execute");
        });

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
    }

    @Test
    void doFilter_returnsAuth401_whenTokenInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer invalid.token.parts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Chain should not execute");
        });

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
    }

    @Test
    void doFilter_setsContextAndContinues_whenTokenValid() throws ServletException, IOException {
        long exp = Instant.now().getEpochSecond() + 300;
        String token = token(exp, 1001L);

        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicLong observedAccountId = new AtomicLong(-1L);

        filter.doFilter(request, response, (req, res) ->
                observedAccountId.set(AuthContextHolder.getAccountId().orElse(-1L))
        );

        assertEquals(1001L, observedAccountId.get());
        assertNull(AuthContextHolder.getAccountId().orElse(null));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/accounts/1");
        return request;
    }

    private static String token(long exp, long accountId) {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"demo\",\"accountId\":" + accountId
                + ",\"roles\":[\"CUSTOMER\"],\"iat\":" + (exp - 60)
                + ",\"exp\":" + exp + ",\"iss\":\"" + ISSUER + "\"}";

        String encodedHeader = base64Url(header);
        String encodedPayload = base64Url(payload);
        String signingInput = encodedHeader + "." + encodedPayload;

        String signature = base64Url(hmacSha256(signingInput, SECRET));
        return signingInput + "." + signature;
    }

    private static byte[] hmacSha256(String value, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}


