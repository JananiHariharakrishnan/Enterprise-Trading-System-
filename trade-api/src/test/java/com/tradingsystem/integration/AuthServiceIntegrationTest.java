package com.tradingsystem.integration;

import com.tradingsystem.security.JwtTokenVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for JWT token verification: Auth Service token protection validation.
 *
 * Tests the acceptance criteria:
 * 1. Trade API's JwtTokenVerifier correctly validates tokens from auth-service
 * 2. A token signed with a key the platform should not trust is refused
 * 3. A token from wrong issuer is refused
 * 4. Tokens expire correctly
 */
class AuthServiceIntegrationTest {

    private final JwtTokenVerifier verifier = new JwtTokenVerifier(
            "development-only-shared-secret-change-me",
            "auth-service"
    );

    /**
     * Test: Token verifier rejects request with no token
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenIsNull() {
        assertNull(verifier.verifyAndGetAccountId(null));
    }

    /**
     * Test: Token verifier rejects request with empty token
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenIsEmpty() {
        assertNull(verifier.verifyAndGetAccountId(""));
    }

    /**
     * Test: Token verifier rejects invalid token
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenInvalid() {
        assertNull(verifier.verifyAndGetAccountId("invalid.token.parts"));
    }

    /**
     * Test: Token verifier rejects token signed by untrusted key
     *
     * This verifies that even a structurally valid token signed by a key
     * the platform should not trust is rejected.
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenSignedByUntrustedKey() {
        String tokenSignedByUntrustedKey = createJwt(
                "untrusted-secret-key-that-platform-should-not-accept",
                "auth-service",
                1001L,
                3600
        );

        assertNull(verifier.verifyAndGetAccountId(tokenSignedByUntrustedKey));
    }

    /**
     * Test: Token verifier rejects token with wrong issuer
     *
     * Even if signed with the correct key, a token from a different issuer
     * (e.g., an old test fixture or attacker-controlled issuer) must be rejected.
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenIssuedByWrongIssuer() {
        String tokenWithWrongIssuer = createJwt(
                "development-only-shared-secret-change-me",
                "attacker-issued-tokens",
                1001L,
                3600
        );

        assertNull(verifier.verifyAndGetAccountId(tokenWithWrongIssuer));
    }

    /**
     * Test: Token verifier rejects expired token
     */
    @Test
    void tokenVerifier_returnsNull_whenTokenExpired() {
        String expiredToken = createJwt(
                "development-only-shared-secret-change-me",
                "auth-service",
                1001L,
                -10  // expired 10 seconds ago
        );

        assertNull(verifier.verifyAndGetAccountId(expiredToken));
    }

    /**
     * Test: Token verifier accepts valid token signed by auth-service
     *
     * This is the happy path: a token signed with the correct key, with the correct issuer,
     * and not expired is accepted and the accountId is extracted.
     */
    @Test
    void tokenVerifier_returnsAccountId_whenTokenIsValid() {
        String validToken = createJwt(
                "development-only-shared-secret-change-me",
                "auth-service",
                1001L,
                3600
        );

        Long accountId = verifier.verifyAndGetAccountId(validToken);
        assertNotNull(accountId);
        assertEquals(1001L, accountId);
    }

    /**
     * Test: Token verifier rejects token with wrong account ID structure
     */
    @Test
    void tokenVerifier_returnsNull_whenAccountIdMissing() {
        String tokenWithoutAccountId = createJwtWithoutAccountId(
                "development-only-shared-secret-change-me",
                "auth-service",
                3600
        );

        assertNull(verifier.verifyAndGetAccountId(tokenWithoutAccountId));
    }

    // ========== Helper methods ==========

    /**
     * Creates a signed JWT token for testing.
     *
     * @param secret       The secret key to sign with
     * @param issuer       The issuer claim value
     * @param accountId    The accountId claim value
     * @param expiresInSec Lifetime in seconds (negative for expired token)
     * @return A complete signed JWT token
     */
    private String createJwt(String secret, String issuer, long accountId, int expiresInSec) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expiresInSec;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(
                "{\"sub\":\"test-user\",\"accountId\":" + accountId +
                ",\"roles\":[\"CUSTOMER\"],\"iat\":" + now +
                ",\"exp\":" + exp +
                ",\"iss\":\"" + issuer + "\"}"
        );

        String signingInput = header + "." + payload;
        String signature = signHmacSha256(signingInput, secret);

        return signingInput + "." + signature;
    }

    /**
     * Creates a JWT token without the accountId field for testing rejection.
     */
    private String createJwtWithoutAccountId(String secret, String issuer, int expiresInSec) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expiresInSec;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(
                "{\"sub\":\"test-user\"" +
                ",\"roles\":[\"CUSTOMER\"],\"iat\":" + now +
                ",\"exp\":" + exp +
                ",\"iss\":\"" + issuer + "\"}"
        );

        String signingInput = header + "." + payload;
        String signature = signHmacSha256(signingInput, secret);

        return signingInput + "." + signature;
    }

    private String base64UrlEncode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String signHmacSha256(String signingInput, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }
}
