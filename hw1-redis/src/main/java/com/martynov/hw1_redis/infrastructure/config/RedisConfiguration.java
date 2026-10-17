package com.martynov.hw1_redis.infrastructure.config;

import io.lettuce.core.ReadFrom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfiguration {

    // Для чтения с мастера
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(sentinelConfiguration());
    }

    // Для чтения с реплик (достижения и лидерборд)
    @Bean
    public RedisConnectionFactory replicaConnectionFactory() {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .build();
        return new LettuceConnectionFactory(sentinelConfiguration(), client);
    }

    private RedisSentinelConfiguration sentinelConfiguration() {
        return new RedisSentinelConfiguration()
                .master("mymaster")
                .sentinel("192.168.0.5", 26379)
                .sentinel("192.168.0.6", 26379)
                .sentinel("192.168.0.7", 26379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            @Qualifier("replicaConnectionFactory") RedisConnectionFactory factory,
            ObjectMapper objectMapper
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJacksonJsonRedisSerializer(objectMapper));
        template.setHashValueSerializer(new GenericJacksonJsonRedisSerializer(objectMapper));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(
            @Qualifier("redisConnectionFactory") RedisConnectionFactory factory
    ) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public StringRedisTemplate replicaStringRedisTemplate(
            @Qualifier("replicaConnectionFactory") RedisConnectionFactory factory
    ) {
        return new StringRedisTemplate(factory);
    }
}
