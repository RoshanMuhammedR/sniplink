package com.sniplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A single recorded click on a short link")
public record ClickDetail(

		@Schema(example = "192.168.1.1") String ipAddress,
		@Schema(example = "Mozilla/5.0 ...") String userAgent,
		@Schema(example = "https://twitter.com") String referrer,
		LocalDateTime clickedAt

) {
}
