package it.zuperman.support_trainer.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.dto.response.RegistrationAcceptedResponse;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

class AuthServiceRegistrationNeutralTest {

    private final RegistrationPersistenceService registrationPersistenceService =
            mock(RegistrationPersistenceService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService authService = new AuthService(
            mock(UserRepository.class),
            mock(EmailVerificationTokenRepository.class),
            passwordEncoder,
            mock(AuthenticationManager.class),
            mock(ApplicationTimeProvider.class),
            mock(ApplicationEventPublisher.class),
            registrationPersistenceService,
            new UserReadinessValidator()
    );

    @Test
    void shouldReturnNeutralResponseForStructuredUsersEmailUniqueConstraint() {
        RegistrationAcceptedResponse response = registerProfessionalWith(
                structuredConstraintViolation("uk_users_email")
        );

        assertThat(response.message()).isEqualTo(RegistrationAcceptedResponse.NEUTRAL_MESSAGE);
    }

    @Test
    void shouldReturnNeutralResponseForCaseInsensitiveQualifiedUsersEmailConstraint() {
        RegistrationAcceptedResponse response = registerProfessionalWith(
                structuredConstraintViolation("`support_trainer`.`UK_USERS_EMAIL`")
        );

        assertThat(response.message()).isEqualTo(RegistrationAcceptedResponse.NEUTRAL_MESSAGE);
    }

    @Test
    void shouldPropagateDifferentStructuredConstraintViolation() {
        DataIntegrityViolationException differentConstraint = structuredConstraintViolation("uk_invite_code");

        assertThatThrownBy(() -> registerProfessionalWith(differentConstraint))
                .isSameAs(differentConstraint);
    }

    @Test
    void shouldUseMessageFallbackWhenNoStructuredConstraintNameIsAvailable() {
        RegistrationAcceptedResponse response = registerProfessionalWith(
                new DataIntegrityViolationException("Duplicate key uk_users_email")
        );

        assertThat(response.message()).isEqualTo(RegistrationAcceptedResponse.NEUTRAL_MESSAGE);
    }

    @Test
    void shouldPropagateExceptionWithoutUsersEmailConstraintName() {
        DataIntegrityViolationException unrelatedFailure =
                new DataIntegrityViolationException("Foreign key constraint failed");

        assertThatThrownBy(() -> registerProfessionalWith(unrelatedFailure))
                .isSameAs(unrelatedFailure);
    }

    private RegistrationAcceptedResponse registerProfessionalWith(DataIntegrityViolationException failure) {
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        doThrow(failure)
                .when(registrationPersistenceService)
                .registerProfessional(any(ProfessionalProfile.class));

        return authService.registerProfessional(request());
    }

    private DataIntegrityViolationException structuredConstraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "Database constraint violation",
                new ConstraintViolationException(
                        "Constraint violation",
                        new SQLException("Duplicate key"),
                        constraintName
                )
        );
    }

    private RegisterProfessionalRequest request() {
        return new RegisterProfessionalRequest(
                "Mario",
                "Rossi",
                "mario.rossi@example.com",
                "Password123!",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
    }
}
