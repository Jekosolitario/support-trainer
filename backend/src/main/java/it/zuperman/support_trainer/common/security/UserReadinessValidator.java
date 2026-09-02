package it.zuperman.support_trainer.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

/** Validates the runtime readiness of an already-loaded authenticated user. */
@Component
public class UserReadinessValidator {

    /**
     * Eligibility to establish or keep session authentication.
     * Does not inspect profile.active.
     */
    public void validateAuthenticationEligibility(User user) {
        validateAccountAndEmail(user);
    }

    public void validateAccountAndEmail(User user) {
        validateAccountAndEmail(user.getAccountStatus(), user.getEmailVerified());
    }

    public void validateAccountAndEmail(AccountStatus accountStatus, Boolean emailVerified) {
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Account non attivo"
            );
        }

        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "Email non verificata"
            );
        }
    }

    public void validateOperationalProfile(User user) {
        if (user instanceof ClientProfile clientProfile
                && !Boolean.TRUE.equals(clientProfile.getActive())) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "CLIENT_NOT_ACTIVE",
                    "Profilo cliente non attivo"
            );
        }

        if (user instanceof ProfessionalProfile professionalProfile
                && !Boolean.TRUE.equals(professionalProfile.getActive())) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_NOT_ACTIVE",
                    "Profilo professionista non attivo"
            );
        }
    }

    public void validateOperationalUser(User user) {
        validateAccountAndEmail(user);
        validateOperationalProfile(user);
    }
}
