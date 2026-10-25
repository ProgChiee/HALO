package com.ptc.halo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared deployment key prevents offline guessing of the small OTP space from a database dump. */
@Component
public class PasswordResetProtection {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public final int maxAttempts, ttlSeconds, cooldownSeconds, windowSeconds;
    public final int requestAccountLimit, requestIpLimit, verificationAccountLimit, verificationIpLimit;

    public PasswordResetProtection(
            @Value("${halo.password-reset.pepper:${HALO_PASSWORD_RESET_PEPPER:}}") String pepper,
            @Value("${halo.password-reset.max-attempts:5}") int maxAttempts,
            @Value("${halo.password-reset.ttl-seconds:600}") int ttlSeconds,
            @Value("${halo.password-reset.cooldown-seconds:60}") int cooldownSeconds,
            @Value("${halo.password-reset.window-seconds:3600}") int windowSeconds,
            @Value("${halo.password-reset.request-account-limit:3}") int requestAccountLimit,
            @Value("${halo.password-reset.request-ip-limit:20}") int requestIpLimit,
            @Value("${halo.password-reset.verification-account-limit:10}") int verificationAccountLimit,
            @Value("${halo.password-reset.verification-ip-limit:100}") int verificationIpLimit) {
        try { key = Base64.getDecoder().decode(pepper); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("Password-reset pepper must be base64 encoded"); }
        if (key.length < 32) throw new IllegalStateException("Configure a password-reset pepper of at least 32 random bytes");
        if (maxAttempts < 1 || maxAttempts > 10 || ttlSeconds < 60 || ttlSeconds > 1800
                || cooldownSeconds < 1 || cooldownSeconds > ttlSeconds || windowSeconds < ttlSeconds
                || windowSeconds > 86400 || requestAccountLimit < 1 || requestIpLimit < 1
                || verificationAccountLimit < 1 || verificationIpLimit < 1)
            throw new IllegalStateException("Invalid password-reset security limits");
        this.maxAttempts=maxAttempts; this.ttlSeconds=ttlSeconds; this.cooldownSeconds=cooldownSeconds;
        this.windowSeconds=windowSeconds; this.requestAccountLimit=requestAccountLimit; this.requestIpLimit=requestIpLimit;
        this.verificationAccountLimit=verificationAccountLimit; this.verificationIpLimit=verificationIpLimit;
    }

    public String newCode() { return String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public String verifier(long userId, String code) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        String nonce=HexFormat.of().formatHex(salt);
        return "v1:"+nonce+":"+digest("otp:"+userId+":"+nonce+":"+code);
    }
    public boolean matches(long userId, String code, String stored) {
        // Fail closed for legacy plaintext challenges and malformed stored values.
        if (stored == null || !stored.matches("v1:[0-9a-f]{32}:[0-9a-f]{64}")) return false;
        String[] parts=stored.split(":");
        return MessageDigest.isEqual(HexFormat.of().parseHex(parts[2]),
                HexFormat.of().parseHex(digest("otp:"+userId+":"+parts[1]+":"+code)));
    }
    public String bucket(String purpose, String identifier) { return digest("limit:"+purpose+":"+identifier); }
    private String digest(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("Reset verification unavailable"); }
    }
}
