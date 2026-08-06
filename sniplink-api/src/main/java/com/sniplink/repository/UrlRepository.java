package com.sniplink.repository;

import com.sniplink.model.Url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

	Optional<Url> findByShortCode(String shortCode);

	Optional<Url> findByOriginalUrl(String originalUrl);

	/**
	 * Incremented as a single atomic statement rather than a read-modify-write on
	 * the entity. Click logging runs on a virtual thread per redirect, so
	 * concurrent increments are the normal case here, not an edge case — loading
	 * the entity and calling setClickCount() would silently drop hits.
	 */
	@Modifying
	@Query("update Url u set u.clickCount = u.clickCount + 1 where u.id = :id")
	void incrementClickCount(@Param("id") Long id);

}
