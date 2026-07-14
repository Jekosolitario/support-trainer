ALTER TABLE client_profiles
    MODIFY COLUMN created_at DATETIME(6) NULL,
    MODIFY COLUMN updated_at DATETIME(6) NULL;
