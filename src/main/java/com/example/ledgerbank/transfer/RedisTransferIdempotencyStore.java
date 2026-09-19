package com.example.ledgerbank.transfer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisTransferIdempotencyStore {
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisTransferIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<CachedTransfer> get(UUID actorId, String idempotencyKey) {
        String value = redis.opsForValue().get(resultKey(actorId, idempotencyKey));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        try {
            return Optional.of(new CachedTransfer(parts[0], UUID.fromString(parts[1])));
        } catch (RuntimeException invalidCache) {
            redis.delete(resultKey(actorId, idempotencyKey));
            return Optional.empty();
        }
    }

    public void put(UUID actorId, String idempotencyKey, String requestHash, UUID transferId, Duration ttl) {
        redis.opsForValue().set(resultKey(actorId, idempotencyKey), requestHash + ":" + transferId, ttl);
    }

    public boolean acquire(UUID actorId, String idempotencyKey, String owner, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey(actorId, idempotencyKey), owner, ttl));
    }

    public void release(UUID actorId, String idempotencyKey, String owner) {
        redis.execute(RELEASE_LOCK, List.of(lockKey(actorId, idempotencyKey)), owner);
    }

    private String resultKey(UUID actorId, String idempotencyKey) {
        return "idempotency:transfer:" + digest(actorId, idempotencyKey);
    }

    private String lockKey(UUID actorId, String idempotencyKey) {
        return "idempotency:transfer:lock:" + digest(actorId, idempotencyKey);
    }

    private String digest(UUID actorId, String idempotencyKey) {
        try {
            byte[] value = (actorId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public record CachedTransfer(String requestHash, UUID transferId) {}
}
