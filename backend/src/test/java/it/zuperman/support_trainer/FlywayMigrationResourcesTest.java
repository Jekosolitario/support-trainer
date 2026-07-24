package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import db.migration.V4__convert_runtime_datetimes_from_rome_to_utc;
import db.migration.V6__add_booking_historical_snapshots;

class FlywayMigrationResourcesTest {

    private static final List<String> MIGRATIONS = List.of(
            "db/migration/V1__create_legacy_compatible_runtime_schema.sql",
            "db/migration/V2__align_runtime_schema_contract.sql",
            "db/migration/V3_1__expand_users_timestamps_to_microseconds.sql",
            "db/migration/V3_2__expand_professional_profiles_timestamps_to_microseconds.sql",
            "db/migration/V3_3__expand_client_profiles_timestamps_to_microseconds.sql",
            "db/migration/V3_4__expand_professional_client_links_timestamps_to_microseconds.sql",
            "db/migration/V3_5__expand_invite_codes_timestamps_to_microseconds.sql",
            "db/migration/V3_6__expand_email_verification_tokens_timestamps_to_microseconds.sql",
            "db/migration/V3_7__expand_availability_slots_timestamps_to_microseconds.sql",
            "db/migration/V3_8__expand_booking_requests_timestamps_to_microseconds.sql",
            "db/migration/V3_9__expand_booking_request_items_timestamps_to_microseconds.sql",
            "db/migration/V5_1__transfer_users_audit_ownership_to_application.sql",
            "db/migration/V5_2__freeze_professional_profile_shadow_timestamps.sql",
            "db/migration/V5_3__freeze_client_profile_shadow_timestamps.sql",
            "db/migration/V5_4__transfer_link_audit_ownership_to_application.sql",
            "db/migration/V5_5__transfer_invite_audit_ownership_to_application.sql",
            "db/migration/V5_6__transfer_email_token_audit_ownership_to_application.sql",
            "db/migration/V5_7__transfer_availability_audit_ownership_to_application.sql",
            "db/migration/V5_8__transfer_booking_request_audit_ownership_to_application.sql",
            "db/migration/V5_9__transfer_booking_item_audit_ownership_to_application.sql",
            "db/migration/V7__create_spring_session_jdbc_schema.sql"
    );

    private static final List<V5MigrationContract> V5_MIGRATIONS = List.of(
            new V5MigrationContract(MIGRATIONS.get(11), "users", false),
            new V5MigrationContract(MIGRATIONS.get(12), "professional_profiles", true),
            new V5MigrationContract(MIGRATIONS.get(13), "client_profiles", true),
            new V5MigrationContract(MIGRATIONS.get(14), "professional_client_links", false),
            new V5MigrationContract(MIGRATIONS.get(15), "invite_codes", false),
            new V5MigrationContract(MIGRATIONS.get(16), "email_verification_tokens", false),
            new V5MigrationContract(MIGRATIONS.get(17), "availability_slots", false),
            new V5MigrationContract(MIGRATIONS.get(18), "booking_requests", false),
            new V5MigrationContract(MIGRATIONS.get(19), "booking_request_items", false)
    );

