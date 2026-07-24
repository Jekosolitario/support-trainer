package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.repository.UserRepository;

/**
 * Temporary neutrality gate for Lot 1: JWT remains the only live authentication mechanism.
 * Session cookie login, CSRF and logout endpoints are intentionally still inactive.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class JwtSessionNeutralityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSpringSessionTables() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    @DisplayName("Login JWT deve restare 200 con token e senza cookie o riga SPRING_SESSION")
    void loginMustRemainJwtOnlyWithoutSessionCookieOrJdbcRow() throws Exception {
        String email = "lot1.jwt.neutral." + UUID.randomUUID() + "@example.com";
        String password = "Password123!";
        registerAndVerifyProfessional(email, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        assertNoSessionCookie(loginResult);
        assertThat(countSessions()).isZero();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        assertThat(accessToken).isNotBlank();
        assertThat((String) JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken"))
                .isNotBlank();
    }

    @Test
    @DisplayName("Una richiesta Bearer protetta non deve creare sessione JDBC né cookie di sessione")
    void bearerProtectedRequestMustNotCreateJdbcSessionOrCookie() throws Exception {
        String email = "lot1.jwt.bearer." + UUID.randomUUID() + "@example.com";
        String password = "Password123!";
        registerAndVerifyProfessional(email, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        clearSpringSessionTables();

        MvcResult meResult = mockMvc.perform(get("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        assertNoSessionCookie(meResult);
        assertThat(countSessions()).isZero();

        mockMvc.perform(get("/api/v1/me/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF e logout a sessione non devono essere attivi nel Lotto 1")
    void csrfAndSessionLogoutMustRemainInactive() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().isUnauthorized());

        // Default LogoutFilter may still redirect; it must not become session-auth logout.
        MvcResult logoutResult = mockMvc.perform(post("/logout"))
                .andExpect(status().isFound())
                .andReturn();
        assertNoSessionCookie(logoutResult);
        assertThat(countSessions()).isZero();

        // CSRF disabled: login without CSRF token must still reach the auth handler.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing.csrf@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"));
    }

    private void registerAndVerifyProfessional(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Lot",
                                  "lastName": "One",
                                  "email": "%s",
                                  "password": "%s",
                                  "specialization": "PERSONAL_TRAINER"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isAccepted());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(verificationToken.getToken())))
                .andExpect(status().isOk());
    }

    private void assertNoSessionCookie(MvcResult result) {
        List<String> setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .as("JWT flows must not emit a session cookie in Lot 1")
                .isEmpty();
    }

    private int countSessions() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        return count == null ? 0 : count;
    }
}
