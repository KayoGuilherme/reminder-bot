package com.kayo.reminder.transport.llm;

import java.util.List;

public record ChatCompletion(List<ChatChoice> choices) {
}
