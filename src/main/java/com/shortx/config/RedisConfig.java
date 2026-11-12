package com.shortx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configures a JSON-serializing {@code RedisTemplate} for cached domain
 * objects (see cache.ShortUrlCacheService). {@code StringRedisTemplate}
 * (auto-configured by Spring Boot, used directly where needed) handles
 * the separate plain string/counter operations (click-count INCR, pending
 * key tracking) — those don't need JSON at all, so keeping them on a
 * distinct, simpler template avoids paying JSON serialization overhead on
 * an operation that's just an integer.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        // Reuses the Spring-managed ObjectMapper (already configured with
        // the JSR-310 module for OffsetDateTime) rather than constructing
        // a fresh one, via .copy() so this doesn't mutate the shared
        // instance Spring MVC uses for HTTP request/response bodies.
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper.copy());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
