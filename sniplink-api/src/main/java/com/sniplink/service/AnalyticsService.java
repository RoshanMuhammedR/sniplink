package com.sniplink.service;

import com.sniplink.dto.AnalyticsResponse;
import com.sniplink.dto.ClickDetail;
import com.sniplink.exception.UrlNotFoundException;
import com.sniplink.model.Url;
import com.sniplink.repository.ClickEventRepository;
import com.sniplink.repository.UrlRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalyticsService {

	private final UrlRepository urlRepository;
	private final ClickEventRepository clickEventRepository;

	public AnalyticsService(UrlRepository urlRepository, ClickEventRepository clickEventRepository) {
		this.urlRepository = urlRepository;
		this.clickEventRepository = clickEventRepository;
	}

	@Transactional(readOnly = true)
	public AnalyticsResponse getAnalytics(String shortCode) {
		Url url = urlRepository.findByShortCode(shortCode)
				.orElseThrow(() -> new UrlNotFoundException(shortCode));

		List<ClickDetail> recent = clickEventRepository
				.findTop20ByUrl_IdOrderByClickedAtDesc(url.getId())
				.stream()
				.map(event -> new ClickDetail(
						event.getIpAddress(),
						event.getUserAgent(),
						event.getReferrer(),
						event.getClickedAt()))
				.toList();

		return new AnalyticsResponse(
				url.getShortCode(),
				url.getOriginalUrl(),
				url.getClickCount(),
				url.getCreatedAt(),
				recent);
	}

}
