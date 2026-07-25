package it.zuperman.support_trainer.security.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.response.ErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        if (isCsrfFailure(accessDeniedException)) {
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "CSRF_VALIDATION_FAILED",
                    "La verifica di sicurezza della richiesta non è riuscita"
            );
            return;
        }

        errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accesso negato");
    }

    private static boolean isCsrfFailure(AccessDeniedException accessDeniedException) {
        Throwable current = accessDeniedException;
        while (current != null) {
            if (current instanceof CsrfException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
