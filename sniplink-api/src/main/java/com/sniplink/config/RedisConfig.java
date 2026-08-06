package com.sniplink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Plain {@link StringRedisTemplate} — both values we store (a URL and a request
 * counter) are strings, and we need explicit per-key TTLs, which is why this is
 * used directly instead of the {@code @Cacheable} abstraction.
 */
@Configuration
public class RedisConfig {

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

}
