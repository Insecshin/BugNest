package com.nolla.bugnest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class LoginRateLimiter {
    private static final DefaultRedisScript<Long> CONSUME_IP_TOKEN = new DefaultRedisScript<>("""
            local values = redis.call('HMGET', KEYS[1], 'tokens', 'updatedAt')
            local nowParts = redis.call('TIME')
            local now = tonumber(nowParts[1]) * 1000 + math.floor(tonumber(nowParts[2]) / 1000)
            local capacity = tonumber(ARGV[1])
            local refillPerMs = tonumber(ARGV[2])
            local tokens = tonumber(values[1]) or capacity
            local updatedAt = tonumber(values[2]) or now
            tokens = math.min(capacity, tokens + math.max(0, now - updatedAt) * refillPerMs)
            local waitMs = 0
            if tokens < 1 then
                waitMs = math.ceil((1 - tokens) / refillPerMs)
            else
                tokens = tokens - 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updatedAt', now)
            redis.call('PEXPIRE', KEYS[1], 120000)
            return math.ceil(waitMs / 1000)
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_ACCOUNT = new DefaultRedisScript<>("""
            local blockedUntil = tonumber(redis.call('HGET', KEYS[1], 'blockedUntil')) or 0
            local nowParts = redis.call('TIME')
            local now = tonumber(nowParts[1]) * 1000 + math.floor(tonumber(nowParts[2]) / 1000)
            local retryAfter = math.max(0, math.ceil((blockedUntil - now) / 1000))
            if retryAfter > 0 then return retryAfter end
            if not redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', 10000) then return 1 end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return -1 end
            local failures = redis.call('HINCRBY', KEYS[1], 'failures', 1)
            local delay = 0
            if failures >= 5 then
                delay = 30
                for i = 6, failures do
                    delay = math.min(delay * 2, 900)
                    if delay == 900 then break end
                end
                local nowParts = redis.call('TIME')
                local now = tonumber(nowParts[1]) * 1000 + math.floor(tonumber(nowParts[2]) / 1000)
                redis.call('HSET', KEYS[1], 'blockedUntil', now + delay * 1000)
            end
            redis.call('EXPIRE', KEYS[1], 86400)
            redis.call('DEL', KEYS[2])
            return delay
            """, Long.class);

    private static final DefaultRedisScript<Long> RECORD_SUCCESS = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return -1 end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_ACCOUNT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) == ARGV[1] then return redis.call('DEL', KEYS[2]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final byte[] hmacKey;

    public LoginRateLimiter(
            StringRedisTemplate redis,
            @Value("${bugnest.security.rate-limit.hmac-key}") String hmacKey
    ) {
        if (hmacKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("BUGNEST_RATE_LIMIT_HMAC_KEY must be at least 32 bytes");
        }
        this.redis = redis;
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public void consumeIpToken(String ipAddress) {
        long retryAfter = execute(CONSUME_IP_TOKEN, List.of("bugnest:login:ip:" + hmac(ipAddress)), "30", "0.0005");
        if (retryAfter > 0) {
            throw new LoginRateLimitedException(retryAfter);
        }
    }

    public String accountKey(long accountId) {
        return "bugnest:login:account:" + accountId;
    }

    public String unknownIdentifierKey(String normalizedIdentifier) {
        return "bugnest:login:unknown:" + hmac(normalizedIdentifier);
    }

    public String acquireAccount(String accountKey) {
        String owner = UUID.randomUUID().toString();
        long retryAfter = execute(ACQUIRE_ACCOUNT, leaseKeys(accountKey), owner);
        if (retryAfter > 0) {
            throw new LoginRateLimitedException(retryAfter);
        }
        return owner;
    }

    public void recordFailure(String accountKey, String owner) {
        requireOwner(execute(RECORD_FAILURE, leaseKeys(accountKey), owner));
    }

    public void recordSuccess(String accountKey, String owner) {
        requireOwner(execute(RECORD_SUCCESS, leaseKeys(accountKey), owner));
    }

    public void releaseAccount(String accountKey, String owner) {
        execute(RELEASE_ACCOUNT, leaseKeys(accountKey), owner);
    }

    private List<String> leaseKeys(String accountKey) {
        return List.of(accountKey, accountKey + ":inflight");
    }

    private void requireOwner(long result) {
        if (result < 0) {
            throw new LoginRateLimitUnavailableException(new IllegalStateException("Authentication lease expired"));
        }
    }

    private long execute(DefaultRedisScript<Long> script, List<String> keys, String... arguments) {
        try {
            Long result = redis.execute(script, keys, (Object[]) arguments);
            if (result == null) {
                throw new IllegalStateException("Redis rate-limit script returned no result");
            }
            return result;
        } catch (DataAccessException exception) {
            throw new LoginRateLimitUnavailableException(exception);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
