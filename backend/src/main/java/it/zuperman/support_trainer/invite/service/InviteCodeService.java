package it.zuperman.support_trainer.invite.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@Service
public class InviteCodeService {

    private static final Duration INVITE_CODE_VALIDITY = Duration.ofHours(168);

    private final InviteCodeRepository inviteCodeRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ApplicationTimeProvider timeProvider;
    private final UserReadinessValidator userReadinessValidator;

    public InviteCodeService(
            InviteCodeRepository inviteCodeRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ApplicationTimeProvider timeProvider,
            UserReadinessValidator userReadinessValidator
    ) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.timeProvider = timeProvider;
        this.userReadinessValidator = userReadinessValidator;
    }

    @Transactional
    public InviteCode createInviteCode(String professionalEmail) {
        ProfessionalProfile professional = getVerifiedActiveProfessional(professionalEmail);

        String code = buildReadableCode();
        Instant expiresAt = timeProvider.nowInstant().plus(INVITE_CODE_VALIDITY);

        InviteCode inviteCode = new InviteCode(code, professional, expiresAt);

        try {
            return inviteCodeRepository.saveAndFlush(inviteCode);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "INVITE_CODE_GENERATION_FAILED",
                    "Impossibile generare un codice invito univoco"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<InviteCode> getInviteCodesByProfessional(String professionalEmail) {
        ProfessionalProfile professional = getVerifiedActiveProfessional(professionalEmail);

        return inviteCodeRepository.findAllByProfessional_IdOrderByCreatedAtDesc(professional.getId());
    }

    @Transactional(readOnly = true)
    public InviteCode validateInviteCode(String code) {
        String normalizedCode = normalizeInviteCode(code);

        InviteCode inviteCode = inviteCodeRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "INVITE_CODE_NOT_FOUND",
                "Codice invito non valido"
        ));

        validateInviteCodeState(inviteCode);
        validateInviteProfessionalState(inviteCode.getProfessional());

        return inviteCode;
    }

    private void validateInviteCodeState(InviteCode inviteCode) {
        if (!Boolean.TRUE.equals(inviteCode.getActive())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_NOT_ACTIVE",
                    "Codice invito non attivo"
            );
        }

        if (Boolean.TRUE.equals(inviteCode.getUsed())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_ALREADY_USED",
                    "Codice invito già utilizzato"
            );
        }

        if (!inviteCode.getExpiresAt().isAfter(timeProvider.nowInstant())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_EXPIRED",
                    "Codice invito scaduto"
            );
        }
    }

    private void validateInviteProfessionalState(ProfessionalProfile professional) {
        if (!Boolean.TRUE.equals(professional.getActive())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_NOT_ACTIVE",
                    "Codice invito non attivo"
            );
        }

        if (!Boolean.TRUE.equals(professional.getEmailVerified())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_NOT_ACTIVE",
                    "Codice invito non attivo"
            );
        }

        if (professional.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_NOT_ACTIVE",
                    "Codice invito non attivo"
            );
        }
    }

    private ProfessionalProfile getVerifiedActiveProfessional(String professionalEmail) {
        ProfessionalProfile professional = professionalProfileRepository.findByEmail(professionalEmail)
                .orElseThrow(() -> new AppException(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN_OPERATION",
                "Solo i professionisti possono usare questa funzionalità"
        ));

        userReadinessValidator.validateOperationalUser(professional);

        return professional;
    }

    private String buildReadableCode() {
        String rawCode = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();

        return "INV-" + rawCode;
    }

    private String normalizeInviteCode(String code) {
        return code.trim().toUpperCase();
    }

    public InviteCode validateInviteCodeForRegistration(String rawCode) {
        String normalizedCode = rawCode.trim().toUpperCase();

        InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "INVITE_CODE_NOT_FOUND",
                "Codice invito non trovato"
        ));

        if (!inviteCode.getActive()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_NOT_ACTIVE",
                    "Codice invito non attivo"
            );
        }

        if (inviteCode.getUsed()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_ALREADY_USED",
                    "Codice invito già utilizzato"
            );
        }

        if (!inviteCode.getExpiresAt().isAfter(timeProvider.nowInstant())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_EXPIRED",
                    "Codice invito scaduto"
            );
        }

        return inviteCode;
    }
}
