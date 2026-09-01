package com.polaris.mes.security;

import com.polaris.mes.common.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Signed, stateless bearer token carrying the authenticated tenant identity. */
@Component
public class TokenService {
    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(@Value("${polaris.security.token-secret:polaris-development-secret-change-me}") String secret,
                        @Value("${polaris.security.token-ttl-seconds:28800}") long ttlSeconds) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("polaris.security.token-secret 至少需要 32 个字符");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(TenantContext.Identity identity) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = String.join("|", "1", String.valueOf(identity.tenantId()), identity.tenantCode(),
                identity.tenantName(), String.valueOf(identity.userId()), identity.username(), identity.roleCode(),
                String.valueOf(expiresAt), UUID.randomUUID().toString());
        String encoded = encode(payload);
        return "p1." + encoded + "." + encode(signature(encoded));
    }

    public TenantContext.Identity verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || !"p1".equals(parts[0])) throw new IllegalArgumentException("令牌格式无效");
            byte[] supplied = decode(parts[2]);
            byte[] expected = signature(parts[1]);
            if (!MessageDigest.isEqual(supplied, expected)) throw new IllegalArgumentException("令牌签名无效");
            String[] fields = new String(decode(parts[1]), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 9 || !"1".equals(fields[0])) throw new IllegalArgumentException("令牌内容无效");
            if (Long.parseLong(fields[7]) < Instant.now().getEpochSecond()) throw new IllegalArgumentException("令牌已过期");
            return new TenantContext.Identity(Long.parseLong(fields[1]), fields[2], fields[3],
                    Long.parseLong(fields[4]), fields[5], fields[6]);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("登录状态无效或已过期");
        }
    }

    private byte[] signature(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发登录令牌", ex);
        }
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
