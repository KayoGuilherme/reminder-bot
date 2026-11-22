package com.kayo.reminder.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.in.IncomingCommand.Kind;
import org.junit.jupiter.api.Test;

class IncomingCommandTest {

	@Test
	void textPopulatesOnlyText() {
		IncomingCommand c = IncomingCommand.text(10, 20, 30, "buy milk");
		assertThat(c.kind()).isEqualTo(Kind.TEXT);
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
		IncomingCommand c = IncomingCommand.voice(10, 20, 30, "file-1", 7);
		assertThat(c.kind()).isEqualTo(Kind.VOICE);
		assertThat(c.voiceFileId()).isEqualTo("file-1");
		assertThat(c.voiceDurationSeconds()).isEqualTo(7);
		assertThat(c.text()).isNull();
		assertThat(c.callbackId()).isNull();
		assertThat(c.callbackData()).isNull();
	}

	@Test
	void callbackPopulatesOnlyCallback() {
		IncomingCommand c = IncomingCommand.callback(10, 20, 30, "cb-1", "x:1");
		assertThat(c.kind()).isEqualTo(Kind.CALLBACK);
		assertThat(c.callbackId()).isEqualTo("cb-1");
		assertThat(c.callbackData()).isEqualTo("x:1");
		assertThat(c.messageId()).isEqualTo(30);
		assertThat(c.text()).isNull();
		assertThat(c.voiceFileId()).isNull();
	}

	@Test
	void rejectsNoKindOrSeveralKinds() {
		assertThatThrownBy(() -> new IncomingCommand(1, 2, 3, null, null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IncomingCommand(1, 2, 3, "t", "f", 1, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
