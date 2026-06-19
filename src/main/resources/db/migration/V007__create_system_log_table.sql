CREATE TABLE IF NOT EXISTS system_log
(
    id            VARCHAR(255) PRIMARY KEY,
    username      VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    action        VARCHAR(100),
    module        VARCHAR(100),
    resource      VARCHAR(150),
    resource_id   VARCHAR(255),
    http_method   VARCHAR(20),
    request_path  VARCHAR(500),
    status_code   INTEGER,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500),
    request_body  TEXT,
    response_body TEXT,
    message       TEXT,
    error_message TEXT,
    logged_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_system_log_account FOREIGN KEY (username) REFERENCES account (username) ON DELETE SET NULL,
    CONSTRAINT uk_system_log_id UNIQUE (id)
);

CREATE INDEX IF NOT EXISTS idx_system_log_username ON system_log(username);
CREATE INDEX IF NOT EXISTS idx_system_log_action ON system_log(action);
CREATE INDEX IF NOT EXISTS idx_system_log_module ON system_log(module);
CREATE INDEX IF NOT EXISTS idx_system_log_logged_at ON system_log(logged_at);
CREATE INDEX IF NOT EXISTS idx_system_log_status_code ON system_log(status_code);
