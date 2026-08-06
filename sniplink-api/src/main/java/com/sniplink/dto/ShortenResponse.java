package com.sniplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A freshly created (or previously existing) short link")
public record ShortenResponse(

		@Schema(example = "http://localhost:8080/3d7") String shortUrl,
		@Schema(example = "3d7") String shortCode,
		@Schema(example = "https://example.com/some/long/path") String originalUrl,
		LocalDateTime createdAt

) {
}
