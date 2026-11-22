CREATE TABLE tasks (
    id                  UUID         PRIMARY KEY,
    chat_id             BIGINT       NOT NULL,
    title               VARCHAR(500) NOT NULL,
    urgency             VARCHAR(10)  NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    start_at            TIMESTAMPTZ,
    deadline_at         TIMESTAMPTZ  NOT NULL,
    last_reminder_stage SMALLINT     NOT NULL DEFAULT 0,
    last_reminded_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tasks_urgency_check CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT tasks_status_check CHECK (status IN ('PENDING', 'DONE', 'CANCELED'))
);

CREATE INDEX idx_tasks_status_deadline ON tasks (status, deadline_at);
