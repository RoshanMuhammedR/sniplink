package com.sniplink.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's IP, honouring a proxy hop if one is present. Shared by
 * the rate limiter and by click logging so both key on the same value.
 */
public final class ClientIpResolver {

	private static final String UNKNOWN = "unknown";
	private static final int MAX_LENGTH = 45; // fits IPv6, matches the column width

	private ClientIpResolver() {
	}

	public static String resolve(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			// Left-most entry is the original client; the rest are proxies.
			String first = forwarded.split(",")[0].trim();
			if (!first.isEmpty()) {
				return truncate(first);
			}
		}
		String remote = request.getRemoteAddr();
		return remote == null || remote.isBlank() ? UNKNOWN : truncate(remote);
	}

	private static String truncate(String value) {
		return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
	}

}
