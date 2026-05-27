package com.fininsight.transaction.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 不同缓存场景，不同TTL
        RedisCacheConfiguration reportCache = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))            // 报表30分钟
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .prefixCacheNameWith("fin:");

        RedisCacheConfiguration hotCache = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(30))              // 热点数据30秒
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .prefixCacheNameWith("fin:");

        RedisCacheConfiguration listCache = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(10))              // 列表10秒
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .prefixCacheNameWith("fin:");

        java.util.Map<String, RedisCacheConfiguration> configs = new java.util.HashMap<>();
        configs.put("report", reportCache);
        configs.put("hot", hotCache);
        configs.put("list", listCache);

        return RedisCacheManager.builder(factory)
            .cacheDefaults(listCache)
            .withInitialCacheConfigurations(configs)
            .build();
    }
}
