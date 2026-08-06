package com.sniplink.service;

import com.sniplink.model.ClickEvent;
import com.sniplink.model.Url;
import com.sniplink.repository.ClickEventRepository;
import com.sniplink.repository.UrlRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fire-and-forget click recording, kept in its own bean on purpose.
 *
 * <p>{@code @Async} is implemented with a proxy, and a proxy is only involved
 * when the call arrives from outside the bean. If this method lived on
 * {@link UrlServiceImpl} next to {@code resolveUrl()}, the natural
 * {@code this.logClick(...)} call would bypass the proxy and run on the request
 * thread — no error, no warning, just a silently synchronous redirect. Keeping
 * it here forces every caller across the proxy boundary.
 */
@Service
public class ClickLoggingService {

	private static final Logger log = LoggerFactory.getLogger(ClickLoggingService.class);

	private final UrlRepository urlRepository;
	private final ClickEventRepository clickEventRepository;

	public ClickLoggingService(UrlRepository urlRepository, ClickEventRepository clickEventRepository) {
		this.urlRepository = urlRepository;
		this.clickEventRepository = clickEventRepository;
	}

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void logClick(String shortCode, String ipAddress, String userAgent, String referrer) {
		try {
			Url url = urlRepository.findByShortCode(shortCode).orElse(null);
			if (url == null) {
				// Deleted between the redirect and this task running. Nothing to record.
				return;
			}
			clickEventRepository.save(new ClickEvent(url, ipAddress, truncate(userAgent, 512),
					truncate(referrer, 2048)));
			urlRepository.incrementClickCount(url.getId());
		}
		catch (RuntimeException ex) {
			// The visitor has already been redirected; analytics must never
			// surface as a user-facing failure.
			log.warn("Failed to log click for short code {}: {}", shortCode, ex.toString());
		}
	}

	private static String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

}
