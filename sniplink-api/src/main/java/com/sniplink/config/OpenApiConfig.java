package com.sniplink.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI sniplinkOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Sniplink API")
				.version("v1")
				.description("""
						A URL shortener with Base62 short codes, Redis-backed redirect caching, \
						asynchronous click analytics, and per-IP rate limiting.""")
				.contact(new Contact().name("Sniplink"))
				.license(new License().name("MIT")));
	}

}
