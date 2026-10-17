package com.agrosense.frontend.backend;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

class BackendToken {

    private static final String ALGORITHM = "HmacSHA512";
    private static final String HEADER = encode("{\"alg\":\"HS512\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final long SECONDS_TO_LIVE = 120;

    private final SecretKeySpec key;

    BackendToken(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    String bearerFor(String email) {
        long now = Instant.now().getEpochSecond();
        String payload = "{\"sub\":\"" + email.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"iat\":" + now
                + ",\"exp\":" + (now + SECONDS_TO_LIVE) + "}";
        String content = HEADER + "." + encode(payload.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + content + "." + encode(sign(content));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign the backend token", exception);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
