package com.sniplink.controller;

import com.sniplink.dto.ErrorResponse;
import com.sniplink.dto.ShortenRequest;
import com.sniplink.dto.ShortenResponse;
import com.sniplink.service.UrlService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "URLs", description = "Create and remove short links")
public class UrlController {

	private final UrlService urlService;

	public UrlController(UrlService urlService) {
		this.urlService = urlService;
	}

	@PostMapping("/shorten")
	@Operation(summary = "Shorten a URL",
			description = "Returns the existing short link if this URL has already been shortened.")
	@ApiResponse(responseCode = "201", description = "Short link created")
	@ApiResponse(responseCode = "400", description = "Blank, malformed, or non-http(s) URL",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429", description = "Rate limit exceeded",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
		ShortenResponse response = urlService.shortenUrl(request.url());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@DeleteMapping("/urls/{code}")
	@Operation(summary = "Delete a short link",
			description = "Removes the link, its click history, and its cache entry.")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", description = "Short code not found",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<Void> delete(@PathVariable String code) {
		urlService.deleteUrl(code);
		return ResponseEntity.noContent().build();
	}

}
