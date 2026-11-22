package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.in.IncomingCommand.Kind;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

class TelegramUpdateMapperTest {

	private final TelegramUpdateMapper mapper = new TelegramUpdateMapper();

	@Test
	void textMessage() {
		IncomingCommand c = mapper.toCommand(Updates.text(1, 42, 100, "buy milk")).orElseThrow();
		assertThat(c.kind()).isEqualTo(Kind.TEXT);
		assertThat(c.text()).isEqualTo("buy milk");
		assertThat(c.chatId()).isEqualTo(100);
		assertThat(c.userId()).isEqualTo(42);
		assertThat(c.messageId()).isEqualTo(10);
		assertThat(c.voiceFileId()).isNull();
		assertThat(c.callbackId()).isNull();
	}

	@Test
	void voiceMessage() {
		IncomingCommand c = mapper.toCommand(Updates.voice(2, 42, 100, "file-1", 7)).orElseThrow();
		assertThat(c.kind()).isEqualTo(Kind.VOICE);
		assertThat(c.voiceFileId()).isEqualTo("file-1");
		assertThat(c.voiceDurationSeconds()).isEqualTo(7);
		assertThat(c.text()).isNull();
	}

	@Test
	void callbackQueryUsesTheButtonMessageForChatAndMessageId() {
		IncomingCommand c = mapper.toCommand(Updates.callback(3, 42, 100, 55, "cb-1", "x:1")).orElseThrow();
		assertThat(c.kind()).isEqualTo(Kind.CALLBACK);
		assertThat(c.callbackId()).isEqualTo("cb-1");
		assertThat(c.callbackData()).isEqualTo("x:1");
		assertThat(c.chatId()).isEqualTo(100);
		assertThat(c.messageId()).isEqualTo(55);
		assertThat(c.userId()).isEqualTo(42);
		assertThat(c.text()).isNull();
	}

	@Test
	void groupChatKeepsChatIdDistinctFromUserId() {
		IncomingCommand c = mapper.toCommand(Updates.text(4, 42, -1001234L, "hi")).orElseThrow();
		assertThat(c.chatId()).isEqualTo(-1001234L);
		assertThat(c.userId()).isEqualTo(42);
	}

	@Test
	void unsupportedKindsAreEmpty() {
		// sticker/photo style message: no text, no voice
		Update photoLike = Updates.withMessage(5, Updates.message(100, 42, 50).build());
		assertThat(mapper.toCommand(photoLike)).isEmpty();

		// edited message arrives in a different field and must be ignored
		Update edited = new Update();
		edited.setUpdateId(6);
		edited.setEditedMessage(Updates.message(100, 42, 51).text("edited").build());
		assertThat(mapper.toCommand(edited)).isEmpty();

		assertThat(mapper.toCommand(new Update())).isEmpty();
	}

	@Test
	void callbackWithoutMessageIsEmpty() {
		CallbackQuery query = new CallbackQuery();
		query.setId("cb-2");
		query.setFrom(Updates.user(42));
		query.setData("x");
		Update update = new Update();
		update.setCallbackQuery(query);
		Optional<IncomingCommand> result = mapper.toCommand(update);
		assertThat(result).isEmpty();
	}

	@Test
	void messageWithoutSenderIsEmpty() {
		Update update = Updates.withMessage(7,
				Updates.message(100, 42, 1).from(null).text("x").build());
		assertThat(mapper.toCommand(update)).isEmpty();
	}
}
