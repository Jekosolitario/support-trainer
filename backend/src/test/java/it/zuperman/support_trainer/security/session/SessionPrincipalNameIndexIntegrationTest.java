package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SessionPrincipalNameIndexIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @BeforeEach
    void clearSpringSessionTables() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    @DisplayName("Dopo login, SPRING_SESSION.PRINCIPAL_NAME deve coincidere con userId e l'indice deve trovare la sessione")
    void loginMustIndexPrincipalNameAsUserId() throws Exception {
        String email = "principal.index." + UUID.randomUUID() + "@example.com";
        ProfessionalProfile professional = new ProfessionalProfile(
                "Principal",
                "Index",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional = professionalProfileRepository.saveAndFlush(professional);
        String expectedPrincipalName = professional.getId().toString();

        CsrfSession session = SessionAuthTestSupport.login(mockMvc, email, PASSWORD);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());

        List<String> principalNames = jdbcTemplate.queryForList(
                "SELECT PRINCIPAL_NAME FROM SPRING_SESSION",
                String.class
        );
        assertThat(principalNames)
                .isNotEmpty()
                .allMatch(name -> name != null && !name.isBlank())
                .containsOnly(expectedPrincipalName);

        Map<String, ? extends Session> indexed = sessionRepository.findByPrincipalName(expectedPrincipalName);
        assertThat(indexed).isNotEmpty();
        assertThat(indexed.values()).allSatisfy(loaded -> assertThat(loaded).isNotNull());
    }
}
