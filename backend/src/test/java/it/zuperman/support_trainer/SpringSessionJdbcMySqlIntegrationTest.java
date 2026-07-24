package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import it.zuperman.support_trainer.session.MySqlSessionTestDatabaseNames;

/**
 * Opt-in MySQL certification for Spring Session JDBC schema (Flyway V7).
 * Enable with: {@code -Dit.mysql.enabled=true}.
 * Requires {@code MYSQL_PWD} and a MySQL instance clearly reserved for tests.
 * Optional overrides {@code it.mysql.empty-schema} / {@code it.mysql.v6-schema}
 * must obey {@link MySqlSessionTestDatabaseNames}.
 */
@EnabledIfSystemProperty(named = "it.mysql.enabled", matches = "true")
class SpringSessionJdbcMySqlIntegrationTest {

    static final String JWT_SECRET
            = "VGhpc0lzQVRlc3RTZWNyZXRLZXlGb3JKV1RUaGF0SXNMb25nRW5vdWdoMTIzNDU2";
    static final String JWT_EXPIRATION = "1h";
    static final String JWT_REFRESH_EXPIRATION = "7d";

    @Test
    @DisplayName("MySQL: Flyway fino a V7, upgrade da V6, Hibernate validate e SessionRepository CRUD")
    void shouldMigrateValidateAndOperateSpringSessionOnMysql() throws Exception {
        String password = requireEnvironment("MYSQL_PWD");
        String[] schemas = MySqlSessionTestDatabaseNames.requireDistinctPair(
                resolveSchemaProperty("it.mysql.empty-schema", MySqlSessionTestDatabaseNames.DEFAULT_EMPTY_SCHEMA),
                resolveSchemaProperty("it.mysql.v6-schema", MySqlSessionTestDatabaseNames.DEFAULT_FROM_V6_SCHEMA)
        );
        String emptySchema = schemas[0];
        String fromV6Schema = schemas[1];

        recreateSchema(emptySchema, password);
        Flyway emptyFlyway = flyway(emptySchema, password, null);
        assertThat(emptyFlyway.migrate().migrationsExecuted).isGreaterThan(0);
        assertThat(emptyFlyway.migrate().migrationsExecuted).isZero();
        assertSpringSessionSchema(emptySchema, password);
        validateWithHibernate(emptySchema, password);
        assertSessionRepositoryCrud(emptySchema, password);
        assertInitializeSchemaNeverDoesNotRecreateTables(emptySchema, password);

        recreateSchema(fromV6Schema, password);
        assertThat(flyway(fromV6Schema, password, "6").migrate().migrationsExecuted).isGreaterThan(0);
        assertThat(tableExists(fromV6Schema, password, "SPRING_SESSION")).isFalse();
        Flyway upgrade = flyway(fromV6Schema, password, null);
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertSpringSessionSchema(fromV6Schema, password);
        validateWithHibernate(fromV6Schema, password);
        assertSessionRepositoryCrud(fromV6Schema, password);
    }

    private static void assertSpringSessionSchema(String schema, String password) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        assertThat(tableExists(validatedSchema, password, "SPRING_SESSION")).isTrue();
        assertThat(tableExists(validatedSchema, password, "SPRING_SESSION_ATTRIBUTES")).isTrue();