    private static final List<V3MigrationContract> V3_MIGRATIONS = List.of(
            new V3MigrationContract(MIGRATIONS.get(2), "users", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(3), "professional_profiles", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(4), "client_profiles", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(5), "professional_client_links", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(6), "invite_codes", List.of(
                    "MODIFY COLUMN expires_at DATETIME(6) NOT NULL",
                    "MODIFY COLUMN used_at DATETIME(6) NULL",
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(7), "email_verification_tokens", List.of(
                    "MODIFY COLUMN expires_at DATETIME(6) NOT NULL",
                    "MODIFY COLUMN used_at DATETIME(6) NULL",
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(8), "availability_slots", List.of(
                    "MODIFY COLUMN start_date_time DATETIME(6) NOT NULL",
                    "MODIFY COLUMN end_date_time DATETIME(6) NOT NULL",
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(9), "booking_requests", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "MODIFY COLUMN updated_at DATETIME(6) NOT NULL "
                            + "DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
            )),
            new V3MigrationContract(MIGRATIONS.get(10), "booking_request_items", List.of(
                    "MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)"
            ))
    );

    private static final Set<String> RUNTIME_TABLES = Set.of(
            "users",
            "professional_profiles",
            "client_profiles",
            "professional_client_links",
            "invite_codes",
            "email_verification_tokens",
            "availability_slots",
            "booking_requests",
            "booking_request_items"
    );

    private static final Set<String> LEGACY_FUTURE_TABLES = Set.of(
            "client_measurements",
            "nutrition_days",
            "nutrition_entries",
            "nutrition_feedbacks",
            "nutrition_plans",
            "nutrition_weeks",
            "password_reset_tokens",
            "refresh_tokens",
            "workout_days",
            "workout_exercises",
            "workout_feedbacks",
            "workout_plans",
            "workout_weeks"
    );

    private static final Set<String> SPRING_SESSION_TABLES = Set.of(
            "spring_session",
            "spring_session_attributes"
    );

    private static final Pattern VALID_MIGRATION_NAME = Pattern.compile(
            "^V[1-9][0-9]*(?:_[0-9]+)*__[a-z0-9_]+\\.sql$"
    );

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+([a-z0-9_]+)"
    );

    private static final Pattern DESTRUCTIVE_STATEMENT = Pattern.compile(
            "(?im)^\\s*(DROP|TRUNCATE|DELETE)\\b"
    );

    private static final Pattern ALTER_TABLE = Pattern.compile(
            "(?i)\\bALTER\\s+TABLE\\s+([a-z0-9_]+)"
    );

    private static final Pattern FORBIDDEN_V3_STATEMENT = Pattern.compile(
            "(?im)^\\s*(CREATE|DROP|INSERT|UPDATE|DELETE|TRUNCATE)\\b"
    );

    @Test
    void shouldExposeVersionedMigrationsInExpectedOrderWithValidNames() {
        assertThat(MIGRATIONS)
                .extracting(path -> path.substring(path.lastIndexOf('/') + 1))
                .containsExactly(
                        "V1__create_legacy_compatible_runtime_schema.sql",
                        "V2__align_runtime_schema_contract.sql",
                        "V3_1__expand_users_timestamps_to_microseconds.sql",
                        "V3_2__expand_professional_profiles_timestamps_to_microseconds.sql",
                        "V3_3__expand_client_profiles_timestamps_to_microseconds.sql",
                        "V3_4__expand_professional_client_links_timestamps_to_microseconds.sql",
                        "V3_5__expand_invite_codes_timestamps_to_microseconds.sql",
                        "V3_6__expand_email_verification_tokens_timestamps_to_microseconds.sql",
                        "V3_7__expand_availability_slots_timestamps_to_microseconds.sql",
                        "V3_8__expand_booking_requests_timestamps_to_microseconds.sql",
                        "V3_9__expand_booking_request_items_timestamps_to_microseconds.sql",
                        "V5_1__transfer_users_audit_ownership_to_application.sql",
                        "V5_2__freeze_professional_profile_shadow_timestamps.sql",
                        "V5_3__freeze_client_profile_shadow_timestamps.sql",
                        "V5_4__transfer_link_audit_ownership_to_application.sql",
                        "V5_5__transfer_invite_audit_ownership_to_application.sql",
                        "V5_6__transfer_email_token_audit_ownership_to_application.sql",
                        "V5_7__transfer_availability_audit_ownership_to_application.sql",
                        "V5_8__transfer_booking_request_audit_ownership_to_application.sql",
                        "V5_9__transfer_booking_item_audit_ownership_to_application.sql",
                        "V7__create_spring_session_jdbc_schema.sql"
                )
                .allMatch(name -> VALID_MIGRATION_NAME.matcher(name).matches());

        assertThat(MIGRATIONS)
                .allSatisfy(path -> assertThat(new ClassPathResource(path).exists()).isTrue());
    }

    @Test
    void migrationDirectoryShouldContainOnlyApprovedSqlMigrationsThroughV7() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");

        assertThat(resources)
                .extracting(Resource::getFilename)
                .containsExactlyInAnyOrderElementsOf(
                        MIGRATIONS.stream()
                                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                                .toList()
                );
    }

