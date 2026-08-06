package com.sniplink.service;

import com.sniplink.dto.ShortenResponse;

public interface UrlService {

	ShortenResponse shortenUrl(String rawUrl);

	/**
	 * @return the original URL behind {@code shortCode}
	 * @throws com.sniplink.exception.UrlNotFoundException if the code is unknown
	 */
	String resolveUrl(String shortCode);

	void deleteUrl(String shortCode);

}
