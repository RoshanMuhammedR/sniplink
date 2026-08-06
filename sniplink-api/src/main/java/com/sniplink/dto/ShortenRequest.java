package com.sniplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to shorten a URL")
public record ShortenRequest(

		@Schema(description = "Absolute http(s) URL to shorten",
				example = "https://example.com/some/long/path")
		@NotBlank(message = "URL must not be blank")
		@Size(max = 2048, message = "URL must be at most 2048 characters")
		String url

) {
}
