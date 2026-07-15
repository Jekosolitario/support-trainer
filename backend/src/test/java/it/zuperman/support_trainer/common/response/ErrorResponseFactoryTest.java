package it.zuperman.support_trainer.common.response;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.exception.GlobalExceptionHandler;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import jakarta.servlet.RequestDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ErrorResponseFactoryTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-15T10:00:00.123456Z");

    @Test
    void shouldUseUtcTimestampRequestUriAndDeterministicFieldErrorOrder() {
        ErrorResponseFactory factory = new ErrorResponseFactory(fixedTimeProvider());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("token=must-not-appear");

        ErrorResponse response = factory.create(
                request,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "La richiesta contiene dati non validi",
                List.of(
                        new FieldErrorResponse("password", "Size", "Troppo corta"),
                        new FieldErrorResponse(null, "ValidRequest", "Errore globale"),
                        new FieldErrorResponse("email", "Email", "Formato non valido"),
                        new FieldErrorResponse("password", "NotBlank", "Obbligatoria")
                )
        );

        assertThat(response.timestamp()).isEqualTo(FIXED_INSTANT);
        assertThat(response.path()).isEqualTo("/api/v1/auth/login");
        assertThat(response.fieldErrors()).containsExactly(
                new FieldErrorResponse("email", "Email", "Formato non valido"),
                new FieldErrorResponse("password", "NotBlank", "Obbligatoria"),
                new FieldErrorResponse("password", "Size", "Troppo corta"),
                new FieldErrorResponse(null, "ValidRequest", "Errore globale")
        );
    }

    @Test
    void shouldPreferOriginalErrorRequestUri() {
        ErrorResponseFactory factory = new ErrorResponseFactory(fixedTimeProvider());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/original-resource");

        ErrorResponse response = factory.create(
                request,
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Risorsa non trovata"
        );

        assertThat(response.path()).isEqualTo("/api/v1/original-resource");
        assertThat(response.fieldErrors()).isNull();
    }

    @Test
    void shouldSanitizeInternalAppExceptionBeforeWritingTheResponse() {
        ErrorResponseFactory factory = new ErrorResponseFactory(fixedTimeProvider());
        GlobalExceptionHandler handler = new GlobalExceptionHandler(factory, mock(ErrorResponseWriter.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test-errors/application");

        ErrorResponse response = handler.handleAppException(
                new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ONLY", "internal detail"),
                request
        ).getBody();

        assertThat(response.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.message()).isEqualTo("Si è verificato un errore interno");
        assertThat(response.code()).doesNotContain("INTERNAL_ONLY");
        assertThat(response.message()).doesNotContain("internal detail");
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        return new ApplicationTimeProvider(
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"))
        );
    }
}
