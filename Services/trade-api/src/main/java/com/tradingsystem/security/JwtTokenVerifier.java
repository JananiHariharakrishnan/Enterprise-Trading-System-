package com.tradingsystem.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies HS256 JWTs issued by the auth service.
 */
public class JwtTokenVerifier {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Pattern EXP_FIELD = Pattern.compile("\"exp\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ACCOUNT_ID_FIELD = Pattern.compile("\"accountId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ALG_FIELD = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ISS_FIELD = Pattern.compile("\"iss\"\\s*:\\s*\"([^\"]*)\"");

    private final byte[] secret;
    private final String issuer;

    public JwtTokenVerifier(String secret, String issuer) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
    }

    public Long verifyAndGetAccountId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String signature = parts[2];

        // 1) Verify signature before trusting any token data.
        String signingInput = encodedHeader + "." + encodedPayload;
        String expectedSignature = sign(signingInput);

        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        String payload = decodeUtf8(encodedPayload);
        if (payload == null) {
            return null;
        }

        // 2) Verify expiry.
        Long exp = extractLong(payload, EXP_FIELD);
        if (exp == null) {
            return null;
        }

        long now = Instant.now().getEpochSecond();
        if (exp <= now) {
            return null;
        }

        String header = decodeUtf8(encodedHeader);
        if (header == null) {
            return null;
        }

        // 3) Verify the declared algorithm.
        String alg = extractString(header, ALG_FIELD);
        if (!"HS256".equals(alg)) {
            return null;
        }

        if (issuer != null && !issuer.isBlank()) {
            String tokenIssuer = extractString(payload, ISS_FIELD);
            if (!issuer.equals(tokenIssuer)) {
                return null;
            }
        }

        Long accountId = extractLong(payload, ACCOUNT_ID_FIELD);
        if (accountId == null) {
            return null;
        }
        if (accountId <= 0) {
            return null;
        }

        return accountId;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hmac = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(hmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify JWT", ex);
        }
    }

    private String decodeUtf8(String encoded) {
        try {
            byte[] bytes = URL_DECODER.decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long extractLong(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractString(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }
}



