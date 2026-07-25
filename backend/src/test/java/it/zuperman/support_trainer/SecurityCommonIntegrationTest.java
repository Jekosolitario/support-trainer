package it.zuperman.support_trainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class SecurityCommonIntegrationTest {

    private static final String PROTECTED_ENDPOINT = "/api/v1/me/account";
    private static final String ROLE_PROTECTED_ENDPOINT = "/api/v1/professionals/my";
    private static final String PROFESSIONAL_EMAIL = "security.common@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Endpoint protetto senza sessione deve restituire unauthorized")
    void shouldRejectUnauthenticatedWithUnauthorizedErrorResponse() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("Le superfici Swagger inattive non devono essere pubbliche")
    void shouldRejectAnonymousSwaggerPaths() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Le superfici Swagger inattive devono restare 404 per utenti autenticati")
    void shouldReturnNotFoundForAuthenticatedSwaggerPaths() throws Exception {
        CsrfSession session = loginProfessional();

        mockMvc.perform(get("/swagger-ui/index.html")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isNotFound())
                .andExpectAll(errorResponse(404, "RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/v3/api-docs")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isNotFound())
                .andExpectAll(errorResponse(404, "RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Header Authorization Bearer deve essere ignorato e non autenticare")
    void shouldIgnoreBearerAuthorizationHeader() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-session-token"))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("Utente autenticato senza authority corretta deve restituire forbidden")
    void shouldRejectAuthenticatedUserWithWrongAuthority() throws Exception {
        CsrfSession session = loginProfessional();

        mockMvc.perform(get(ROLE_PROTECTED_ENDPOINT)
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isForbidden())
                .andExpectAll(errorResponse(403, "ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Mutazione senza CSRF deve restituire CSRF_VALIDATION_FAILED")
    void shouldRejectMutatingRequestWithoutCsrf() throws Exception {
        CsrfSession session = loginProfessional();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isForbidden())
                .andExpectAll(errorResponse(403, "CSRF_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Body obbligatorio mancante deve restituire bad request coerente")
    void shouldReturnBadRequestForMissingRequiredBody() throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpectAll(errorResponse(400, "MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Endpoint inesistente autenticato deve restituire not found")
    void shouldReturnNotFoundForMissingAuthenticatedEndpoint() throws Exception {
        CsrfSession session = loginProfessional();

        mockMvc.perform(get("/api/v1/endpoint-not-found")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isNotFound())
                .andExpectAll(errorResponse(404, "RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Metodo HTTP non supportato deve restituire method not allowed")
    void shouldReturnMethodNotAllowedForUnsupportedHttpMethod() throws Exception {
        createProfessional();
        CsrfSession session = SessionAuthTestSupport.loginAndRefreshCsrf(
                mockMvc,
                PROFESSIONAL_EMAIL,
                PASSWORD
        );

        mockMvc.perform(put(PROTECTED_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(session)))
                .andExpect(status().isMethodNotAllowed())
                .andExpectAll(errorResponse(405, "METHOD_NOT_ALLOWED"))
                .andExpect(header().exists(HttpHeaders.ALLOW));
    }

    @Test
    @DisplayName("Media type non supportato deve restituire unsupported media type")
    void shouldReturnUnsupportedMediaType() throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<login/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpectAll(errorResponse(415, "UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(header().exists(HttpHeaders.ACCEPT));
    }

    private CsrfSession loginProfessional() throws Exception {
        createProfessional();
        return SessionAuthTestSupport.login(mockMvc, PROFESSIONAL_EMAIL, PASSWORD);
    }

    private void createProfessional() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                PROFESSIONAL_EMAIL,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        professionalProfileRepository.saveAndFlush(professional);
    }

    private ResultMatcher[] errorResponse(int expectedStatus, String expectedCode) {
        return new ResultMatcher[]{
            jsonPath("$.timestamp").isNotEmpty(),
            jsonPath("$.status").value(expectedStatus),
            jsonPath("$.code").value(expectedCode),
            jsonPath("$.message").isNotEmpty(),
            jsonPath("$.path").isNotEmpty(),
            jsonPath("$.error").doesNotExist(),
            jsonPath("$.errorCode").doesNotExist(),
            jsonPath("$.validationErrors").doesNotExist(),
            jsonPath("$.fieldErrors").doesNotExist()
        };
    }
}
