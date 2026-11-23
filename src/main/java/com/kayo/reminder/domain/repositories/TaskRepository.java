package com.kayo.reminder.domain.repositories;

import com.kayo.reminder.domain.models.TaskEntity;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

	@Query("""
			select t from TaskEntity t
			where t.status = com.kayo.reminder.domain.models.enums.TaskStatus.PENDING and t.deadlineAt <= :cutoff
			  and ((t.urgency = com.kayo.reminder.domain.models.enums.Urgency.LOW and t.lastReminderStage < :lowFinal)
			    or (t.urgency = com.kayo.reminder.domain.models.enums.Urgency.MEDIUM and t.lastReminderStage < :mediumFinal)
			    or (t.urgency = com.kayo.reminder.domain.models.enums.Urgency.HIGH and t.lastReminderStage < :highFinal))
			order by t.deadlineAt, t.id
			""")
	List<TaskEntity> findReminderCandidates(@Param("cutoff") Instant cutoff, @Param("lowFinal") short lowFinal,
			@Param("mediumFinal") short mediumFinal, @Param("highFinal") short highFinal);

	List<TaskEntity> findByChatIdAndStatusOrderByDeadlineAtAscIdAsc(long chatId, TaskStatus status, Pageable page);

	long countByChatIdAndStatus(long chatId, TaskStatus status);

	@Modifying
	@Query("""
			update TaskEntity t
			set t.lastReminderStage = :stage, t.lastRemindedAt = :at, t.updatedAt = :at
			where t.id = :id and t.status = com.kayo.reminder.domain.models.enums.TaskStatus.PENDING
			  and t.lastReminderStage < :stage and t.deadlineAt = :deadline
			""")
	int markReminded(@Param("id") UUID id, @Param("stage") short stage, @Param("at") Instant at,
			@Param("deadline") Instant deadline);

	@Modifying
	@Query("""
			update TaskEntity t
			set t.status = :status, t.updatedAt = :at
			where t.id = :id and t.status = com.kayo.reminder.domain.models.enums.TaskStatus.PENDING
			""")
	int finishPending(@Param("id") UUID id, @Param("status") TaskStatus status, @Param("at") Instant at);

	@Modifying
	@Query("""
			update TaskEntity t
			set t.deadlineAt = :newDeadline, t.lastReminderStage = 0, t.updatedAt = :at
			where t.id = :id and t.status = com.kayo.reminder.domain.models.enums.TaskStatus.PENDING
			  and t.deadlineAt = :expected and t.lastReminderStage > 0
			""")
	int snooze(@Param("id") UUID id, @Param("expected") Instant expected, @Param("newDeadline") Instant newDeadline,
			@Param("at") Instant at);

	@Modifying
	@Query("""
			delete from TaskEntity t
			where t.status in (com.kayo.reminder.domain.models.enums.TaskStatus.DONE, com.kayo.reminder.domain.models.enums.TaskStatus.CANCELED)
			  and t.updatedAt < :cutoff
			""")
	int deleteFinishedBefore(@Param("cutoff") Instant cutoff);
}