    @Test
    void v4JavaMigrationShouldHaveExplicitStableChecksumAndNoSchemaChanges() throws IOException {
        V4__convert_runtime_datetimes_from_rome_to_utc first
                = new V4__convert_runtime_datetimes_from_rome_to_utc();
        V4__convert_runtime_datetimes_from_rome_to_utc second
                = new V4__convert_runtime_datetimes_from_rome_to_utc();
        String source = Files.readString(
                Path.of("src/main/java/db/migration/V4__convert_runtime_datetimes_from_rome_to_utc.java"),
                StandardCharsets.UTF_8
        );

        assertThat(first.getVersion().toString()).isEqualTo("4");
        assertThat(first.getChecksum())
                .isNotNull()
                .isEqualTo(second.getChecksum())
                .isNotEqualTo(-1886151667);
        assertThat(first.canExecuteInTransaction()).isTrue();
        assertThat(source)
                .contains("getValidOffsets")
                .contains("information_schema.COLUMNS")
                .contains("DATETIME_PRECISION")
                .contains("IS_NULLABLE")
                .contains("SELECT DATABASE()")
                .doesNotContain("DECIMAL_DIGITS")
                .doesNotContain("DatabaseMetaData")
                .doesNotContainIgnoringCase("CONVERT_TZ")
                .doesNotContainPattern("(?i)\\b(CREATE|ALTER|DROP|TRUNCATE)\\s+TABLE\\b")
                .doesNotContain(".commit(");
    }

    @Test
    void v6JavaMigrationShouldHaveExplicitStableChecksumAndHistoricalSnapshotContract() throws IOException {
        V6__add_booking_historical_snapshots first = new V6__add_booking_historical_snapshots();
        V6__add_booking_historical_snapshots second = new V6__add_booking_historical_snapshots();
        String source = Files.readString(
                Path.of("src/main/java/db/migration/V6__add_booking_historical_snapshots.java"),
                StandardCharsets.UTF_8
        );

        assertThat(first.getVersion().toString()).isEqualTo("6");
        assertThat(first.getChecksum()).isNotNull().isEqualTo(second.getChecksum());
        assertThat(first.canExecuteInTransaction()).isFalse();
        assertThat(source)
                .contains("client_display_name")
                .contains("professional_display_name")
                .contains("scheduled_start")
                .contains("scheduled_end")
                .contains("DATETIME(6)")
                .contains("CONFIRMED")
                .contains("REJECTED")
                .contains("CANCELLED")
                .contains("has no items to snapshot")
                .doesNotContain("Sconosciuto")
                .doesNotContain("N/D")
                .doesNotContain("Instant.now")
                .doesNotContain("LocalDateTime.now")
                .doesNotContain(".commit(");
    }

    @Test
    void v1ShouldCreateOnlyTheApprovedRuntimeTables() throws IOException {
        Matcher matcher = CREATE_TABLE.matcher(readResource(MIGRATIONS.getFirst()));
        Set<String> createdTables = new LinkedHashSet<>();
        while (matcher.find()) {
            createdTables.add(matcher.group(1).toLowerCase());
        }

        assertThat(createdTables).containsExactlyInAnyOrderElementsOf(RUNTIME_TABLES);
    }

    @Test
    void migrationsShouldNotReferenceLegacyFutureTables() throws IOException {
        for (String migration : MIGRATIONS) {
            String sql = readResource(migration).toLowerCase();
            assertThat(LEGACY_FUTURE_TABLES)
                    .allSatisfy(table -> assertThat(sql).doesNotContain(table));
        }
    }

    @Test
    void migrationsShouldNotContainDestructiveStatements() throws IOException {
        for (String migration : MIGRATIONS) {
            String sql = readResource(migration);
            assertThat(DESTRUCTIVE_STATEMENT.matcher(sql).find()).isFalse();
            assertThat(sql).doesNotContainIgnoringCase("CREATE TABLE IF NOT EXISTS");
        }
    }

    @Test
    void v3MigrationsShouldBeStructuralAndModifyOnlyTheirExpectedTable() throws IOException {
        for (V3MigrationContract contract : V3_MIGRATIONS) {
            String sql = readResource(contract.path());
            Matcher matcher = ALTER_TABLE.matcher(sql);
            Set<String> alteredTables = new LinkedHashSet<>();
            while (matcher.find()) {
                alteredTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }

            assertThat(alteredTables).containsExactly(contract.table());
            assertThat(FORBIDDEN_V3_STATEMENT.matcher(sql).find()).isFalse();
            assertThat(sql)
                    .doesNotContainIgnoringCase("CONVERT_TZ")
                    .doesNotContainIgnoringCase("IF EXISTS")
                    .doesNotContainIgnoringCase("IF NOT EXISTS")
                    .doesNotContainIgnoringCase("DATETIME(0)");
        }
    }

