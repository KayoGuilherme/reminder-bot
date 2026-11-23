package com.kayo.reminder.transport.llm;

import com.kayo.reminder.application.dto.response.ParsedTaskResponse;

public interface TaskParser {

	ParsedTaskResponse parse(String text);
}
