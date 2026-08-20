ALTER TABLE booking_requests
    ADD COLUMN rejection_reason VARCHAR(1000) NULL;

ALTER TABLE booking_requests
    ADD COLUMN cancellation_reason VARCHAR(1000) NULL;

ALTER TABLE booking_requests
    ADD COLUMN cancelled_by VARCHAR(32) NULL;
