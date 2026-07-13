CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    role VARCHAR(50) NOT NULL,
    account_status VARCHAR(50) NOT NULL,
    email_verified TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE professional_profiles (
    id BIGINT NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    operational_status VARCHAR(50) NOT NULL,
    phone_number VARCHAR(30) NULL,
    bio TEXT NULL,
    workplace_name VARCHAR(150) NULL,
    city VARCHAR(100) NULL,
    instagram_url VARCHAR(500) NULL,
    website_url VARCHAR(500) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_professional_profiles PRIMARY KEY (id),
    CONSTRAINT fk_professional_profiles_user FOREIGN KEY (id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE client_profiles (
    id BIGINT NOT NULL,
    operational_status VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    height_cm DECIMAL(5,2) NOT NULL,
    primary_goal VARCHAR(150) NOT NULL,
    gender VARCHAR(30) NOT NULL,
    medical_notes TEXT NULL,
    injury_notes TEXT NULL,
    notes TEXT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_client_profiles PRIMARY KEY (id),
    CONSTRAINT fk_client_profiles_user FOREIGN KEY (id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE professional_client_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_professional_client_links PRIMARY KEY (id),
    INDEX idx_pcl_professional_id (professional_id),
    INDEX idx_pcl_client_id (client_id),
    INDEX idx_pcl_professional_client (professional_id, client_id),
    INDEX idx_pcl_professional_active (professional_id, active),
    INDEX idx_pcl_client_active (client_id, active),
    CONSTRAINT fk_pcl_professional FOREIGN KEY (professional_id)
        REFERENCES professional_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_client FOREIGN KEY (client_id)
        REFERENCES client_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invite_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    professional_id BIGINT NOT NULL,
    expires_at DATETIME(0) NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME(0) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_invite_codes PRIMARY KEY (id),
    CONSTRAINT uk_invite_codes_code UNIQUE (code),
    INDEX idx_invite_codes_professional_id (professional_id),
    INDEX idx_invite_codes_active (active),
    INDEX idx_invite_codes_expires_at (expires_at),
    INDEX idx_invite_codes_used (used),
    CONSTRAINT fk_invite_codes_professional FOREIGN KEY (professional_id)
        REFERENCES professional_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL,
    expires_at DATETIME(0) NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME(0) NULL,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens_token UNIQUE (token),
    INDEX idx_email_verification_tokens_user_id (user_id),
    INDEX idx_email_verification_tokens_expires_at (expires_at),
    INDEX idx_email_verification_tokens_used (used),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE availability_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    start_date_time DATETIME(0) NOT NULL,
    end_date_time DATETIME(0) NOT NULL,
    status VARCHAR(50) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_availability_slots PRIMARY KEY (id),
    INDEX idx_availability_slots_professional_id (professional_id),
    INDEX idx_availability_slots_professional_start (professional_id, start_date_time),
    INDEX idx_availability_slots_professional_active_start (professional_id, active, start_date_time),
    INDEX idx_availability_slots_status (status),
    CONSTRAINT chk_availability_slots_time_range CHECK (end_date_time > start_date_time),
    CONSTRAINT fk_availability_slots_professional FOREIGN KEY (professional_id)
        REFERENCES professional_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    note TEXT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_booking_requests PRIMARY KEY (id),
    INDEX idx_booking_requests_client_id (client_id),
    INDEX idx_booking_requests_professional_id (professional_id),
    INDEX idx_booking_requests_client_created (client_id, created_at),
    INDEX idx_booking_requests_professional_created (professional_id, created_at),
    INDEX idx_booking_requests_active_status (active, status),
    INDEX idx_booking_requests_status (status),
    CONSTRAINT fk_booking_requests_client FOREIGN KEY (client_id)
        REFERENCES client_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_requests_professional FOREIGN KEY (professional_id)
        REFERENCES professional_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_request_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_request_id BIGINT NOT NULL,
    availability_slot_id BIGINT NOT NULL,
    created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_booking_request_items PRIMARY KEY (id),
    CONSTRAINT uk_booking_request_items_request_slot UNIQUE (booking_request_id, availability_slot_id),
    INDEX idx_booking_request_items_request_id (booking_request_id),
    INDEX idx_booking_request_items_slot_id (availability_slot_id),
    CONSTRAINT fk_booking_request_items_request FOREIGN KEY (booking_request_id)
        REFERENCES booking_requests (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_request_items_slot FOREIGN KEY (availability_slot_id)
        REFERENCES availability_slots (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
