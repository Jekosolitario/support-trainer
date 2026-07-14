package it.zuperman.support_trainer;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.jwt.JwtService;
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
    private static final String TEST_ALLOWED_ORIGIN = "http://localhost";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("Endpoint protetto senza JWT deve restituire unauthorized")
    void shouldRejectMissingJwtWithUnauthorizedErrorResponse() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED", "UNAUTHORIZED"));
    }

    @Test
    @DisplayName("JWT alterato deve restituire invalid token")
    void shouldRejectAlteredJwt() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String alteredToken = alterSignature(jwtService.generateAccessToken(userDetails));

        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alteredToken)))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED", "INVALID_TOKEN"));
    }

    @Test
    @DisplayName("JWT scaduto deve restituire token expired")
    void shouldRejectExpiredJwt() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String expiredToken = generateExpiredAccessToken(userDetails);

        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED", "TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Refresh token usato come Bearer deve essere rifiutato")
    void shouldRejectRefreshTokenUsedAsBearer() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpectAll(errorResponse(401, "UNAUTHORIZED", "INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Utente autenticato senza authority corretta deve restituire forbidden")
    void shouldRejectAuthenticatedUserWithWrongAuthority() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String accessToken = jwtService.generateAccessToken(userDetails);

        mockMvc.perform(get(ROLE_PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpectAll(errorResponse(403, "FORBIDDEN", "ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Parametro obbligatorio mancante deve restituire bad request coerente")
    void shouldReturnBadRequestForMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify-email"))
                .andExpect(status().isBadRequest())
                .andExpectAll(errorResponse(400, "BAD_REQUEST", "MISSING_REQUEST_PARAMETER"));
    }

    @Test
    @DisplayName("Endpoint inesistente autenticato deve restituire not found")
    void shouldReturnNotFoundForMissingAuthenticatedEndpoint() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String accessToken = jwtService.generateAccessToken(userDetails);

        mockMvc.perform(get("/api/v1/endpoint-not-found")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpectAll(errorResponse(404, "NOT_FOUND", "RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Metodo HTTP non supportato deve restituire method not allowed")
    void shouldReturnMethodNotAllowedForUnsupportedHttpMethod() throws Exception {
        UserDetails userDetails = createProfessionalUserDetails();
        String accessToken = jwtService.generateAccessToken(userDetails);

        mockMvc.perform(put(PROTECTED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpectAll(errorResponse(405, "METHOD_NOT_ALLOWED", "METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Media type non supportato deve restituire unsupported media type")
    void shouldReturnUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<login/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpectAll(errorResponse(415, "UNSUPPORTED_MEDIA_TYPE", "UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("Preflight da origine consentita deve essere accettato")
    void shouldAcceptPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .with(request -> {
                            request.setServerName("backend.test");
                            return request;
                        })
                        .header(HttpHeaders.ORIGIN, TEST_ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TEST_ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("Preflight da origine non consentita deve essere rifiutato")
    void shouldRejectPreflightFromDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://disallowed.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("Preflight deve consentire l'header Authorization")
    void shouldAllowAuthorizationHeaderInPreflight() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .with(request -> {
                            request.setServerName("backend.test");
                            return request;
                        })
                        .header(HttpHeaders.ORIGIN, TEST_ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TEST_ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, HttpHeaders.AUTHORIZATION));
    }

    private UserDetails createProfessionalUserDetails() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                PROFESSIONAL_EMAIL,
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);

        ProfessionalProfile savedProfessional = professionalProfileRepository.saveAndFlush(professional);

        return org.springframework.security.core.userdetails.User.builder()
                .username(savedProfessional.getEmail())
                .password(savedProfessional.getPassword())
                .authorities(savedProfessional.getRole().name())
                .build();
    }

    private String generateExpiredAccessToken(UserDetails userDetails) {
        Instant now = Instant.now();

        return Jwts.builder()
                .claim("token_type", "access")
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    private String alterSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureCharacter = token.charAt(signatureStart);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';

        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultMatcher[] errorResponse(int expectedStatus, String expectedError, String expectedErrorCode) {
        return new ResultMatcher[]{
            jsonPath("$.timestamp").isNotEmpty(),
            jsonPath("$.status").value(expectedStatus),
            jsonPath("$.error").value(expectedError),
            jsonPath("$.errorCode").value(expectedErrorCode),
            jsonPath("$.message").isNotEmpty()
        };
    }
}
