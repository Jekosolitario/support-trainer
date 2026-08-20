package it.zuperman.support_trainer.common.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import it.zuperman.support_trainer.common.response.FieldErrorResponse;

public class AppValidationException extends AppException {

    private static final String VALIDATION_MESSAGE = "La richiesta contiene dati non validi";

    private final List<FieldErrorResponse> fieldErrors;

    public AppValidationException(List<FieldErrorResponse> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_MESSAGE);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static AppValidationException field(String field, String code, String message) {
        return new AppValidationException(List.of(new FieldErrorResponse(field, code, message)));
    }

    public List<FieldErrorResponse> getFieldErrors() {
        return fieldErrors;
    }
}
