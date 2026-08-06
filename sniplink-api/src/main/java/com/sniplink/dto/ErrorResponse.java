package com.sniplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Uniform error body returned by every failing endpoint")
public record ErrorResponse(

		@Schema(example = "404") int status,
		@Schema(example = "Not Found") String error,
		@Schema(example = "Short URL 'xyz' not found") String message,
		LocalDateTime timestamp

) {

	public static ErrorResponse of(org.springframework.http.HttpStatus status, String message) {
		return new ErrorResponse(status.value(), status.getReasonPhrase(), message, LocalDateTime.now());
	}

}
