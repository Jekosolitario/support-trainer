package it.zuperman.support_trainer.common.exception;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import it.zuperman.support_trainer.common.response.ErrorResponseWriter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class UniformErrorController implements ErrorController {

    private final ErrorResponseWriter errorResponseWriter;

    public UniformErrorController(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @RequestMapping("/error")
    public void error(HttpServletRequest request, HttpServletResponse response) {
        int status = statusCode(request);
        ErrorDescription description = descriptionFor(status);
        errorResponseWriter.write(request, response, status, description.code(), description.message());
    }

    private int statusCode(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusAttribute instanceof Integer status && status >= 400 && status <= 599) {
            return status;
        }
        return 500;
    }

    private ErrorDescription descriptionFor(int status) {
        return switch (status) {
            case 400 -> new ErrorDescription("BAD_REQUEST", "La richiesta non è valida");
            case 401 -> new ErrorDescription("UNAUTHORIZED", "Utente non autenticato");
            case 403 -> new ErrorDescription("ACCESS_DENIED", "Accesso negato");
            case 404 -> new ErrorDescription("RESOURCE_NOT_FOUND", "Risorsa non trovata");
            case 405 -> new ErrorDescription("METHOD_NOT_ALLOWED", "Metodo HTTP non supportato");
            case 406 -> new ErrorDescription("NOT_ACCEPTABLE", "Rappresentazione richiesta non disponibile");
            case 409 -> new ErrorDescription("CONFLICT", "La richiesta è in conflitto con lo stato corrente");
            case 410 -> new ErrorDescription("GONE", "Risorsa non disponibile");
            case 415 -> new ErrorDescription("UNSUPPORTED_MEDIA_TYPE", "Media type non supportato");
            default -> status >= 500
                    ? new ErrorDescription("INTERNAL_SERVER_ERROR", "Si è verificato un errore interno")
                    : new ErrorDescription("HTTP_ERROR", "La richiesta non è valida");
        };
    }

    private record ErrorDescription(String code, String message) {
    }
}
