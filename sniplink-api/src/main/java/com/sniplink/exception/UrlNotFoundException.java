package com.sniplink.exception;

public class UrlNotFoundException extends RuntimeException {

	public UrlNotFoundException(String shortCode) {
		super("Short URL '" + shortCode + "' not found");
	}

}
