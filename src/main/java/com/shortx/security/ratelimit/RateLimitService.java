package com.shortx.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Token-bucket rate limiting via Bucket4j, with two tiers already
 * declared in application.yml since Milestone 1
 * ({@code shortx.rate-limit.anonymous} / {@code .authenticated}).
 *
 * <p><b>Distributed by default, with an honest fallback.</b> The primary
 * path backs every bucket with Redis (via Bucket4j's Lettuce
 * integration), so rate limits are enforced correctly across every
 * running instance of this service, not per-instance — which is the only
 * version of rate limiting that's actually correct once this service is
 * horizontally scaled to more than one instance behind a load balancer.
 *
 * <p>The Redis-backed proxy manager is constructed once at startup inside
 * a try/catch. On ANY failure — Redis unreachable, or (worth stating
 * plainly, since this sandbox has no access to Maven Central to verify
 * the exact Bucket4j/Lettuce integration API against the real jar) a
 * possible API mismatch in this exact wiring — this falls back to
 * per-instance in-memory buckets rather than failing application
 * startup. That fallback is a real, working, correct rate limiter; it
 * just enforces limits independently per instance instead of globally,
 * which is a reasonable and clearly-logged degradation rather than a
 * silent one. This mirrors the identical philosophy already applied to
 * Redis cache failures and a missing GeoIP database in earlier
 * milestones — treat this component's specialized dependency as
 * something that can be absent, and keep the application running anyway.
 */
@Service
@Slf4j
public class RateLimitService {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${shortx.rate-limit.anonymous.capacity}")
    private long anonymousCapacity;

    @Value("${shortx.rate-limit.anonymous.refill-per-minute}")
    private long anonymousRefillPerMinute;

    @Value("${shortx.rate-limit.authenticated.capacity}")
    private long authenticatedCapacity;

    @Value("${shortx.rate-limit.authenticated.refill-per-minute}")
    private long authenticatedRefillPerMinute;

    private ProxyManager<String> proxyManager;
    private final ConcurrentHashMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        try {
            RedisURI.Builder uriBuilder = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort);
            if (redisPassword != null && !redisPassword.isBlank()) {
                uriBuilder.withPassword(redisPassword.toCharArray());
            }

            RedisClient redisClient = RedisClient.create(uriBuilder.build());
            StatefulRedisConnection<String, byte[]> connection =
                    redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

            this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
            log.info("Rate limiting backed by Redis at {}:{} (distributed across all app instances)", redisHost, redisPort);
        } catch (Exception e) {
            log.warn("Could not initialize Redis-backed rate limiting ({}). Falling back to per-instance "
                    + "in-memory rate limiting - limits will be enforced independently per application "
                    + "instance rather than globally. Fine for a single instance; revisit for a true "
                    + "horizontally-scaled deployment.", e.getMessage());
            this.proxyManager = null;
        }
    }

    public RateLimitDecision tryConsume(String key, boolean authenticated) {
        long capacity = authenticated ? authenticatedCapacity : anonymousCapacity;
        long refillPerMinute = authenticated ? authenticatedRefillPerMinute : anonymousRefillPerMinute;

        Bucket bucket = resolveBucket(key, capacity, refillPerMinute);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        return new RateLimitDecision(
                probe.isConsumed(),
                probe.getRemainingTokens(),
                capacity,
                Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket resolveBucket(String key, long capacity, long refillPerMinute) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(refillPerMinute, Duration.ofMinutes(1))))
                .build();

        if (proxyManager != null) {
            return proxyManager.builder().build(key, configSupplier);
        }

        return localBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(refillPerMinute, Duration.ofMinutes(1))))
                .build());
    }
}
