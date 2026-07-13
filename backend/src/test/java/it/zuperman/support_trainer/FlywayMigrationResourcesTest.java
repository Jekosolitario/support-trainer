package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlywayMigrationResourcesTest {

    private static final List<String> MIGRATIONS = List.of(
            "db/migration/V1__create_legacy_compatible_runtime_schema.sql",
            "db/migration/V2__align_runtime_schema_contract.sql"
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

    private static final Pattern VALID_MIGRATION_NAME = Pattern.compile(
            "^V[1-9][0-9]*__[a-z0-9_]+\\.sql$"
    );

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+([a-z0-9_]+)"
    );

    private static final Pattern DESTRUCTIVE_STATEMENT = Pattern.compile(
            "(?im)^\\s*(DROP|TRUNCATE|DELETE)\\b"
    );

    @Test
    void shouldExposeVersionedMigrationsInExpectedOrderWithValidNames() {
        assertThat(MIGRATIONS)
                .extracting(path -> path.substring(path.lastIndexOf('/') + 1))
                .containsExactly(
                        "V1__create_legacy_compatible_runtime_schema.sql",
                        "V2__align_runtime_schema_contract.sql"
                )
                .allMatch(name -> VALID_MIGRATION_NAME.matcher(name).matches());

        assertThat(MIGRATIONS)
                .allSatisfy(path -> assertThat(new ClassPathResource(path).exists()).isTrue());
    }

    @Test
    void migrationDirectoryShouldContainOnlyV1AndV2() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");

        assertThat(resources)
                .extracting(Resource::getFilename)
                .containsExactlyInAnyOrder(
                        "V1__create_legacy_compatible_runtime_schema.sql",
                        "V2__align_runtime_schema_contract.sql"
                );
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
    void mysqlExampleShouldEnableFlywayWithSafeSchemaSettings() throws IOException {
        Properties properties = readProperties("application-example.properties");

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(readResource("application-example.properties"))
                .doesNotContain("spring.flyway.baseline-on-migrate=true");
    }

    @Test
    void h2TestProfileShouldKeepFlywayDisabledAndHibernateCreateDrop() throws IOException {
        Properties properties = readProperties("application-test.properties");

        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(readResource("application-test.properties"))
                .doesNotContain("spring.flyway.baseline-on-migrate=true");
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
}
