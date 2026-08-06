package com.sniplink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_events", indexes = {
		@Index(name = "idx_click_events_url_id", columnList = "url_id")
})
public class ClickEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "click_event_seq")
	@SequenceGenerator(name = "click_event_seq", sequenceName = "click_event_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "url_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Url url;

	@Column(name = "ip_address", nullable = false, length = 45)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "referrer", length = 2048)
	private String referrer;

	@Column(name = "clicked_at", nullable = false)
	private LocalDateTime clickedAt;

	protected ClickEvent() {
		// for JPA
	}

	public ClickEvent(Url url, String ipAddress, String userAgent, String referrer) {
		this.url = url;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
		this.referrer = referrer;
	}

	@PrePersist
	void onCreate() {
		if (clickedAt == null) {
			clickedAt = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Url getUrl() {
		return url;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public String getReferrer() {
		return referrer;
	}

	public LocalDateTime getClickedAt() {
		return clickedAt;
	}

}