    @Test
    void v3MigrationsShouldPreserveExpectedPrecisionNullabilityAndDefaults() throws IOException {
        for (V3MigrationContract contract : V3_MIGRATIONS) {
            String expectedSql = "ALTER TABLE " + contract.table() + " "
                    + String.join(", ", contract.columnDefinitions()) + ";";

            assertThat(normalizeSql(readResource(contract.path())))
                    .isEqualTo(normalizeSql(expectedSql));
        }
    }

    @Test
    void v3MigrationsShouldLeaveCivilDatesAndExistingBookingItemMicrosecondsUntouched() throws IOException {
        for (V3MigrationContract contract : V3_MIGRATIONS) {
            assertThat(readResource(contract.path())).doesNotContainIgnoringCase("birth_date");
        }

        assertThat(readResource(V3_MIGRATIONS.getLast().path()))
                .doesNotContainIgnoringCase("updated_at")
                .doesNotContainIgnoringCase("DATETIME(0)");
    }

    @Test
    void v5MigrationsShouldOnlyTransferAuditOwnershipOnRuntimeTables() throws IOException {
        Pattern forbiddenDml = Pattern.compile("(?im)^\\s*(INSERT|UPDATE|DELETE)\\b");

        for (V5MigrationContract contract : V5_MIGRATIONS) {
            String sql = readResource(contract.path());
            Matcher matcher = ALTER_TABLE.matcher(sql);
            Set<String> alteredTables = new LinkedHashSet<>();
            while (matcher.find()) {
                alteredTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }

            assertThat(alteredTables).containsExactly(contract.table());
            assertThat(sql)
                    .containsIgnoringCase("DATETIME(6)")
                    .doesNotContainIgnoringCase("DEFAULT CURRENT_TIMESTAMP")
                    .doesNotContainIgnoringCase("ON UPDATE CURRENT_TIMESTAMP")
                    .doesNotContainIgnoringCase("CONVERT_TZ");
            assertThat(forbiddenDml.matcher(sql).find()).isFalse();
            assertThat(LEGACY_FUTURE_TABLES)
                    .allSatisfy(table -> assertThat(sql).doesNotContainIgnoringCase(table));

            if (contract.frozenShadowTimestamps()) {
                assertThat(sql)
                        .containsIgnoringCase("created_at DATETIME(6) NULL")
                        .containsIgnoringCase("updated_at DATETIME(6) NULL");
            } else {
                assertThat(sql).containsIgnoringCase("created_at DATETIME(6) NOT NULL");
            }
        }
    }

    @Test
    void v2ShouldPreserveBookingItemUpdatedAtMicroseconds() throws IOException {
        String sql = readResource(MIGRATIONS.get(1));

        assertThat(sql)
                .containsPattern(
                        "(?is)UPDATE\\s+booking_request_items\\s+"
                                + "SET\\s+updated_at\\s*=\\s*COALESCE\\(created_at,\\s*CURRENT_TIMESTAMP\\(6\\)\\)\\s+"
                                + "WHERE\\s+updated_at\\s+IS\\s+NULL\\s*;"
                )
                .containsPattern(
                        "(?is)ALTER\\s+TABLE\\s+booking_request_items\\s+"
                                + "MODIFY\\s+COLUMN\\s+updated_at\\s+DATETIME\\(6\\)\\s+NOT\\s+NULL\\s+"
                                + "DEFAULT\\s+CURRENT_TIMESTAMP\\(6\\)\\s+"
                                + "ON\\s+UPDATE\\s+CURRENT_TIMESTAMP\\(6\\)\\s*;"
                )
                .doesNotContainIgnoringCase("DATETIME(0)");
    }

