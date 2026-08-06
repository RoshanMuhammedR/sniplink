package com.sniplink.config;

import com.sniplink.interceptor.RateLimitInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;
	private final String[] allowedOrigins;

	public WebConfig(RateLimitInterceptor rateLimitInterceptor,
			@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.rateLimitInterceptor = rateLimitInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Shorten is the only write endpoint worth throttling; redirects and
		// analytics are reads and stay unmetered.
		registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/v1/shorten");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.maxAge(3600);
	}

}
