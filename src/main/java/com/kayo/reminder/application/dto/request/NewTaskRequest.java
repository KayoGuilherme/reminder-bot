package com.kayo.reminder.application.dto.request;

import com.kayo.reminder.domain.models.enums.Urgency;

public record NewTaskRequest(String title, String deadline, Urgency urgency, String startAt, String language) {
}
