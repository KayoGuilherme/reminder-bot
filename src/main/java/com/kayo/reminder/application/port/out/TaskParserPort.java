package com.kayo.reminder.application.port.out;

public interface TaskParserPort {

	/**
	 * Extracts a task from free text.
	 *
	 * @throws ExternalServiceException if the service fails or its answer is not a usable task
	 */
	ParsedTask parse(String text);
}
