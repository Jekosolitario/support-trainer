CREATE TABLE weekly_availability_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    day_of_week VARCHAR(16) NOT NULL,
    start_time TIME(0) NOT NULL,
    end_time TIME(0) NOT NULL,
    location_label VARCHAR(255) NULL,
    capacity_per_slot INT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    valid_from DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_weekly_availability_rules PRIMARY KEY (id),
    INDEX idx_weekly_rules_professional_active_day
        (professional_id, active, day_of_week, valid_from),
    CONSTRAINT chk_weekly_rules_time_range CHECK (end_time > start_time),
    CONSTRAINT chk_weekly_rules_capacity CHECK (capacity_per_slot >= 1),
    CONSTRAINT fk_weekly_rules_professional FOREIGN KEY (professional_id)
        REFERENCES professional_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE weekly_availability_rule_durations (
    weekly_rule_id BIGINT NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT pk_weekly_availability_rule_durations
        PRIMARY KEY (weekly_rule_id, duration_minutes),
    CONSTRAINT chk_weekly_rule_duration_range
        CHECK (duration_minutes >= 15 AND duration_minutes <= 180),
    CONSTRAINT chk_weekly_rule_duration_interval
        CHECK (MOD(duration_minutes, 15) = 0),
    CONSTRAINT fk_weekly_rule_durations_rule FOREIGN KEY (weekly_rule_id)
        REFERENCES weekly_availability_rules (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE availability_rule_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    weekly_rule_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_reason VARCHAR(1000) NULL,
    impacted_booking_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_availability_rule_changes PRIMARY KEY (id),
    INDEX idx_availability_rule_changes_rule_created (weekly_rule_id, created_at),
    CONSTRAINT chk_availability_rule_changes_impact CHECK (impacted_booking_count >= 0),
    CONSTRAINT fk_availability_rule_changes_rule FOREIGN KEY (weekly_rule_id)
        REFERENCES weekly_availability_rules (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE availability_slots
    ADD COLUMN weekly_rule_id BIGINT NULL;

ALTER TABLE availability_slots
    ADD COLUMN location_label VARCHAR(255) NULL;

ALTER TABLE availability_slots
    ADD COLUMN capacity INT NOT NULL DEFAULT 1;

ALTER TABLE availability_slots
    ADD COLUMN blocked TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE availability_slots
    ADD CONSTRAINT chk_availability_slots_capacity CHECK (capacity >= 1);

ALTER TABLE availability_slots
    ADD CONSTRAINT fk_availability_slots_weekly_rule FOREIGN KEY (weekly_rule_id)
        REFERENCES weekly_availability_rules (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE UNIQUE INDEX uk_availability_slots_rule_start
    ON availability_slots (weekly_rule_id, start_date_time);

CREATE INDEX idx_availability_slots_client_bookable
    ON availability_slots (professional_id, active, blocked, start_date_time);

CREATE TABLE availability_slot_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    availability_slot_id BIGINT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_reason VARCHAR(1000) NULL,
    impacted_booking_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_availability_slot_changes PRIMARY KEY (id),
    INDEX idx_availability_slot_changes_slot_created (availability_slot_id, created_at),
    CONSTRAINT chk_availability_slot_changes_impact CHECK (impacted_booking_count >= 0),
    CONSTRAINT fk_availability_slot_changes_slot FOREIGN KEY (availability_slot_id)
        REFERENCES availability_slots (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE booking_request_items
    ADD COLUMN location_label_snapshot VARCHAR(255) NULL;

CREATE INDEX idx_booking_request_items_slot_schedule
    ON booking_request_items (availability_slot_id, scheduled_start, scheduled_end);

UPDATE availability_slots
SET blocked = CASE WHEN status = 'BLOCKED' THEN 1 ELSE 0 END,
    capacity = 1;
