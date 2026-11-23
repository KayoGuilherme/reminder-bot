package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.dto.response.ButtonResponse;
import java.util.List;
import lombok.Value;

@Value
public class SentButtons {

	long chatId;
	String text;
	List<List<ButtonResponse>> buttons;
}
