package it.zuperman.support_trainer.common.response;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ErrorResponseFactory {

    private static final Comparator<FieldErrorResponse> FIELD_ERROR_ORDER = Comparator
            .comparing((FieldErrorResponse error) -> error.field() == null)
            .thenComparing(FieldErrorResponse::field, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FieldErrorResponse::code, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FieldErrorResponse::message, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ApplicationTimeProvider timeProvider;

    public ErrorResponseFactory(ApplicationTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public ErrorResponse create(
            HttpServletRequest request,
            HttpStatusCode status,
            String code,
            String message
    ) {
        return create(request, status.value(), code, message, null);
    }

    public ErrorResponse create(
            HttpServletRequest request,
            HttpStatusCode status,
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors
    ) {
        return create(request, status.value(), code, message, fieldErrors);
    }

    public ErrorResponse create(
            HttpServletRequest request,
            int status,
            String code,
            String message
    ) {
        return create(request, status, code, message, null);
    }

    public ErrorResponse create(
            HttpServletRequest request,
            int status,
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors
    ) {
        List<FieldErrorResponse> orderedFieldErrors = fieldErrors == null ? null : fieldErrors.stream()
                .sorted(FIELD_ERROR_ORDER)
                .toList();

        return new ErrorResponse(
                timeProvider.nowInstant(),
                status,
                code,
                message,
                requestPath(request),
                orderedFieldErrors
        );
    }

    public String requestPath(HttpServletRequest request) {
        Object originalRequestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalRequestUri instanceof String originalPath && !originalPath.isBlank()) {
            return originalPath;
        }

        String requestUri = request.getRequestURI();
        return requestUri == null || requestUri.isBlank() ? "/error" : requestUri;
    }
}
