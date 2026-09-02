package it.zuperman.support_trainer.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterClientRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.dto.response.RegistrationAcceptedResponse;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.email.event.EmailVerificationRequestedEvent;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.security.password.BcryptPasswordPolicy;
import it.zuperman.support_trainer.security.service.AuthenticationUserDetails;
import it.zuperman.support_trainer.security.session.SessionLoginIdentity;

@Service
public class AuthService {

    private static final Duration EMAIL_VERIFICATION_TOKEN_VALIDITY = Duration.ofHours(24);
    private static final Duration EMAIL_VERIFICATION_RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ApplicationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final RegistrationPersistenceService registrationPersistenceService;
    private final UserReadinessValidator userReadinessValidator;

    public AuthService(
            UserRepository userRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            ApplicationTimeProvider timeProvider,
            ApplicationEventPublisher eventPublisher,
            RegistrationPersistenceService registrationPersistenceService,
            UserReadinessValidator userReadinessValidator
    ) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.timeProvider = timeProvider;
        this.eventPublisher = eventPublisher;
        this.registrationPersistenceService = registrationPersistenceService;
        this.userReadinessValidator = userReadinessValidator;
    }

    public RegistrationAcceptedResponse registerProfessional(RegisterProfessionalRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        ProfessionalProfile professional = new ProfessionalProfile(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                encodePassword(request.getPassword()),
                request.getSpecialization()
        );

        try {
            registrationPersistenceService.registerProfessional(professional);
        } catch (DataIntegrityViolationException ex) {
            return handleEmailUniqueCollision(ex);
        }

        return RegistrationAcceptedResponse.neutral();
    }

    public RegistrationAcceptedResponse registerClient(RegisterClientRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        ClientProfile client = new ClientProfile(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                encodePassword(request.getPassword()),
                request.getBirthDate(),
                request.getHeightCm(),
                request.getPrimaryGoal().trim(),
                request.getGender()
        );

        client.setMedicalNotes(normalizeOptionalText(request.getMedicalNotes()));
        client.setInjuryNotes(normalizeOptionalText(request.getInjuryNotes()));
        client.setNotes(normalizeOptionalText(request.getNotes()));

        client.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        client.setEmailVerified(false);

        try {
            registrationPersistenceService.registerClient(client, request.getInviteCode());
        } catch (DataIntegrityViolationException ex) {
            return handleEmailUniqueCollision(ex);
        }

        return RegistrationAcceptedResponse.neutral();
    }

    private EmailVerificationToken createEmailVerificationToken(User user, Instant issuedAt) {
        EmailVerificationToken verificationToken = new EmailVerificationToken(
                user,
                UUID.randomUUID().toString(),
                issuedAt.plus(EMAIL_VERIFICATION_TOKEN_VALIDITY)
        );

        return emailVerificationTokenRepository.save(verificationToken);
    }

    private void publishEmailVerificationRequested(
            User user,
            EmailVerificationToken verificationToken,
            EmailVerificationReason reason
    ) {
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
                user.getEmail(),
                verificationToken.getToken(),
                verificationToken.getExpiresAt(),
                reason,
                UUID.randomUUID()
        ));
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "EMAIL_VERIFICATION_TOKEN_NOT_FOUND",
                "Token di verifica non valido"
        ));

        User user = verificationToken.getUser();

        if (Boolean.TRUE.equals(verificationToken.getUsed())) {
            if (isVerificationStateCoherent(user)) {
                return;
            }

            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_VERIFICATION_TOKEN_ALREADY_USED",
                    "Token di verifica già utilizzato"
            );
        }

        Instant currentDateTime = timeProvider.nowInstant();

        if (verificationToken.isExpired(currentDateTime)) {
            throw new AppException(
                    HttpStatus.GONE,
                    "EMAIL_VERIFICATION_TOKEN_EXPIRED",
                    "Token di verifica scaduto"
            );
        }

        validateProfileIsActive(user);

        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);

        verificationToken.markAsUsed(currentDateTime);

        userRepository.save(user);
        emailVerificationTokenRepository.save(verificationToken);
    }

    @Transactional
    public void resendEmailVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);

        if (!isEligibleForEmailVerificationResend(user)) {
            return;
        }

        Instant currentDateTime = timeProvider.nowInstant();
        List<EmailVerificationToken> existingTokens
                = emailVerificationTokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());

        if (isEmailVerificationResendCooldownActive(existingTokens, currentDateTime)) {
            return;
        }

        List<EmailVerificationToken> tokensToInvalidate = existingTokens.stream()
                .filter(token -> Boolean.FALSE.equals(token.getUsed()))
                .toList();

        tokensToInvalidate.forEach(token -> token.markAsUsed(currentDateTime));
        if (!tokensToInvalidate.isEmpty()) {
            emailVerificationTokenRepository.saveAllAndFlush(tokensToInvalidate);
        }

        EmailVerificationToken verificationToken = createEmailVerificationToken(user, currentDateTime);
        publishEmailVerificationRequested(user, verificationToken, EmailVerificationReason.RESEND);
    }

    private boolean isEligibleForEmailVerificationResend(User user) {
        return user != null
                && user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION
                && Boolean.FALSE.equals(user.getEmailVerified())
                && isProfileActive(user);
    }

    private boolean isEmailVerificationResendCooldownActive(
            List<EmailVerificationToken> existingTokens,
            Instant currentDateTime
    ) {
        if (existingTokens.isEmpty()) {
            return false;
        }

        Instant cooldownEndsAt = existingTokens.get(0).getCreatedAt()
                .plus(EMAIL_VERIFICATION_RESEND_COOLDOWN);
        return currentDateTime.isBefore(cooldownEndsAt);
    }

    private boolean isVerificationStateCoherent(User user) {
        return Boolean.TRUE.equals(user.getEmailVerified())
                && user.getAccountStatus() == AccountStatus.ACTIVE
                && isProfileActive(user);
    }

    private void validateProfileIsActive(User user) {
        if (isProfileActive(user)) {
            return;
        }

        if (user instanceof ProfessionalProfile) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_NOT_ACTIVE",
                    "Profilo professionista non attivo"
            );
        }

        throw new AppException(
                HttpStatus.FORBIDDEN,
                "CLIENT_NOT_ACTIVE",
                "Profilo cliente non attivo"
        );
    }

    private boolean isProfileActive(User user) {
        if (user instanceof ProfessionalProfile professionalProfile) {
            return Boolean.TRUE.equals(professionalProfile.getActive());
        }

        if (user instanceof ClientProfile clientProfile) {
            return Boolean.TRUE.equals(clientProfile.getActive());
        }

        return false;
    }

    /**
     * Verifies credentials and authentication eligibility. Does not touch HTTP or session infrastructure.
     */
    public SessionLoginIdentity authenticateForSession(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (!BcryptPasswordPolicy.isWithinLimit(request.getPassword())) {
            throw new BadCredentialsException("Credenziali non valide");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        if (!(authentication.getPrincipal() instanceof AuthenticationUserDetails snapshot)) {
            throw new IllegalStateException("Authenticated principal is not an authentication snapshot");
        }

        userReadinessValidator.validateAccountAndEmail(
                snapshot.getAccountStatus(),
                snapshot.getEmailVerified()
        );

        return new SessionLoginIdentity(
                snapshot.getUserId(),
                snapshot.getEmail(),
                snapshot.getRole(),
                snapshot.getSessionVersion()
        );
    }

    private RegistrationAcceptedResponse handleEmailUniqueCollision(DataIntegrityViolationException ex) {
        if (isUsersEmailUniqueViolation(ex)) {
            return RegistrationAcceptedResponse.neutral();
        }

        throw ex;
    }

    private boolean isUsersEmailUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        boolean structuredConstraintNameFound = false;
        boolean usersEmailConstraintFound = false;
        boolean differentStructuredConstraintFound = false;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (constraintName != null && !constraintName.isBlank()) {
                    structuredConstraintNameFound = true;
                    if (isUsersEmailConstraintName(constraintName)) {
                        usersEmailConstraintFound = true;
                    } else {
                        differentStructuredConstraintFound = true;
                    }
                }
            }
            cause = cause.getCause();
        }

        if (structuredConstraintNameFound) {
            return usersEmailConstraintFound && !differentStructuredConstraintFound;
        }

        return containsUsersEmailConstraintInMessage(ex);
    }

    private boolean isUsersEmailConstraintName(String constraintName) {
        String normalizedConstraintName = constraintName.trim()
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("[", "")
                .replace("]", "");
        int qualifierSeparator = normalizedConstraintName.lastIndexOf('.');
        String unqualifiedConstraintName = qualifierSeparator >= 0
                ? normalizedConstraintName.substring(qualifierSeparator + 1)
                : normalizedConstraintName;

        return "uk_users_email".equalsIgnoreCase(unqualifiedConstraintName.trim());
    }

    private boolean containsUsersEmailConstraintInMessage(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("uk_users_email")) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    private String encodePassword(String password) {
        if (!BcryptPasswordPolicy.isWithinLimit(password)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    BcryptPasswordPolicy.MAX_LENGTH_MESSAGE
            );
        }

        return passwordEncoder.encode(password);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }
}
