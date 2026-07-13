package it.zuperman.support_trainer.security.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BcryptCompatiblePasswordValidator
        implements ConstraintValidator<BcryptCompatiblePassword, CharSequence> {

    @Override
    public boolean isValid(CharSequence password, ConstraintValidatorContext context) {
        return BcryptPasswordPolicy.isWithinLimit(password);
    }
}
