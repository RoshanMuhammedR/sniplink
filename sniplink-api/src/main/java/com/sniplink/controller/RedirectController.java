package com.sniplink.controller;

import com.sniplink.dto.ErrorResponse;
import com.sniplink.service.ClickLoggingService;
import com.sniplink.service.UrlService;
import com.sniplink.util.ClientIpResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Redirect", description = "Short code resolution")
public class RedirectController {

	private final UrlService urlService;
	private final ClickLoggingService clickLoggingService;

	public RedirectController(UrlService urlService, ClickLoggingService clickLoggingService) {
		this.urlService = urlService;
		this.clickLoggingService = clickLoggingService;
	}

	/**
	 * The path variable is constrained to the Base62 alphabet on purpose.
	 *
	 * <p>A bare {@code /{code}} sits at order 0 in RequestMappingHandlerMapping,
	 * while static resources are served near Integer.MAX_VALUE — so an
	 * unconstrained pattern would swallow {@code /swagger-ui.html} and
	 * {@code /favicon.ico} before they ever reached their handlers. Neither can
	 * match this regex (both contain a dot).
	 */
	@GetMapping("/{code:[0-9a-zA-Z]{1,10}}")
	@Operation(summary = "Resolve a short code",
			description = "Issues a 302 to the original URL and records the click asynchronously.")
	@ApiResponse(responseCode = "302", description = "Redirect to the original URL")
	@ApiResponse(responseCode = "404", description = "Short code not found",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
		String originalUrl = urlService.resolveUrl(code);

		// Fire-and-forget: crosses the proxy boundary, so it lands on a virtual
		// thread and the redirect returns without waiting for the write.
		clickLoggingService.logClick(
				code,
				ClientIpResolver.resolve(request),
				request.getHeader(HttpHeaders.USER_AGENT),
				request.getHeader(HttpHeaders.REFERER));

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, originalUrl)
				.header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
				.build();
	}

}
