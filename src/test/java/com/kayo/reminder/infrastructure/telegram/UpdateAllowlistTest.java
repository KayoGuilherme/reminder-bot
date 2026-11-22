package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

class UpdateAllowlistTest {

	private final UpdateAllowlist allowlist = new UpdateAllowlist(42);

	@Test
	void allowedUserPassesWithoutWarning() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			assertThat(allowlist.isAllowed(Updates.text(1, 42, 100, "hello"))).isTrue();
			assertThat(allowlist.isAllowed(Updates.callback(2, 42, 100, 5, "cb", "x"))).isTrue();
			assertThat(logs.events(Level.WARN)).isEmpty();
		}
	}

	@Test
	void otherUserIsRejectedWithWarningContainingIdButNotText() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			assertThat(allowlist.isAllowed(Updates.text(1, 99, 100, "my secret text"))).isFalse();
			assertThat(logs.events(Level.WARN)).hasSize(1);
			assertThat(logs.allText()).contains("99").doesNotContain("my secret text");
		}
	}

	@Test
	void otherUsersCallbackIsRejectedWithoutLeakingData() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			assertThat(allowlist.isAllowed(Updates.callback(1, 99, 100, 5, "cb-77", "secret:data"))).isFalse();
			assertThat(logs.events(Level.WARN)).hasSize(1);
			assertThat(logs.allText()).contains("99").doesNotContain("secret:data").doesNotContain("cb-77");
		}
	}

	@Test
	void updateWithoutSenderIsRejectedWithoutWarning() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			assertThat(allowlist.isAllowed(new Update())).isFalse();
			assertThat(allowlist.isAllowed(Updates.withMessage(3,
					Updates.message(100, 42, 1).from(null).text("channel post").build()))).isFalse();
			assertThat(logs.events(Level.WARN)).isEmpty();
		}
	}
}
