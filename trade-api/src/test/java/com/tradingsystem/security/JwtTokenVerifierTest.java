package com.tradingsystem.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTokenVerifierTest {

    private static final String SECRET = "development-only-shared-secret-change-me";
    private static final String ISSUER = "auth-stub";

    private final JwtTokenVerifier verifier = new JwtTokenVerifier(
            SECRET,
            ISSUER
    );

    private static String base64UrlEncode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String signingInput, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    }

    @Test
    void verify_validToken_returnsAccountId() throws Exception {
        String secret = "test-secret";
        String issuer = "test-issuer";
        long exp = Instant.now().getEpochSecond() + 3600;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode("{\"exp\":" + exp + ",\"accountId\":1001,\"iss\":\"" + issuer + "\"}");

        String signingInput = header + "." + payload;
        String signature = sign(signingInput, secret);

        String token = signingInput + "." + signature;

        JwtTokenVerifier verifier = new JwtTokenVerifier(secret, issuer);
        Long accountId = verifier.verifyAndGetAccountId(token);

        assertNotNull(accountId);
        assertEquals(1001L, accountId.longValue());
    }

    @Test
    void verify_expiredToken_returnsNull() throws Exception {
        String secret = "s";
        String issuer = "i";
        long exp = Instant.now().getEpochSecond() - 10;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode("{\"exp\":" + exp + ",\"accountId\":1001,\"iss\":\"" + issuer + "\"}");
        String signingInput = header + "." + payload;
        String signature = sign(signingInput, secret);
        String token = signingInput + "." + signature;

        JwtTokenVerifier verifier = new JwtTokenVerifier(secret, issuer);
        assertNull(verifier.verifyAndGetAccountId(token));
    }

    @Test
    void verify_wrongIssuer_returnsNull() throws Exception {
        String secret = "s";
        String issuer = "i";
        long exp = Instant.now().getEpochSecond() + 1000;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode("{\"exp\":" + exp + ",\"accountId\":1001,\"iss\":\"other\"}");
        String signingInput = header + "." + payload;
        String signature = sign(signingInput, secret);
        String token = signingInput + "." + signature;

        JwtTokenVerifier verifier = new JwtTokenVerifier(secret, issuer);
        assertNull(verifier.verifyAndGetAccountId(token));
    }

    @Test
    void verify_malformedToken_returnsNull() {
        JwtTokenVerifier verifier = new JwtTokenVerifier("s", "i");
        assertNull(verifier.verifyAndGetAccountId("bad.token"));
        assertNull(verifier.verifyAndGetAccountId(null));
        assertNull(verifier.verifyAndGetAccountId(""));
    }

    @Test
    void verify_returnsAccountId_whenTokenIsValid() {
        long exp = Instant.now().getEpochSecond() + 300;
        String token = token("HS256", exp, 1001L, ISSUER, SECRET);

        Long accountId = verifier.verifyAndGetAccountId(token);

        assertEquals(1001L, accountId);
    }

    @Test
    void verify_returnsNull_whenExpired() {
        long exp = Instant.now().getEpochSecond() - 1;
        String token = token("HS256", exp, 1001L, ISSUER, SECRET);

        assertNull(verifier.verifyAndGetAccountId(token));
    }

    @Test
    void verify_returnsNull_whenSignatureIsForged() {
        long exp = Instant.now().getEpochSecond() + 300;
        String token = token("HS256", exp, 1001L, ISSUER, "wrong-secret");

        assertNull(verifier.verifyAndGetAccountId(token));
    }

    @Test
    void verify_returnsNull_whenAlgorithmIsNotHs256() {
        long exp = Instant.now().getEpochSecond() + 300;
        String token = token("none", exp, 1001L, ISSUER, SECRET);

        assertNull(verifier.verifyAndGetAccountId(token));
    }

    private static String token(String alg, long exp, long accountId, String iss, String secret) {
        String header = "{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"demo\",\"accountId\":" + accountId
                + ",\"roles\":[\"CUSTOMER\"],\"iat\":" + (exp - 60)
                + ",\"exp\":" + exp + ",\"iss\":\"" + iss + "\"}";

        String encodedHeader = base64Url(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = hmac(signingInput, secret);

        return signingInput + "." + signature;
    }

    private static String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}



