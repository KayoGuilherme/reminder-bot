package com.kayo.reminder.application.dto.response;

import java.util.List;

public record PendingTasksResponse(List<TaskViewResponse> tasks, long total) {
}
