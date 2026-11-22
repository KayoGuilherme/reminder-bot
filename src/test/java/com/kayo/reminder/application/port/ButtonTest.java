package com.kayo.reminder.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.port.out.Button;
import org.junit.jupiter.api.Test;

class ButtonTest {

	@Test
	void acceptsExactly64Bytes() {
		assertThat(new Button("ok", "a".repeat(64)).callbackData()).hasSize(64);
	}

	@Test
	void rejects65Bytes() {
		assertThatThrownBy(() -> new Button("ok", "a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void countsBytesNotCharacters() {
		// "é" is 2 bytes in UTF-8: 32 of them are 64 bytes, 33 are 66 bytes.
		assertThat(new Button("ok", "é".repeat(32)).callbackData()).hasSize(32);
		assertThatThrownBy(() -> new Button("ok", "é".repeat(33))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsBlankTextAndEmptyData() {
		assertThatThrownBy(() -> new Button(" ", "x")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Button("ok", "")).isInstanceOf(IllegalArgumentException.class);
	}
}
