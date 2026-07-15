package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import io.jsonwebtoken.MalformedJwtException;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.exception.GlobalExceptionHandler;
import it.zuperman.support_trainer.common.response.ErrorResponseFactory;
import it.zuperman.support_trainer.common.response.ErrorResponseWriter;
import it.zuperman.support_trainer.security.config.RestAccessDeniedHandler;
import it.zuperman.support_trainer.security.config.RestAuthenticationEntryPoint;
import it.zuperman.support_trainer.security.jwt.JwtAuthenticationFilter;
import it.zuperman.support_trainer.security.jwt.JwtService;
import it.zuperman.support_trainer.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorTimestampDeterminismTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-13T15:30:45Z");

    @Test
    void shouldBuildGlobalErrorResponseAtFixedUtcInstant() {
        ErrorResponseFactory factory = new ErrorResponseFactory(fixedTimeProvider());
        GlobalExceptionHandler handler = new GlobalExceptionHandler(factory, mock(ErrorResponseWriter.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        Instant timestamp = handler.handleAppException(
                new AppException(HttpStatus.CONFLICT, "TEST_ERROR", "Errore di test"),
                request
        ).getBody().timestamp();

        assertThat(timestamp).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void shouldDelegateAuthenticationErrorToSharedWriter() throws Exception {
        ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("test"));

        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(writer).write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utente non autenticato");
    }

    @Test
    void shouldDelegateAccessDeniedErrorToSharedWriter() throws Exception {
        ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(writer);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("test"));

        verify(writer).write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accesso negato");
    }

    @Test
    void shouldDelegateInvalidJwtErrorToSharedWriter() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService, writer);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(jwtService.extractUsername("invalid-token")).thenThrow(new MalformedJwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(writer).write(eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq("INVALID_TOKEN"), any());
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        TimeProperties properties = new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        return new ApplicationTimeProvider(clock, properties);
    }
}
