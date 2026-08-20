package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingHistoricalSnapshotMigrationTest {

    private static final String JDBC_URL = "jdbc:h2:mem:booking_historical_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeEach
    void setUpLegacyV59Schema() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, first_name VARCHAR(100) NOT NULL, "
                    + "last_name VARCHAR(100) NOT NULL)");
            statement.execute("CREATE TABLE client_profiles (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE professional_profiles (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE availability_slots (id BIGINT PRIMARY KEY, "
                    + "professional_id BIGINT NOT NULL, start_date_time DATETIME(6) NOT NULL, "
                    + "end_date_time DATETIME(6) NOT NULL, status VARCHAR(50) NOT NULL, active BOOLEAN NOT NULL)");
            statement.execute("CREATE TABLE booking_requests (id BIGINT PRIMARY KEY, client_id BIGINT NOT NULL, "
                    + "professional_id BIGINT NOT NULL, status VARCHAR(50) NOT NULL, updated_at DATETIME(6) NOT NULL)");
            statement.execute("CREATE TABLE booking_request_items (id BIGINT PRIMARY KEY, booking_request_id BIGINT NOT NULL, "
                    + "availability_slot_id BIGINT NOT NULL)");

            statement.execute("INSERT INTO users VALUES (1, '  Luigi  ', ' Bianchi '), (2, ' Mario ', ' Rossi ')");
            statement.execute("INSERT INTO client_profiles VALUES (1)");
            statement.execute("INSERT INTO professional_profiles VALUES (2)");
            statement.execute("INSERT INTO booking_requests VALUES "
                    + "(10, 1, 2, 'PENDING', '2026-07-13 15:30:45.123456'), "
                    + "(11, 1, 2, 'CONFIRMED', '2026-07-14 15:30:45.123456'), "
                    + "(12, 1, 2, 'REJECTED', '2026-07-15 15:30:45.123456'), "
                    + "(13, 1, 2, 'CANCELLED', '2026-07-16 15:30:45.123456')");
            statement.execute("INSERT INTO availability_slots VALUES "
                    + "(100, 2, '2026-08-01 09:00:00.123456', '2026-08-01 10:00:00.123456', "
                    + "'AVAILABLE', TRUE), "
                    + "(101, 2, '2026-08-02 09:00:00.123456', '2026-08-02 10:00:00.123456', "
                    + "'BOOKED', TRUE), "
                    + "(102, 2, '2026-08-03 09:00:00.123456', '2026-08-03 10:00:00.123456', "
                    + "'AVAILABLE', TRUE), "
                    + "(103, 2, '2026-08-04 09:00:00.123456', '2026-08-04 10:00:00.123456', "
                    + "'BLOCKED', TRUE)");
            statement.execute("INSERT INTO booking_request_items VALUES "
                    + "(1000, 10, 100), (1001, 11, 101), (1002, 12, 102), (1003, 13, 103)");
        }
    }

    @Test
    void shouldBackfillSnapshotsAndLegacyTimelineFromV59AndRemainIdempotent() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("5.9")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_NAME) IN ('SPRING_SESSION', 'SPRING_SESSION_ATTRIBUTES')"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_NAME) IN ('WEEKLY_AVAILABILITY_RULES', "
                            + "'WEEKLY_AVAILABILITY_RULE_DURATIONS', 'AVAILABILITY_RULE_CHANGES', "
                            + "'AVAILABILITY_SLOT_CHANGES')"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(4);
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT client_display_name, professional_display_name, confirmed_at, rejected_at, cancelled_at "
                            + "FROM booking_requests WHERE id = 13"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("client_display_name")).isEqualTo("Luigi Bianchi");
                assertThat(resultSet.getString("professional_display_name")).isEqualTo("Mario Rossi");
                assertThat(resultSet.getObject("confirmed_at", LocalDateTime.class)).isNull();
                assertThat(resultSet.getObject("rejected_at", LocalDateTime.class)).isNull();
                assertThat(resultSet.getObject("cancelled_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-07-16T15:30:45.123456"));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT rejection_reason, cancellation_reason, cancelled_by "
                            + "FROM booking_requests WHERE id = 13"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("rejection_reason")).isNull();
                assertThat(resultSet.getString("cancellation_reason")).isNull();
                assertThat(resultSet.getString("cancelled_by")).isNull();
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT confirmed_at, rejected_at, cancelled_at FROM booking_requests WHERE id = 11"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject("confirmed_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-07-14T15:30:45.123456"));
                assertThat(resultSet.getObject("rejected_at", LocalDateTime.class)).isNull();
                assertThat(resultSet.getObject("cancelled_at", LocalDateTime.class)).isNull();
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT scheduled_start, scheduled_end FROM booking_request_items WHERE id = 1000"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject("scheduled_start", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-08-01T09:00:00.123456"));
                assertThat(resultSet.getObject("scheduled_end", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-08-01T10:00:00.123456"));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT location_label_snapshot FROM booking_request_items WHERE id = 1000"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("location_label_snapshot")).isNull();
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id, weekly_rule_id, capacity, blocked "
                            + "FROM availability_slots WHERE id IN (101, 103) ORDER BY id"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("id")).isEqualTo(101);
                assertThat(resultSet.getObject("weekly_rule_id")).isNull();
                assertThat(resultSet.getInt("capacity")).isEqualTo(1);
                assertThat(resultSet.getBoolean("blocked")).isFalse();
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("id")).isEqualTo(103);
                assertThat(resultSet.getObject("weekly_rule_id")).isNull();
                assertThat(resultSet.getInt("capacity")).isEqualTo(1);
                assertThat(resultSet.getBoolean("blocked")).isTrue();
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE UPPER(TABLE_NAME) = 'BOOKING_REQUESTS' "
                            + "AND UPPER(COLUMN_NAME) = 'CLIENT_DISPLAY_NAME'"
            )) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("IS_NULLABLE")).isEqualTo("NO");
            }
        }
    }
}
