ALTER TABLE tasks ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en';

ALTER TABLE tasks ADD CONSTRAINT tasks_language_check CHECK (language IN ('en', 'pt-BR'));
