package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import it.zuperman.support_trainer.security.config.RestAccessDeniedHandler;
import it.zuperman.support_trainer.security.config.RestAuthenticationEntryPoint;
import it.zuperman.support_trainer.security.jwt.JwtAuthenticationFilter;
import it.zuperman.support_trainer.security.jwt.JwtService;
import it.zuperman.support_trainer.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorTimestampDeterminismTest {

    private static final LocalDateTime FIXED_BUSINESS_DATE_TIME = LocalDateTime.of(2026, 7, 13, 17, 30, 45);

    @Test
    void shouldBuildGlobalErrorResponseAtFixedBusinessTime() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(fixedTimeProvider());

        LocalDateTime timestamp = handler.handleAppException(
                new AppException(HttpStatus.CONFLICT, "TEST_ERROR", "Errore di test")
        ).getBody().getTimestamp();

        assertThat(timestamp).isEqualTo(FIXED_BUSINESS_DATE_TIME);
    }

    @Test
    void shouldBuildAuthenticationErrorAtFixedBusinessTime() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(fixedTimeProvider());
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("test")
        );

        assertThat(response.getContentAsString()).contains("\"timestamp\": \"2026-07-13T17:30:45\"");
    }

    @Test
    void shouldBuildAccessDeniedErrorAtFixedBusinessTime() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(fixedTimeProvider());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("test")
        );

        assertThat(response.getContentAsString()).contains("\"timestamp\": \"2026-07-13T17:30:45\"");
    }

    @Test
    void shouldBuildInvalidJwtErrorAtFixedBusinessTime() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                fixedTimeProvider()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(jwtService.extractUsername("invalid-token")).thenThrow(new MalformedJwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getContentAsString()).contains("\"timestamp\": \"2026-07-13T17:30:45\"");
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        TimeProperties properties = new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T15:30:45Z"), ZoneOffset.UTC);
        return new ApplicationTimeProvider(clock, properties);
    }
}
