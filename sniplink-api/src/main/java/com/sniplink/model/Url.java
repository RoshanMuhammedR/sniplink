package com.sniplink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
		@Index(name = "idx_urls_original_url", columnList = "original_url")
})
public class Url {

	/**
	 * SEQUENCE rather than IDENTITY so the id is available as soon as the entity
	 * is persisted, without waiting on the INSERT to come back.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "url_seq")
	@SequenceGenerator(name = "url_seq", sequenceName = "url_seq", allocationSize = 1)
	private Long id;

	@Column(name = "original_url", nullable = false, length = 2048)
	private String originalUrl;

	/**
	 * Deliberately nullable at the database level, though never null once the
	 * transaction commits.
	 *
	 * <p>The code is derived from the id, so it cannot be known until the id is
	 * assigned. Hibernate snapshots entity state when persist() queues the
	 * insert, so a value set afterwards lands in a follow-up UPDATE rather than
	 * in the INSERT itself — a NOT NULL column would reject the insert outright.
	 * Both statements run inside one transaction, so no other session ever
	 * observes the intermediate null. Uniqueness is still enforced.
	 */
	@Column(name = "short_code", unique = true, length = 10)
	private String shortCode;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "click_count", nullable = false)
	private long clickCount;

	protected Url() {
		// for JPA
	}

	public Url(String originalUrl) {
		this.originalUrl = originalUrl;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public String getShortCode() {
		return shortCode;
	}

	public void setShortCode(String shortCode) {
		this.shortCode = shortCode;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public long getClickCount() {
		return clickCount;
	}

}
