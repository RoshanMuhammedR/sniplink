package com.sniplink.service;

/**
 * Bijective Base62 codec mapping database ids to short codes.
 *
 * <p>Collision-free by construction: every id maps to exactly one code and back
 * again, so there is no generate-and-check-for-duplicates loop anywhere.
 *
 * <p>Static utility, not a Spring bean.
 */
public final class Base62Encoder {

	private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int BASE = ALPHABET.length();

	private Base62Encoder() {
	}

	public static String encode(long id) {
		if (id < 0) {
			throw new IllegalArgumentException("Cannot encode a negative id: " + id);
		}
		// The divide-down loop below produces nothing for zero, so it is special-cased.
		if (id == 0) {
			return String.valueOf(ALPHABET.charAt(0));
		}
		StringBuilder sb = new StringBuilder();
		long remaining = id;
		while (remaining > 0) {
			sb.append(ALPHABET.charAt((int) (remaining % BASE)));
			remaining /= BASE;
		}
		return sb.reverse().toString();
	}

	public static long decode(String code) {
		if (code == null || code.isEmpty()) {
			throw new IllegalArgumentException("Cannot decode a blank code");
		}
		long value = 0;
		for (int i = 0; i < code.length(); i++) {
			int digit = ALPHABET.indexOf(code.charAt(i));
			if (digit < 0) {
				throw new IllegalArgumentException(
						"Character '" + code.charAt(i) + "' is not in the Base62 alphabet");
			}
			value = value * BASE + digit;
		}
		return value;
	}

}