        try (Connection connection = DriverManager.getConnection(jdbcUrl(validatedSchema), "root", password);
                Statement statement = connection.createStatement()) {
            assertIndexNames(statement, validatedSchema, "SPRING_SESSION", Set.of(
                    "SPRING_SESSION_IX1",
                    "SPRING_SESSION_IX2",
                    "SPRING_SESSION_IX3",
                    "PRIMARY"
            ));
            assertIndexNames(statement, validatedSchema, "SPRING_SESSION_ATTRIBUTES", Set.of(
                    "SPRING_SESSION_ATTRIBUTES_FK",
                    "PRIMARY"
            ));

            try (ResultSet engine = statement.executeQuery(
                    "SELECT ENGINE, ROW_FORMAT FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA = '" + validatedSchema + "' AND TABLE_NAME = 'SPRING_SESSION'"
            )) {
                assertThat(engine.next()).isTrue();
                assertThat(engine.getString("ENGINE")).isEqualToIgnoringCase("InnoDB");
                assertThat(engine.getString("ROW_FORMAT").toUpperCase(Locale.ROOT)).contains("DYNAMIC");
            }

            try (ResultSet columns = statement.executeQuery(
                    "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = '" + validatedSchema
                            + "' AND TABLE_NAME = 'SPRING_SESSION_ATTRIBUTES' "
                            + "AND COLUMN_NAME = 'ATTRIBUTE_BYTES'"
            )) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getString("DATA_TYPE").toLowerCase(Locale.ROOT)).isEqualTo("blob");
            }

            try (ResultSet foreignKeys = statement.executeQuery(
                    "SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS "
                            + "WHERE CONSTRAINT_SCHEMA = '" + validatedSchema + "' "
                            + "AND CONSTRAINT_NAME = 'SPRING_SESSION_ATTRIBUTES_FK'"
            )) {
                assertThat(foreignKeys.next()).isTrue();
                assertThat(foreignKeys.getString("DELETE_RULE")).isEqualToIgnoringCase("CASCADE");
            }
        }
    }

    private static void assertIndexNames(
            Statement statement,
            String schema,
            String table,
            Set<String> expected
    ) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        Set<String> actual = new HashSet<>();
        try (ResultSet indexes = statement.executeQuery(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = '" + validatedSchema + "' AND TABLE_NAME = '" + table + "'"
        )) {
            while (indexes.next()) {
                actual.add(indexes.getString("INDEX_NAME").toUpperCase(Locale.ROOT));
            }
        }
        assertThat(actual).containsAll(expected);
    }

    private static void assertSessionRepositoryCrud(String schema, String password) {
        try (ConfigurableApplicationContext context = springContext(schema, password, "validate")) {
            @SuppressWarnings("rawtypes")
            SessionRepository sessionRepository = context.getBean(SessionRepository.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            jdbcTemplate.update("DELETE FROM SPRING_SESSION");

            @SuppressWarnings("unchecked")
            Session created = sessionRepository.createSession();
            created.setAttribute("mysql-lot1", "ok");
            assertThat(created.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
            sessionRepository.save(created);

            Session loaded = sessionRepository.findById(created.getId());
            assertThat(loaded).isNotNull();
            assertThat(loaded.<String>getAttribute("mysql-lot1")).isEqualTo("ok");

            loaded.setAttribute("mysql-lot1", "updated");
            sessionRepository.save(loaded);
            assertThat(sessionRepository.findById(created.getId()).<String>getAttribute("mysql-lot1"))
                    .isEqualTo("updated");

            sessionRepository.deleteById(created.getId());
            assertThat(sessionRepository.findById(created.getId())).isNull();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class))
                    .isZero();
        }
    }

    private static void assertInitializeSchemaNeverDoesNotRecreateTables(
            String schema,
            String password
    ) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(validatedSchema), "root", password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE SPRING_SESSION_ATTRIBUTES");
            statement.execute("DROP TABLE SPRING_SESSION");
        }

        try (ConfigurableApplicationContext ignored = springContext(validatedSchema, password, "none")) {
            assertThat(tableExists(validatedSchema, password, "SPRING_SESSION")).isFalse();
            assertThat(tableExists(validatedSchema, password, "SPRING_SESSION_ATTRIBUTES")).isFalse();
        }
    }

    static String[] jwtContextArguments() {
        return new String[] {
                "--app.security.jwt.secret=" + JWT_SECRET,
                "--app.security.jwt.expiration=" + JWT_EXPIRATION,
                "--app.security.jwt.refresh-expiration=" + JWT_REFRESH_EXPIRATION
        };
    }

    private static ConfigurableApplicationContext springContext(
            String schema,
            String password,
            String ddlAuto
    ) {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        // SERVLET is required for Boot 4 JdbcSessionAutoConfiguration (@ConditionalOnWebApplication).
        return new SpringApplicationBuilder(SupportTrainerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl(validatedSchema),
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=" + password,
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                        "--spring.jpa.open-in-view=false",
                        "--spring.session.jdbc.initialize-schema=never",
                        "--spring.session.timeout=30m",
                        "--app.cors.allowed-origins=http://localhost",
                        "--app.email.mode=DISABLED",
                        "--app.email.verification-page-url=http://localhost:5173/verify-email",
                        jwtContextArguments()[0],
                        jwtContextArguments()[1],
                        jwtContextArguments()[2]
                );
    }

    private static void validateWithHibernate(String schema, String password) {
        try (ConfigurableApplicationContext ignored = springContext(schema, password, "validate")) {
            assertThat(ignored.isRunning()).isTrue();
        }
    }

    private static void recreateSchema(String schema, String password) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        String quoted = MySqlSessionTestDatabaseNames.quoteIdentifier(validatedSchema);
        try (Connection connection = DriverManager.getConnection(jdbcServerUrl(), "root", password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quoted);
            statement.execute("CREATE DATABASE " + quoted);
        }
    }

    private static boolean tableExists(String schema, String password, String table) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(validatedSchema), "root", password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = '" + validatedSchema + "' AND TABLE_NAME = '" + table + "'"
                )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1) > 0;
        }
    }

    private static Flyway flyway(String schema, String password, String target) {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl(validatedSchema), "root", password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String jdbcUrl(String schema) {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        return "jdbc:mysql://localhost:3306/" + validatedSchema
                + "?connectionTimeZone=%2B00&forceConnectionTimeZoneToSession=true";
    }

    private static String jdbcServerUrl() {
        return "jdbc:mysql://localhost:3306/"
                + "?connectionTimeZone=%2B00&forceConnectionTimeZoneToSession=true";
    }

    private static String resolveSchemaProperty(String propertyName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return MySqlSessionTestDatabaseNames.requireValid(defaultValue);
        }
        return MySqlSessionTestDatabaseNames.requireValid(value);
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when MySQL integration tests are enabled");
        }
        return value;
    }
}
