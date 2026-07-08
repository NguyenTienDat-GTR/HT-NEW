ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS registration_start_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS registration_end_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS training_notification (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    sender_unit_type VARCHAR(50),
    sender_unit_id UUID,
    course_code VARCHAR(255),
    total_count INTEGER,
    approved_count INTEGER,
    pending_count INTEGER,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    status BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_training_notification_account
        FOREIGN KEY (username) REFERENCES account (username),
    CONSTRAINT fk_training_notification_course
        FOREIGN KEY (course_code) REFERENCES training_course (course_code)
);

CREATE INDEX IF NOT EXISTS idx_training_notification_username_created_at
    ON training_notification (username, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_training_notification_username_read_at
    ON training_notification (username, read_at);
