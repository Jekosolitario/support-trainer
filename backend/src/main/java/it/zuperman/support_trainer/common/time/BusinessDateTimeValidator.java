package it.zuperman.support_trainer.common.time;

import java.time.OffsetDateTime;

import it.zuperman.support_trainer.common.exception.AppException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BusinessDateTimeValidator implements ConstraintValidator<ValidBusinessDateTime, OffsetDateTime> {

    private final BusinessDateTimeMapper businessDateTimeMapper;

    public BusinessDateTimeValidator(BusinessDateTimeMapper businessDateTimeMapper) {
        this.businessDateTimeMapper = businessDateTimeMapper;
    }

    @Override
    public boolean isValid(OffsetDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            businessDateTimeMapper.validateRequestDateTime(value);
            return true;
        } catch (AppException exception) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(exception.getMessage())
                    .addConstraintViolation();
            return false;
        }
    }
}
