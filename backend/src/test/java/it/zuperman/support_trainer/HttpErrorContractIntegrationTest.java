package it.zuperman.support_trainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.common.exception.AppException;
import jakarta.servlet.RequestDispatcher;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    SupportTrainerApplication.class,
    HttpErrorContractIntegrationTest.ErrorEndpointTestConfiguration.class
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class HttpErrorContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldConfigureJacksonForStrictInput() {
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isTrue();
        assertThat(objectMapper.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION)).isTrue();
    }

    @Test
    void shouldReturnValidationFieldErrorsAsAnOrderedList() throws Exception {
        String payload = """
                {"email":"invalid","password":""}
                """;

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La richiesta contiene dati non validi"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(timestampIsInstant())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, String>> fieldErrors = JsonPath.read(body, "$.fieldErrors");
        assertThat(fieldErrors)
                .extracting(error -> error.get("field"))
                .containsExactly("email", "password", "password");
        assertThat(fieldErrors)
                .extracting(error -> error.get("code"))
                .containsExactly("Email", "NotBlank", "Size");
        assertThat(body).doesNotContain("rejectedValue", "objectName");
    }

    @Test
    void shouldRejectUnknownTrailingAndDuplicateJsonProperties() throws Exception {
        assertMalformedJson("""
                {"email":"user@example.com","password":"Password123!","unexpected":true}
                """);
        assertMalformedJson("""
                {"email":"user@example.com","password":"Password123!"} {}
                """);
        assertMalformedJson("""
                {"email":"first@example.com","email":"second@example.com","password":"Password123!"}
                """);
    }

    @Test
    void shouldReturnUniformUnauthorizedForNonBearerScheme() throws Exception {
        mockMvc.perform(get("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpectAll(commonError(401, "UNAUTHORIZED", "/api/v1/me/account"));
    }

    @Test
    void shouldReturnSpecificCodesForMissingAndInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-errors/parameters"))
                .andExpect(status().isBadRequest())
                .andExpectAll(commonError(
                        400,
                        "MISSING_REQUEST_PARAMETER",
                        "/api/v1/auth/test-errors/parameters"
                ));

        mockMvc.perform(get("/api/v1/auth/test-errors/parameters/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpectAll(commonError(
                        400,
                        "INVALID_REQUEST_PARAMETER",
                        "/api/v1/auth/test-errors/parameters/not-a-number"
                ));
    }

    @Test
    void shouldWriteJsonEvenWhenTheRequestedRepresentationIsUnsupported() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content("{\"email\":\"not-registered@example.com\"}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"))
                .andExpectAll(commonError(406, "NOT_ACCEPTABLE", "/api/v1/auth/email-verification/resend"));
    }

    @Test
    void shouldUseUniformErrorResponseForErrorDispatchWithOriginalPath() throws Exception {
        MockHttpServletRequestBuilder request = get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value())
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/resources/missing");

        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpectAll(commonError(404, "RESOURCE_NOT_FOUND", "/api/v1/resources/missing"));

        MockHttpServletRequestBuilder serverErrorRequest = get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value())
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/processing")
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "internal dispatch detail");

        String body = mockMvc.perform(serverErrorRequest)
                .andExpect(status().isInternalServerError())
                .andExpectAll(commonError(500, "INTERNAL_SERVER_ERROR", "/api/v1/processing"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("internal dispatch detail");
    }

    @Test
    void shouldSanitizeUnexpectedAndInternalApplicationFailures() throws Exception {
        assertSanitizedServerError(
                "/api/v1/auth/test-errors/runtime",
                "unexpected runtime detail"
        );
        assertSanitizedServerError(
                "/api/v1/auth/test-errors/application",
                "internal application detail"
        );
    }

    private void assertMalformedJson(String payload) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpectAll(commonError(400, "MALFORMED_REQUEST", "/api/v1/auth/login"));
    }

    private void assertSanitizedServerError(String path, String internalDetail) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isInternalServerError())
                .andExpectAll(commonError(500, "INTERNAL_SERVER_ERROR", path))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(internalDetail, "IllegalStateException", "INTERNAL_TEST_CODE", "fieldErrors");
    }

    private ResultMatcher[] commonError(int expectedStatus, String expectedCode, String expectedPath) {
        return new ResultMatcher[]{
            timestampIsInstant(),
            jsonPath("$.status").value(expectedStatus),
            jsonPath("$.code").value(expectedCode),
            jsonPath("$.message").isNotEmpty(),
            jsonPath("$.path").value(expectedPath),
            jsonPath("$.error").doesNotExist(),
            jsonPath("$.errorCode").doesNotExist(),
            jsonPath("$.validationErrors").doesNotExist(),
            jsonPath("$.fieldErrors").doesNotExist()
        };
    }

    private ResultMatcher timestampIsInstant() {
        return result -> Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorEndpointTestConfiguration {

        @Bean
        TestOnlyErrorController testOnlyErrorController() {
            return new TestOnlyErrorController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/auth/test-errors")
    static class TestOnlyErrorController {

        @GetMapping("/runtime")
        void runtimeFailure() {
            throw new IllegalStateException("unexpected runtime detail");
        }

        @GetMapping("/application")
        void applicationFailure() {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_TEST_CODE",
                    "internal application detail"
            );
        }

        @GetMapping("/parameters")
        void requiredParameter(@RequestParam String required) {
        }

        @GetMapping("/parameters/{id}")
        void numericPathVariable(@PathVariable Long id) {
        }
    }
}
