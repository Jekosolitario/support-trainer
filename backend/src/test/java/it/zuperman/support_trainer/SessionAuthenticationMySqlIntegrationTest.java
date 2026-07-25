package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.SessionAttributeNames;
import it.zuperman.support_trainer.session.MySqlSessionTestDatabaseNames;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

/**
 * Opt-in MySQL certification for the real session-auth path through Spring Session + Security.
 * Enable with: {@code -Dit.mysql.enabled=true}.
 * Requires {@code MYSQL_PWD} and a MySQL instance clearly reserved for tests.
 */
@EnabledIfSystemProperty(named = "it.mysql.enabled", matches = "true")
class SessionAuthenticationMySqlIntegrationTest {

    private static final String DEFAULT_AUTH_SCHEMA = MySqlSessionTestDatabaseNames.PREFIX + "auth";
    private static final String EMAIL = "mysql.session.auth@example.com";
    private static final String PASSWORD = "Password123!";

    @Test
    @DisplayName("MySQL: CSRF, login, SecurityContext/authenticatedAt persistiti, GET protetta, logout")
    void shouldPersistSessionAuthThroughRealSecurityFiltersOnMysql() throws Exception {
        String password = requireEnvironment("MYSQL_PWD");
        String schema = resolveSchemaProperty("it.mysql.auth-schema", DEFAULT_AUTH_SCHEMA);
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);

        Throwable primaryFailure = null;
        ConfigurableApplicationContext context = null;
        boolean schemaCreated = false;
        try {
            recreateSchema(validatedSchema, password);
            schemaCreated = true;
            assertThat(flyway(validatedSchema, password).migrate().migrationsExecuted).isGreaterThan(0);

            context = springContext(validatedSchema, password);
            WebApplicationContext webContext = (WebApplicationContext) context;

            SessionRepositoryFilter<?> sessionRepositoryFilter
                    = webContext.getBean(SessionRepositoryFilter.class);
            FilterChainProxy springSecurityFilterChain
                    = webContext.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(sessionRepositoryFilter).isNotNull();
            assertThat(springSecurityFilterChain).isNotNull();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                    .addFilters(sessionRepositoryFilter)
                    .apply(springSecurity())
                    .build();

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
            ProfessionalProfileRepository professionalProfileRepository
                    = context.getBean(ProfessionalProfileRepository.class);

            ProfessionalProfile professional = new ProfessionalProfile(
                    "MySql",
                    "Auth",
                    EMAIL,
                    passwordEncoder.encode(PASSWORD),
                    ProfessionalSpecialization.PERSONAL_TRAINER
            );
            professional.setAccountStatus(AccountStatus.ACTIVE);
            professional.setEmailVerified(true);
            professional.setActive(true);
            professionalProfileRepository.saveAndFlush(professional);

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class))
                    .isZero();

            CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(SessionAuthTestSupport.loginBody(EMAIL, PASSWORD)))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().exists("STSESSION"))
                    .andReturn();

            CsrfSession authenticated = new CsrfSession(
                    SessionAuthTestSupport.mergeCookies(csrf.cookies(), loginResult.getResponse().getCookies()),
                    csrf.token(),
                    csrf.headerName()
            );

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class))
                    .isEqualTo(1);

            List<String> attributeNames = jdbcTemplate.queryForList(
                    "SELECT ATTRIBUTE_NAME FROM SPRING_SESSION_ATTRIBUTES",
                    String.class
            );
            assertThat(attributeNames).contains(
                    "SPRING_SECURITY_CONTEXT",
                    SessionAttributeNames.AUTHENTICATED_AT
            );

            mockMvc.perform(get("/api/v1/me/account")
                            .with(SessionAuthTestSupport.withSession(authenticated)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL));

            CsrfSession logoutCsrf = SessionAuthTestSupport.fetchCsrf(mockMvc, authenticated);
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(SessionAuthTestSupport.withSessionAndCsrf(logoutCsrf)))
                    .andExpect(status().isNoContent());

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class))
                    .isZero();

            mockMvc.perform(get("/api/v1/me/account")
                            .with(SessionAuthTestSupport.withSession(authenticated)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        } catch (Throwable failure) {
            primaryFailure = failure;
        } finally {
            ConfigurableApplicationContext contextToClose = context;
            boolean shouldDropSchema = schemaCreated;
            primaryFailure = MySqlTestLifecycleSupport.runCleanup(
                    primaryFailure,
                    contextToClose == null ? null : contextToClose::close,
                    shouldDropSchema
                            ? () -> dropSchema(validatedSchema, password)
                            : () -> {
                            }
            );
        }

        MySqlTestLifecycleSupport.rethrowIfPresent(primaryFailure);
    }

    private static ConfigurableApplicationContext springContext(String schema, String password) {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        return new SpringApplicationBuilder(SupportTrainerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl(validatedSchema),
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=" + password,
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.open-in-view=false",
                        "--spring.session.jdbc.initialize-schema=never",
                        "--spring.session.timeout=30m",
                        "--server.servlet.session.cookie.name=STSESSION",
                        "--server.servlet.session.cookie.http-only=true",
                        "--server.servlet.session.cookie.secure=false",
                        "--server.servlet.session.cookie.same-site=strict",
                        "--server.servlet.session.cookie.path=/",
                        "--app.email.mode=DISABLED",
                        "--app.email.verification-page-url=http://localhost:5173/verify-email",
                        "--app.time.business-zone=Europe/Rome",
                        "--app.time.clock-zone=UTC"
                );
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

    private static void dropSchema(String schema, String password) throws Exception {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        String quoted = MySqlSessionTestDatabaseNames.quoteIdentifier(validatedSchema);
        try (Connection connection = DriverManager.getConnection(jdbcServerUrl(), "root", password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quoted);
        }
    }

    private static Flyway flyway(String schema, String password) {
        String validatedSchema = MySqlSessionTestDatabaseNames.requireValid(schema);
        return Flyway.configure()
                .dataSource(jdbcUrl(validatedSchema), "root", password)
                .locations("classpath:db/migration")
                .load();
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
