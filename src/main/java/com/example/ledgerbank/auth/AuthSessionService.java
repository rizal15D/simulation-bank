package com.example.ledgerbank.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redis;
    private final Duration sessionTtl;

    public AuthSessionService(StringRedisTemplate redis,
                              @Value("${ledgerbank.auth.session-ttl}") Duration sessionTtl) {
        this.redis = redis;
        this.sessionTtl = sessionTtl;
    }

    public TokenResponse create(AppUser user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Map<String, String> session = new HashMap<>();
        session.put("userId", user.getId().toString());
        session.put("customerId", user.getCustomerId() == null ? "" : user.getCustomerId().toString());
        session.put("email", user.getEmail());
        session.put("role", user.getRole().name());
        String key = key(token);
        redis.opsForHash().putAll(key, session);
        if (!Boolean.TRUE.equals(redis.expire(key, sessionTtl))) {
            redis.delete(key);
            throw new DataAccessResourceFailureException("Authentication session TTL could not be set");
        }
        return new TokenResponse(token, "Bearer", Instant.now().plus(sessionTtl), UserResponse.from(user));
    }

    public Optional<BankingPrincipal> find(String token) {
        if (token == null || token.length() != 43) {
            return Optional.empty();
        }
        Map<Object, Object> session = redis.opsForHash().entries(key(token));
        if (session.isEmpty()) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(session.get("userId").toString());
            String customerValue = session.get("customerId").toString();
            UUID customerId = customerValue.isBlank() ? null : UUID.fromString(customerValue);
            return Optional.of(new BankingPrincipal(userId, customerId, session.get("email").toString(),
                    UserRole.valueOf(session.get("role").toString())));
        } catch (RuntimeException invalidSession) {
            redis.delete(key(token));
            return Optional.empty();
        }
    }

    public void delete(String token) {
        if (token != null && token.length() == 43) {
            redis.delete(key(token));
        }
    }

    private String key(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
