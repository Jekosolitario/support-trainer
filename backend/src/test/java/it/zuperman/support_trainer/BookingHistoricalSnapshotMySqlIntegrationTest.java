package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@EnabledIfSystemProperty(named = "it.mysql.enabled", matches = "true")
class BookingHistoricalSnapshotMySqlIntegrationTest {

    @Test
    void shouldMigrateEmptyAndLegacyMySqlSchemasAndPassHibernateValidation() throws Exception {
        String password = requireEnvironment("MYSQL_PWD");
        String emptySchema = requireProperty("it.mysql.empty-schema");
        String legacySchema = requireProperty("it.mysql.legacy-schema");

        migrateToLatest(emptySchema, password);
        assertSnapshotColumns(emptySchema, password);
        validateWithHibernate(emptySchema, password);

        migrateToV59(legacySchema, password);
        seedLegacyBooking(legacySchema, password);
        Flyway latest = flyway(legacySchema, password, null);
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertLegacySnapshot(legacySchema, password);
        assertSnapshotColumns(legacySchema, password);
        validateWithHibernate(legacySchema, password);
    }

    private static void migrateToLatest(String schema, String password) {
        assertThat(flyway(schema, password, null).migrate().migrationsExecuted).isGreaterThan(0);
    }

    private static void migrateToV59(String schema, String password) {
        assertThat(flyway(schema, password, "5.9").migrate().migrationsExecuted).isGreaterThan(0);
    }

    private static Flyway flyway(String schema, String password, String target) {
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl(schema), "root", password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void seedLegacyBooking(String schema, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), "root", password);
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (id, first_name, last_name, email, password, role, account_status, "
                    + "email_verified, created_at, updated_at) VALUES "
                    + "(1, 'Luigi', 'Bianchi', 'legacy-client@example.com', 'encoded', 'CLIENT', 'ACTIVE', 1, "
                    + "'2026-07-01 08:00:00.123456', '2026-07-01 08:00:00.123456'), "
                    + "(2, 'Mario', 'Rossi', 'legacy-professional@example.com', 'encoded', 'PROFESSIONAL', 'ACTIVE', 1, "
                    + "'2026-07-01 08:00:00.123456', '2026-07-01 08:00:00.123456')");
            statement.execute("INSERT INTO client_profiles (id, operational_status, birth_date, height_cm, primary_goal, "
                    + "gender, active) VALUES (1, 'ATTIVO', '1990-01-01', 180.00, 'Allenamento', 'MALE', 1)");
            statement.execute("INSERT INTO professional_profiles (id, specialization, operational_status, active) "
                    + "VALUES (2, 'PERSONAL_TRAINER', 'DISPONIBILE', 1)");
            statement.execute("INSERT INTO availability_slots (id, professional_id, start_date_time, end_date_time, status, "
                    + "active, created_at, updated_at) VALUES (10, 2, '2026-08-01 09:00:00.123456', "
                    + "'2026-08-01 10:00:00.123456', 'AVAILABLE', 1, '2026-07-01 08:00:00.123456', "
                    + "'2026-07-01 08:00:00.123456')");
            statement.execute("INSERT INTO booking_requests (id, client_id, professional_id, status, note, active, created_at, "
                    + "updated_at) VALUES (20, 1, 2, 'CANCELLED', 'legacy', 1, '2026-07-02 08:00:00.123456', "
                    + "'2026-07-03 08:00:00.123456')");
            statement.execute("INSERT INTO booking_request_items (id, booking_request_id, availability_slot_id, created_at, "
                    + "updated_at) VALUES (30, 20, 10, '2026-07-02 08:00:00.123456', '2026-07-02 08:00:00.123456')");
        }
    }

    private static void assertLegacySnapshot(String schema, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), "root", password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT client_display_name, professional_display_name, confirmed_at, rejected_at, cancelled_at, "
                                + "rejection_reason, cancellation_reason, cancelled_by "
                                + "FROM booking_requests WHERE id = 20"
                )) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("client_display_name")).isEqualTo("Luigi Bianchi");
            assertThat(resultSet.getString("professional_display_name")).isEqualTo("Mario Rossi");
            assertThat(resultSet.getObject("confirmed_at", LocalDateTime.class)).isNull();
            assertThat(resultSet.getObject("rejected_at", LocalDateTime.class)).isNull();
            assertThat(resultSet.getObject("cancelled_at", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.parse("2026-07-03T08:00:00.123456"));
            assertThat(resultSet.getString("rejection_reason")).isNull();
            assertThat(resultSet.getString("cancellation_reason")).isNull();
            assertThat(resultSet.getString("cancelled_by")).isNull();
        }
    }

    private static void assertSnapshotColumns(String schema, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), "root", password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT table_name, column_name, data_type, datetime_precision, is_nullable "
                                + "FROM information_schema.columns WHERE table_schema = '" + schema + "' "
                                + "AND column_name IN ('client_display_name', 'professional_display_name', 'scheduled_start', "
                                + "'scheduled_end', 'confirmed_at', 'rejected_at', 'cancelled_at', "
                                + "'rejection_reason', 'cancellation_reason', 'cancelled_by')"
                )) {
            Map<String, String> definitions = new java.util.HashMap<>();
            while (resultSet.next()) {
                definitions.put(
                        resultSet.getString("table_name") + '.' + resultSet.getString("column_name"),
                        resultSet.getString("data_type") + ':' + resultSet.getObject("datetime_precision")
                                + ':' + resultSet.getString("is_nullable")
                );
            }
            assertThat(definitions)
                    .containsEntry("booking_requests.client_display_name", "varchar:null:NO")
                    .containsEntry("booking_requests.professional_display_name", "varchar:null:NO")
                    .containsEntry("booking_request_items.scheduled_start", "datetime:6:NO")
                    .containsEntry("booking_request_items.scheduled_end", "datetime:6:NO")
                    .containsEntry("booking_requests.confirmed_at", "datetime:6:YES")
                    .containsEntry("booking_requests.rejected_at", "datetime:6:YES")
                    .containsEntry("booking_requests.cancelled_at", "datetime:6:YES")
                    .containsEntry("booking_requests.rejection_reason", "varchar:null:YES")
                    .containsEntry("booking_requests.cancellation_reason", "varchar:null:YES")
                    .containsEntry("booking_requests.cancelled_by", "varchar:null:YES");
        }
    }

    private static void validateWithHibernate(String schema, String password) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(SupportTrainerApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + jdbcUrl(schema),
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=" + password,
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.open-in-view=false",
                        "--app.email.mode=DISABLED",
                        "--app.email.verification-page-url=http://localhost:5173/verify-email"
                )) {
            assertThat(ignored.isRunning()).isTrue();
        }
    }

    private static String jdbcUrl(String schema) {
        return "jdbc:mysql://localhost:3306/" + schema
                + "?connectionTimeZone=%2B00&forceConnectionTimeZoneToSession=true";
    }

    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when MySQL integration tests are enabled");
        }
        return value;
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when MySQL integration tests are enabled");
        }
        return value;
    }
}
