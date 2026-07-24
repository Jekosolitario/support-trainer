package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * Certifies Spring Session JDBC wiring on H2 with an explicit test-only schema.
 * Does not certify MySQL DDL, InnoDB, BLOB semantics, locking or multi-instance cleanup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SpringSessionJdbcInfrastructureIntegrationTest {

    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSpringSessionTables() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    @DisplayName("Il contesto test espone SessionRepository JDBC con schema H2 esplicito")
    void shouldExposeJdbcSessionRepositoryWithExplicitH2Schema() {
        assertThat(sessionRepository).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE UPPER(TABLE_NAME) = 'SPRING_SESSION'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE UPPER(TABLE_NAME) = 'SPRING_SESSION_ATTRIBUTES'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("Spring Session JDBC deve creare, leggere e cancellare una sessione su H2")
    @SuppressWarnings("unchecked")
    void shouldCreateFindAndDeleteSession() {
        Session created = sessionRepository.createSession();
        created.setAttribute("lot1", "infrastructure");
        assertThat(created.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));

        sessionRepository.save(created);
        assertThat(countSessions()).isEqualTo(1);

        Session loaded = sessionRepository.findById(created.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.<String>getAttribute("lot1")).isEqualTo("infrastructure");
        assertThat(loaded.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));

        sessionRepository.deleteById(created.getId());
        assertThat(sessionRepository.findById(created.getId())).isNull();
        assertThat(countSessions()).isZero();
    }

    @Test
    @DisplayName("Il cleanup deterministico deve lasciare le tabelle sessione vuote tra i test")
    void shouldStartEachTestWithEmptySessionTables() {
        assertThat(countSessions()).isZero();
        assertThat(countSessionAttributes()).isZero();
    }

    private int countSessions() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        return count == null ? 0 : count;
    }

    private int countSessionAttributes() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES",
                Integer.class
        );
        return count == null ? 0 : count;
    }
}
