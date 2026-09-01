package com.polaris.mes.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Issues short-lived, one-time image challenges for repeated login failures. */
@Component
public class CaptchaService {
    private static final int CODE_LENGTH = 5;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;
    private static final long TTL_SECONDS = 300;
    private static final int MAX_CHALLENGES = 10_000;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> requiredByLogin = new ConcurrentHashMap<>();

    public String loginKey(HttpServletRequest request, Map<String, Object> payload) {
        String tenantCode = normalize(payloadValue(payload, "tenantCode"), 64);
        String username = normalize(payloadValue(payload, "username"), 128);
        String remoteAddress = normalize(request.getRemoteAddr(), 64);
        return remoteAddress + "|" + tenantCode + "|" + username;
    }

    public boolean isRequired(String loginKey) {
        return requiredByLogin.containsKey(loginKey);
    }

    public boolean verify(String loginKey, String challengeId, String answer) {
        String activeChallengeId = requiredByLogin.remove(loginKey);
        if (activeChallengeId == null || challengeId == null || !activeChallengeId.equals(challengeId)) return false;

        Challenge challenge = challenges.remove(challengeId);
        if (challenge == null || !loginKey.equals(challenge.loginKey()) || challenge.expiresAt().isBefore(Instant.now())) return false;

        String normalizedAnswer = answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
        return MessageDigest.isEqual(challenge.code().getBytes(StandardCharsets.UTF_8), normalizedAnswer.getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, Object> issue(String loginKey) {
        purgeExpired();
        if (challenges.size() >= MAX_CHALLENGES) {
            for (Map.Entry<String, Challenge> entry : challenges.entrySet()) {
                if (challenges.remove(entry.getKey(), entry.getValue())) break;
            }
        }
        String challengeId = UUID.randomUUID().toString();
        String code = randomCode();
        Challenge challenge = new Challenge(loginKey, code, Instant.now().plusSeconds(TTL_SECONDS));
        String previousId = requiredByLogin.put(loginKey, challengeId);
        if (previousId != null) challenges.remove(previousId);
        challenges.put(challengeId, challenge);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", challengeId);
        result.put("image", renderImage(code));
        result.put("expiresIn", TTL_SECONDS);
        return result;
    }

    public void clear(String loginKey) {
        String challengeId = requiredByLogin.remove(loginKey);
        if (challengeId != null) challenges.remove(challengeId);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 248, 252));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            for (int i = 0; i < 18; i++) {
                graphics.setColor(new Color(150 + random.nextInt(70), 180 + random.nextInt(55), 205 + random.nextInt(45), 110));
                graphics.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2 + random.nextInt(4), 2 + random.nextInt(4));
            }
            for (int i = 0; i < 5; i++) {
                graphics.setColor(new Color(95, 145, 190, 100));
                graphics.draw(new Line2D.Double(0, random.nextInt(HEIGHT), WIDTH, random.nextInt(HEIGHT)));
            }

            graphics.setFont(new Font("SansSerif", Font.BOLD, 27));
            int x = 16;
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(35 + random.nextInt(55), 70 + random.nextInt(70), 120 + random.nextInt(75)));
                graphics.drawString(String.valueOf(code.charAt(i)), x, 33 + random.nextInt(5));
                x += 27 + random.nextInt(3);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("验证码生成失败", exception);
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        // Keep the requirement marker after the image expires. The next login
        // must therefore fail verification and receive a fresh challenge.
        if (challenges.size() <= MAX_CHALLENGES) return;
        challenges.entrySet().removeIf(entry -> !requiredByLogin.containsValue(entry.getKey()));
    }

    private static String payloadValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private record Challenge(String loginKey, String code, Instant expiresAt) {}
}
