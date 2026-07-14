ALTER TABLE email_verification_tokens
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;
