package com.kayo.reminder.application.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.domain.models.enums.CommandKind;
import org.junit.jupiter.api.Test;

class IncomingCommandRequestTest {

	@Test
	void textPopulatesOnlyText() {
		IncomingCommandRequest c = IncomingCommandRequest.text(10, 20, 30, "buy milk");
		assertThat(c.kind()).isEqualTo(CommandKind.TEXT);
		assertThat(c.chatId()).isEqualTo(10);
		assertThat(c.userId()).isEqualTo(20);
		assertThat(c.messageId()).isEqualTo(30);
		assertThat(c.text()).isEqualTo("buy milk");
		assertThat(c.voiceFileId()).isNull();
		assertThat(c.voiceDurationSeconds()).isNull();
		assertThat(c.callbackId()).isNull();
		assertThat(c.callbackData()).isNull();
	}

	@Test
	void voicePopulatesOnlyVoice() {
		IncomingCommandRequest c = IncomingCommandRequest.voice(10, 20, 30, "file-1", 7);
		assertThat(c.kind()).isEqualTo(CommandKind.VOICE);
		assertThat(c.voiceFileId()).isEqualTo("file-1");
		assertThat(c.voiceDurationSeconds()).isEqualTo(7);
		assertThat(c.text()).isNull();
		assertThat(c.callbackId()).isNull();
		assertThat(c.callbackData()).isNull();
	}

	@Test
	void callbackPopulatesOnlyCallback() {
		IncomingCommandRequest c = IncomingCommandRequest.callback(10, 20, 30, "cb-1", "x:1");
		assertThat(c.kind()).isEqualTo(CommandKind.CALLBACK);
		assertThat(c.callbackId()).isEqualTo("cb-1");
		assertThat(c.callbackData()).isEqualTo("x:1");
		assertThat(c.messageId()).isEqualTo(30);
		assertThat(c.text()).isNull();
		assertThat(c.voiceFileId()).isNull();
	}
}
