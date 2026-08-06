package com.sniplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Click statistics for a short link")
public record AnalyticsResponse(

		@Schema(example = "3d7") String shortCode,
		@Schema(example = "https://example.com/some/long/path") String originalUrl,
		@Schema(example = "142") long totalClicks,
		LocalDateTime createdAt,

		@Schema(description = "Up to 20 most recent clicks, newest first")
		List<ClickDetail> recentClicks

) {
}
