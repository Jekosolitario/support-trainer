package it.zuperman.support_trainer.invite.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@Service
public class InviteCodeService {

    private static final int INVITE_CODE_VALIDITY_DAYS = 7;

    private final InviteCodeRepository inviteCodeRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;

    public InviteCodeService(
            InviteCodeRepository inviteCodeRepository,
            ProfessionalProfileRepository professionalProfileRepository
    ) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.professionalProfileRepository = professionalProfileRepository;
    }

    @Transactional
    public InviteCode createInviteCode(String professionalEmail) {
        ProfessionalProfile professional = getVerifiedActiveProfessional(professionalEmail);

        String code = buildReadableCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITE_CODE_VALIDITY_DAYS);

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

        if (!inviteCode.getExpiresAt().isAfter(LocalDateTime.now())) {
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

        if (!Boolean.TRUE.equals(professional.getActive())) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_NOT_ACTIVE",
                    "Il profilo professionista non è attivo"
            );
        }

        if (!Boolean.TRUE.equals(professional.getEmailVerified())) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "Devi verificare l'email prima di generare codici invito"
            );
        }

        if (professional.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "L'account non è attivo"
            );
        }

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

        if (!inviteCode.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVITE_CODE_EXPIRED",
                    "Codice invito scaduto"
            );
        }

        return inviteCode;
    }
}
