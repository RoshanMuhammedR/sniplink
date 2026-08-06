package com.sniplink.interceptor;

import com.sniplink.exception.RateLimitExceededException;
import com.sniplink.util.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Fixed-window per-IP limiter backed by Redis {@code INCR}.
 *
 * <p>Exceptions thrown from {@code preHandle} are routed through the
 * DispatcherServlet's exception resolvers, so
 * {@link com.sniplink.exception.GlobalExceptionHandler} renders the 429 body.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

	private static final String RATE_KEY_PREFIX = "rate:";
	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final StringRedisTemplate redis;
	private final int requestsPerMinute;

	public RateLimitInterceptor(StringRedisTemplate redis,
			@Value("${app.rate-limit.requests-per-minute}") int requestsPerMinute) {
		this.redis = redis;
		this.requestsPerMinute = requestsPerMinute;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}

		String ip = ClientIpResolver.resolve(request);
		String key = RATE_KEY_PREFIX + ip;

		Long count;
		try {
			count = redis.opsForValue().increment(key);
			if (count != null && count == 1L) {
				redis.expire(key, WINDOW);
			}
		}
		catch (RuntimeException ex) {
			// Fail open: a Redis outage should not block shortening entirely.
			log.warn("Rate limit check skipped, Redis unavailable: {}", ex.getMessage());
			return true;
		}

		if (count != null && count > requestsPerMinute) {
			throw new RateLimitExceededException(requestsPerMinute);
		}
		return true;
	}

}
