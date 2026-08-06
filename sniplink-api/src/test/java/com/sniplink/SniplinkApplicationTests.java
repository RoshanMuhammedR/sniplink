package com.sniplink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sniplink.service.Base62Encoder;

/**
 * Pure unit tests for the Base62 codec. Deliberately does not boot a Spring
 * context so the build stays runnable without Postgres and Redis on hand.
 */
class SniplinkApplicationTests {

	@Test
	void encodesZeroAsSingleDigit() {
		assertThat(Base62Encoder.encode(0L)).isEqualTo("0");
	}

	@Test
	void encodesKnownValues() {
		assertThat(Base62Encoder.encode(1L)).isEqualTo("1");
		assertThat(Base62Encoder.encode(61L)).isEqualTo("Z");
		assertThat(Base62Encoder.encode(62L)).isEqualTo("10");
		assertThat(Base62Encoder.encode(3843L)).isEqualTo("ZZ");
	}

	@Test
	void roundTripsAcrossMagnitudes() {
		long[] samples = { 0L, 1L, 61L, 62L, 12345L, 999_999L, Long.MAX_VALUE };
		for (long sample : samples) {
			assertThat(Base62Encoder.decode(Base62Encoder.encode(sample))).isEqualTo(sample);
		}
	}

	@Test
	void rejectsCharactersOutsideTheAlphabet() {
		assertThatThrownBy(() -> Base62Encoder.decode("abc-def"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNegativeInput() {
		assertThatThrownBy(() -> Base62Encoder.encode(-1L))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
