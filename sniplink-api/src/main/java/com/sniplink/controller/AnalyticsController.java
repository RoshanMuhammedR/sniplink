package com.sniplink.controller;

import com.sniplink.dto.AnalyticsResponse;
import com.sniplink.dto.ErrorResponse;
import com.sniplink.service.AnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analytics", description = "Click statistics for short links")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/analytics/{code}")
	@Operation(summary = "Get click analytics",
			description = "Total click count plus the 20 most recent click events, newest first.")
	@ApiResponse(responseCode = "200", description = "Analytics returned")
	@ApiResponse(responseCode = "404", description = "Short code not found",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<AnalyticsResponse> analytics(@PathVariable String code) {
		return ResponseEntity.ok(analyticsService.getAnalytics(code));
	}

}