    @Test
    void v7ShouldCreateOfficialSpringSessionJdbcSchemaWithoutLegacyReuse() throws IOException {
        String sql = readResource(MIGRATIONS.getLast());
        Matcher matcher = CREATE_TABLE.matcher(sql);
        Set<String> createdTables = new LinkedHashSet<>();
        while (matcher.find()) {
            createdTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }

        assertThat(createdTables).containsExactlyInAnyOrderElementsOf(SPRING_SESSION_TABLES);
        assertThat(sql)
                .contains("PRIMARY_ID CHAR(36) NOT NULL")
                .contains("SESSION_ID CHAR(36) NOT NULL")
                .contains("CREATION_TIME BIGINT NOT NULL")
                .contains("LAST_ACCESS_TIME BIGINT NOT NULL")
                .contains("MAX_INACTIVE_INTERVAL INT NOT NULL")
                .contains("EXPIRY_TIME BIGINT NOT NULL")
                .contains("PRINCIPAL_NAME VARCHAR(100)")
                .contains("CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)")
                .contains("CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)")
                .contains("CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)")
                .contains("CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)")
                .contains("ATTRIBUTE_NAME VARCHAR(200) NOT NULL")
                .contains("ATTRIBUTE_BYTES BLOB NOT NULL")
                .contains("CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)")
                .contains("CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)"
                        + " REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE")
                .contains("ENGINE=InnoDB ROW_FORMAT=DYNAMIC")
                .contains("Spring Session version: 4.0.2")
                .contains("schema-mysql.sql")
                .contains("Acquired: 2026-07-24")
                .doesNotContainIgnoringCase("refresh_tokens")
                .doesNotContainIgnoringCase("CREATE TABLE IF NOT EXISTS");
        assertThat(DESTRUCTIVE_STATEMENT.matcher(sql).find()).isFalse();
        assertThat(LEGACY_FUTURE_TABLES)
                .allSatisfy(table -> assertThat(sql.toLowerCase(Locale.ROOT)).doesNotContain(table));
        assertThat(RUNTIME_TABLES)
                .allSatisfy(table -> assertThat(createdTables).doesNotContain(table));
    }

    @Test
    void h2SpringSessionFixtureShouldMatchOfficialSchemaAndStayTestOnly() throws IOException {
        String sql = readResource("session/schema-h2-spring-session-4.0.2.sql");

        assertThat(sql)
                .contains("Spring Session version: 4.0.2")
                .contains("schema-h2.sql")
                .contains("Acquired: 2026-07-24")
                .contains("CREATE TABLE SPRING_SESSION")
                .contains("CREATE TABLE SPRING_SESSION_ATTRIBUTES")
                .contains("ATTRIBUTE_BYTES LONGVARBINARY NOT NULL")
                .contains("ON DELETE CASCADE")
                .doesNotContain("ENGINE=InnoDB")
                .doesNotContainIgnoringCase("refresh_tokens");
        assertThat(DESTRUCTIVE_STATEMENT.matcher(sql).find()).isFalse();
        assertThat(new ClassPathResource("session/schema-h2-spring-session-4.0.2.sql").exists()).isTrue();
    }

    @Test
    void mysqlExampleShouldEnableFlywayWithSafeSchemaSettings() throws IOException {
        Properties properties = readProperties("application-example.properties");

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(properties.getProperty("app.time.business-zone")).contains("Europe/Rome");
        assertThat(properties.getProperty("app.time.clock-zone")).contains("UTC");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.timeout")).isEqualTo("30m");
        assertThat(properties.getProperty("server.servlet.session.cookie.name")).isEqualTo("__Host-STSESSION");
        assertThat(properties.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(readResource("application-example.properties"))
                .contains("connectionTimeZone=%2B00:00")
                .contains("forceConnectionTimeZoneToSession=true")
                .doesNotContain("spring.session.store-type=")
                .doesNotContain("hibernate.jdbc.time_zone=SYSTEM")
                .doesNotContain("hibernate.jdbc.time_zone=Europe/Rome")
                .doesNotContain("spring.jackson.time-zone");
    }

    @Test
    void h2TestProfileShouldKeepFlywayDisabledAndHibernateCreateDrop() throws IOException {
        Properties properties = readProperties("application-test.properties");

        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.timeout")).isEqualTo("30m");
        assertThat(properties.getProperty("spring.sql.init.mode")).isNull();
        assertThat(properties.getProperty("spring.sql.init.schema-locations")).isNull();
        assertThat(properties.getProperty("server.servlet.session.cookie.name")).isEqualTo("STSESSION");
        assertThat(properties.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
        assertThat(readResource("application-test.properties"))
                .doesNotContain("spring.flyway.baseline-on-migrate=true")
                .doesNotContain("spring.session.store-type=")
                .contains("schema-h2-spring-session-4.0.2.sql");
    }

    private static String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Properties readProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private record V3MigrationContract(String path, String table, List<String> columnDefinitions) {
    }

    private record V5MigrationContract(String path, String table, boolean frozenShadowTimestamps) {
    }
}
