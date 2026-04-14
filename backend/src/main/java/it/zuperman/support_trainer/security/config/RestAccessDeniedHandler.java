package it.zuperman.support_trainer.security.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        String body = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Accesso negato"
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }

    private String buildErrorResponse(HttpStatus status, String errorCode, String message) {
        return """
                {
                  "timestamp": "%s",
                  "status": %d,
                  "error": "%s",
                  "errorCode": "%s",
                  "message": "%s",
                  "validationErrors": null
                }
                """.formatted(
                LocalDateTime.now(),
                status.value(),
                status.name(),
                errorCode,
                message
        );
    }
}