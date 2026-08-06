package com.sniplink.exception;

public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException(int limitPerMinute) {
		super("Rate limit exceeded: at most " + limitPerMinute + " requests per minute are allowed");
	}

}
