package com.kayo.reminder.domain.models;

import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class TaskEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "chat_id", nullable = false)
	private long chatId;

	@Column(nullable = false, length = 500)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Urgency urgency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TaskStatus status;

	@Column(name = "start_at")
	private Instant startAt;

	@Column(name = "deadline_at", nullable = false)
	private Instant deadlineAt;

	@Column(name = "last_reminder_stage", nullable = false)
	private short lastReminderStage;

	@Column(name = "last_reminded_at")
	private Instant lastRemindedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean fresh;

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void markStored() {
		this.fresh = false;
	}
}
