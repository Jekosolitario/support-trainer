package it.zuperman.support_trainer.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import it.zuperman.support_trainer.common.response.ErrorResponse;
import it.zuperman.support_trainer.common.response.ErrorResponseFactory;
import it.zuperman.support_trainer.common.response.ErrorResponseWriter;
import it.zuperman.support_trainer.common.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "Si è verificato un errore interno";

    private final ErrorResponseFactory errorResponseFactory;
    private final ErrorResponseWriter errorResponseWriter;

    public GlobalExceptionHandler(
            ErrorResponseFactory errorResponseFactory,
            ErrorResponseWriter errorResponseWriter
    ) {
        this.errorResponseFactory = errorResponseFactory;
        this.errorResponseWriter = errorResponseWriter;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            LOGGER.error(
                    "Application error while handling request path={} code={}",
                    errorResponseFactory.requestPath(request),
                    ex.getErrorCode()
            );
            return response(request, ex.getStatus(), "INTERNAL_SERVER_ERROR", INTERNAL_ERROR_MESSAGE);
        }

        return response(request, ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        return validationResponse(request, ex.getBindingResult());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return response(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Il corpo della richiesta non è valido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return response(request, HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER", "Parametro obbligatorio mancante");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER", "Parametro della richiesta non valido");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return response(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Risorsa non trovata");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(errorResponseFactory.create(
                        request,
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED",
                        "Metodo HTTP non supportato"
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "Rappresentazione richiesta non disponibile"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (!ex.getSupportedMediaTypes().isEmpty()) {
            headers.setAccept(ex.getSupportedMediaTypes());
        }

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(headers)
                .body(errorResponseFactory.create(
                        request,
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Media type non supportato"
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponseFactory.create(
                        request,
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_ERROR",
                        "Credenziali non valide"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected exception while handling request path={} type={}",
                errorResponseFactory.requestPath(request),
                ex.getClass().getName(),
                ex
        );
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", INTERNAL_ERROR_MESSAGE);
    }

    private ResponseEntity<ErrorResponse> validationResponse(HttpServletRequest request, BindingResult bindingResult) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponseFactory.create(
                        request,
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "La richiesta contiene dati non validi",
                        toFieldErrors(bindingResult)
                ));
    }

    private List<FieldErrorResponse> toFieldErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(this::toFieldError)
                .toList();
    }

    private FieldErrorResponse toFieldError(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : null;
        return new FieldErrorResponse(field, error.getCode(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(errorResponseFactory.create(request, status, code, message));
    }
}
