ALTER TABLE client_profiles
    MODIFY COLUMN primary_goal VARCHAR(255) NOT NULL;

UPDATE booking_request_items
SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP(6))
WHERE updated_at IS NULL;

ALTER TABLE booking_request_items
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE invite_codes
    ADD INDEX idx_invite_codes_professional_created (professional_id, created_at);

ALTER TABLE availability_slots
    ADD INDEX idx_availability_slots_professional_active_status_start
        (professional_id, active, status, start_date_time);

ALTER TABLE booking_requests
    ADD INDEX idx_booking_requests_client_active_created
        (client_id, active, created_at),
    ADD INDEX idx_booking_requests_professional_active_created
        (professional_id, active, created_at);
