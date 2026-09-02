package it.zuperman.support_trainer.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryConfirmRequest;
import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryRequest;
import it.zuperman.support_trainer.auth.dto.response.PasswordRecoveryAcceptedResponse;
import it.zuperman.support_trainer.auth.passwordrecovery.PasswordResetTokenGenerator;
import it.zuperman.support_trainer.auth.passwordrecovery.PasswordResetTokenHasher;
import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.auth.token.PasswordResetToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.email.event.PasswordRecoveryRequestedEvent;
import it.zuperman.support_trainer.security.password.BcryptPasswordPolicy;
import it.zuperman.support_trainer.security.session.UserSessionsPhysicalCleanupRequestedEvent;

@Service
public class PasswordRecoveryService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final UserReadinessValidator userReadinessValidator;

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetTokenGenerator tokenGenerator,
            PasswordEncoder passwordEncoder,
            ApplicationTimeProvider timeProvider,
            ApplicationEventPublisher eventPublisher,
            UserReadinessValidator userReadinessValidator
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.timeProvider = timeProvider;
        this.eventPublisher = eventPublisher;
        this.userReadinessValidator = userReadinessValidator;
    }

    @Transactional
    public PasswordRecoveryAcceptedResponse requestRecovery(PasswordRecoveryRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);

        if (!isEligibleForPasswordRecovery(user)) {
            return PasswordRecoveryAcceptedResponse.neutral();
        }

        Instant currentDateTime = timeProvider.nowInstant();
        List<PasswordResetToken> existingTokens
                = passwordResetTokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());

        if (isRequestCooldownActive(existingTokens, currentDateTime)) {
            return PasswordRecoveryAcceptedResponse.neutral();
        }

        List<PasswordResetToken> tokensToInvalidate = existingTokens.stream()
                .filter(token -> token.getConsumedAt() == null)
                .toList();
        tokensToInvalidate.forEach(token -> token.markConsumed(currentDateTime));
        if (!tokensToInvalidate.isEmpty()) {
            passwordResetTokenRepository.saveAllAndFlush(tokensToInvalidate);
        }

        String rawToken = tokenGenerator.generateRawToken();
        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                PasswordResetTokenHasher.sha256Hex(rawToken),
                currentDateTime.plus(TOKEN_TTL)
        );
        passwordResetTokenRepository.saveAndFlush(resetToken);

        eventPublisher.publishEvent(new PasswordRecoveryRequestedEvent(
                user.getEmail(),
                rawToken,
                resetToken.getExpiresAt(),
                UUID.randomUUID()
        ));

        return PasswordRecoveryAcceptedResponse.neutral();
    }

    @Transactional
    public void confirmRecovery(PasswordRecoveryConfirmRequest request) {
        String tokenHash = PasswordResetTokenHasher.sha256Hex(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidOrExpiredToken);

        if (resetToken.getConsumedAt() != null) {
            throw invalidOrExpiredToken();
        }

        Instant currentDateTime = timeProvider.nowInstant();
        if (resetToken.isExpired(currentDateTime)) {
            throw invalidOrExpiredToken();
        }

        User user = resetToken.getUser();
        if (!isEligibleForPasswordRecovery(user)) {
            throw invalidOrExpiredToken();
        }

        user.setPassword(encodePassword(request.getNewPassword()));
        resetToken.markConsumed(currentDateTime);
        user.incrementSessionVersion();

        List<PasswordResetToken> otherOpenTokens = passwordResetTokenRepository
                .findByUser_IdAndConsumedAtIsNull(user.getId())
                .stream()
                .filter(token -> !token.getId().equals(resetToken.getId()))
                .toList();
        otherOpenTokens.forEach(token -> token.markConsumed(currentDateTime));

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
        if (!otherOpenTokens.isEmpty()) {
            passwordResetTokenRepository.saveAll(otherOpenTokens);
        }

        eventPublisher.publishEvent(new UserSessionsPhysicalCleanupRequestedEvent(user.getId()));
    }

    private boolean isEligibleForPasswordRecovery(User user) {
        if (user == null) {
            return false;
        }
        try {
            userReadinessValidator.validateAuthenticationEligibility(user);
            return true;
        } catch (AppException ignored) {
            return false;
        }
    }

    private boolean isRequestCooldownActive(List<PasswordResetToken> existingTokens, Instant currentDateTime) {
        if (existingTokens.isEmpty()) {
            return false;
        }
        Instant cooldownEndsAt = existingTokens.get(0).getCreatedAt().plus(REQUEST_COOLDOWN);
        return currentDateTime.isBefore(cooldownEndsAt);
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

    private AppException invalidOrExpiredToken() {
        return new AppException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED",
                "Token di reimpostazione password non valido o scaduto"
        );
    }
}
