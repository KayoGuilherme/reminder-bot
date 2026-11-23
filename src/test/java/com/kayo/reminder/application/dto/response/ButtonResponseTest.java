package com.kayo.reminder.application.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ButtonResponseTest {

	@Test
	void keepsTheTextAndTheCallbackData() {
		ButtonResponse button = new ButtonResponse("Done", "task:done:1");

		assertThat(button.text()).isEqualTo("Done");
		assertThat(button.callbackData()).isEqualTo("task:done:1");
	}
}
