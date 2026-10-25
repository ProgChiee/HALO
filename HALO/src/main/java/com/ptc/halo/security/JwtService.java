package com.ptc.halo.security;

import com.ptc.halo.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${jwt.secret:}") String secret) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(secret == null ? "" : secret); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("JWT_SECRET must be Base64-encoded random key material (at least 32 bytes)"); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET is required: use at least 32 random bytes encoded as Base64");
        key = Keys.hmacShaKeyFor(bytes);
    }
    public String generateToken(UserEntity user) {
        if (user.getId() == null || user.getId() <= 0) throw new IllegalArgumentException("Persisted account required");
        return Jwts.builder().subject(user.getId().toString()).claim("identity", "user-id-v1").claim("ver", user.getTokenVersion())
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
    public Long extractUserId(String token) { return accountId(claims(token)); }
    private Long accountId(Claims parsed) {
        // No fallback to email: legacy tokens must be replaced by a fresh login.
        if (!"user-id-v1".equals(parsed.get("identity")) || parsed.getExpiration() == null
                || parsed.getSubject() == null || !parsed.getSubject().matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid token identity");
        return Long.valueOf(parsed.getSubject());
    }
    public boolean isCurrent(String token, UserDetails details) {
        if (!(details instanceof CustomUserDetails account)) return false;
        Claims parsed = claims(token);
        Object version = parsed.get("ver");
        return accountId(parsed).equals(account.getUserId()) && version instanceof Number
                && ((Number) version).longValue() == account.getTokenVersion();
    }
    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
