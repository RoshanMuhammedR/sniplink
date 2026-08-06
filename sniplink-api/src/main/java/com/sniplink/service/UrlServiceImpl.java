package com.sniplink.service;

import com.sniplink.dto.ShortenResponse;
import com.sniplink.exception.UrlNotFoundException;
import com.sniplink.model.Url;
import com.sniplink.repository.UrlRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class UrlServiceImpl implements UrlService {

	private static final Logger log = LoggerFactory.getLogger(UrlServiceImpl.class);

	private static final String CACHE_KEY_PREFIX = "url:";
	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

	private final UrlRepository urlRepository;
	private final StringRedisTemplate redis;
	private final String baseUrl;
	private final Duration cacheTtl;

	public UrlServiceImpl(UrlRepository urlRepository,
			StringRedisTemplate redis,
			@Value("${app.base-url}") String baseUrl,
			@Value("${app.cache.url-ttl-hours}") long cacheTtlHours) {
		this.urlRepository = urlRepository;
		this.redis = redis;
		this.baseUrl = stripTrailingSlash(baseUrl);
		this.cacheTtl = Duration.ofHours(cacheTtlHours);
	}

	@Override
	@Transactional
	public ShortenResponse shortenUrl(String rawUrl) {
		String normalized = validateAndNormalize(rawUrl);

		Optional<Url> existing = urlRepository.findByOriginalUrl(normalized);
		if (existing.isPresent()) {
			Url url = existing.get();
			cache(url.getShortCode(), url.getOriginalUrl());
			return toResponse(url);
		}

		// The short code is a function of the id, so the row has to exist before
		// it can be computed. save() assigns the id from the sequence; setting the
		// code on the managed instance is picked up by dirty checking and flushed
		// as an UPDATE in this same transaction.
		Url url = urlRepository.save(new Url(normalized));
		url.setShortCode(Base62Encoder.encode(url.getId()));
		urlRepository.flush();

		cache(url.getShortCode(), url.getOriginalUrl());
		return toResponse(url);
	}

	@Override
	@Transactional(readOnly = true)
	public String resolveUrl(String shortCode) {
		String cached = redis.opsForValue().get(CACHE_KEY_PREFIX + shortCode);
		if (cached != null) {
			return cached;
		}
		Url url = urlRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new UrlNotFoundException(shortCode));
		cache(url.getShortCode(), url.getOriginalUrl());
		return url.getOriginalUrl();
	}

	@Override
	@Transactional
	public void deleteUrl(String shortCode) {
		Url url = urlRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new UrlNotFoundException(shortCode));
		evict(shortCode);
		// click_events rows go with it via the ON DELETE CASCADE foreign key.
		urlRepository.delete(url);
	}

	private ShortenResponse toResponse(Url url) {
		return new ShortenResponse(
				baseUrl + "/" + url.getShortCode(),
				url.getShortCode(),
				url.getOriginalUrl(),
				url.getCreatedAt());
	}

	private void cache(String shortCode, String originalUrl) {
		try {
			redis.opsForValue().set(CACHE_KEY_PREFIX + shortCode, originalUrl, cacheTtl);
		}
		catch (RuntimeException ex) {
			// A cache outage must not take the API down with it — Postgres is
			// still authoritative and resolveUrl() falls back to it.
			log.warn("Could not cache short code {}: {}", shortCode, ex.getMessage());
		}
	}

	private void evict(String shortCode) {
		try {
			redis.delete(CACHE_KEY_PREFIX + shortCode);
		}
		catch (RuntimeException ex) {
			log.warn("Could not evict short code {}: {}", shortCode, ex.getMessage());
		}
	}

	/**
	 * Rejects anything that is not an absolute http(s) URL with a host.
	 *
	 * <p>The scheme allowlist is a security control, not a formatting nicety: the
	 * stored value is echoed straight back into a {@code Location} header on
	 * redirect, so accepting {@code javascript:} or {@code data:} would turn every
	 * short link into a script-injection vector.
	 */
	private String validateAndNormalize(String rawUrl) {
		String trimmed = rawUrl == null ? "" : rawUrl.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("URL must not be blank");
		}
		URI uri;
		try {
			uri = new URI(trimmed);
		}
		catch (URISyntaxException ex) {
			throw new IllegalArgumentException("Malformed URL: " + trimmed);
		}
		String scheme = uri.getScheme();
		if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Only http and https URLs are supported");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("URL must include a host, e.g. https://example.com/path");
		}
		return trimmed;
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

}
