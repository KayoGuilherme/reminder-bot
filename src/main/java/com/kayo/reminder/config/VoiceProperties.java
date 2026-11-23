package com.kayo.reminder.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class VoiceProperties {

	@Min(1)
	private final int maxSeconds;

	public VoiceProperties(@DefaultValue("60") int maxSeconds) {
		this.maxSeconds = maxSeconds;
	}
}
