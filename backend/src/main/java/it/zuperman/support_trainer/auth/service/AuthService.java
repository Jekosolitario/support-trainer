package it.zuperman.support_trainer.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterClientRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.dto.response.AuthResponse;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.jwt.JwtService;

@Service
public class AuthService {

    private static final long EMAIL_VERIFICATION_TOKEN_DURATION_HOURS = 24L;

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ClientProfileRepository clientProfileRepository;
    private final InviteCodeService inviteCodeService;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;

    public AuthService(
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            ClientProfileRepository clientProfileRepository,
            InviteCodeService inviteCodeService,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            ProfessionalClientLinkRepository professionalClientLinkRepository
    ) {
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.inviteCodeService = inviteCodeService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
    }

    @Transactional
    public AuthResponse registerProfessional(RegisterProfessionalRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "Email già registrata"
            );
        }

        ProfessionalProfile professional = new ProfessionalProfile(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getSpecialization()
        );

        ProfessionalProfile savedProfessional;
        try {
            savedProfessional = professionalProfileRepository.saveAndFlush(professional);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "Email già registrata"
            );
        }

        EmailVerificationToken verificationToken = new EmailVerificationToken(
                savedProfessional,
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusHours(EMAIL_VERIFICATION_TOKEN_DURATION_HOURS)
        );

        emailVerificationTokenRepository.save(verificationToken);

        return buildRegistrationResponse(savedProfessional);
    }

    @Transactional
    public AuthResponse registerClient(RegisterClientRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "Email già registrata"
            );
        }

        InviteCode inviteCode = inviteCodeService.validateInviteCodeForRegistration(request.getInviteCode());
        ProfessionalProfile professional = inviteCode.getProfessional();

        ClientProfile client = new ClientProfile(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getBirthDate(),
                request.getHeightCm(),
                request.getPrimaryGoal().trim(),
                request.getGender()
        );

        client.setMedicalNotes(normalizeOptionalText(request.getMedicalNotes()));
        client.setInjuryNotes(normalizeOptionalText(request.getInjuryNotes()));
        client.setNotes(normalizeOptionalText(request.getNotes()));

        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);

        ClientProfile savedClient;
        try {
            savedClient = clientProfileRepository.saveAndFlush(client);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_REGISTERED",
                    "Email già registrata"
            );
        }

        createProfessionalClientLink(professional, savedClient);

        inviteCode.setUsed(true);
        inviteCode.setUsedAt(LocalDateTime.now());

        return buildRegistrationResponse(savedClient);
    }

    private void createProfessionalClientLink(ProfessionalProfile professional, ClientProfile client) {
        if (professional.getId().equals(client.getId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SELF_LINK_NOT_ALLOWED",
                    "Non è possibile creare un collegamento verso se stessi"
            );
        }

        boolean activeLinkAlreadyExists
                = professionalClientLinkRepository.existsByProfessional_IdAndClient_IdAndActiveTrue(
                        professional.getId(),
                        client.getId()
                );

        if (activeLinkAlreadyExists) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "PROFESSIONAL_CLIENT_LINK_ALREADY_EXISTS",
                    "Esiste già un collegamento attivo tra professionista e cliente"
            );
        }

        long activeProfessionalCount
                = professionalClientLinkRepository.countByClient_IdAndActiveTrue(client.getId());

        if (activeProfessionalCount >= 3) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "CLIENT_MAX_PROFESSIONALS_REACHED",
                    "Il cliente ha già raggiunto il numero massimo di professionisti attivi"
            );
        }

        ProfessionalClientLink link = new ProfessionalClientLink(professional, client);
        professionalClientLinkRepository.save(link);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "EMAIL_VERIFICATION_TOKEN_NOT_FOUND",
                "Token di verifica non valido"
        ));

        if (Boolean.TRUE.equals(verificationToken.getUsed())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_VERIFICATION_TOKEN_ALREADY_USED",
                    "Token di verifica già utilizzato"
            );
        }

        if (verificationToken.isExpired()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_VERIFICATION_TOKEN_EXPIRED",
                    "Token di verifica scaduto"
            );
        }

        User user = verificationToken.getUser();

        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);

        verificationToken.markAsUsed();

        userRepository.save(user);
        emailVerificationTokenRepository.save(verificationToken);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Utente non trovato"
        ));

        validateLoginAccess(user);

        UserDetails userDetails = buildUserDetails(user);

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    private void validateLoginAccess(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Account non ancora attivo"
            );
        }

        if (user instanceof ProfessionalProfile professionalProfile) {
            if (Boolean.FALSE.equals(user.getEmailVerified())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "EMAIL_NOT_VERIFIED",
                        "Email non ancora verificata"
                );
            }

            if (Boolean.FALSE.equals(professionalProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "PROFESSIONAL_NOT_ACTIVE",
                        "Profilo professionista non attivo"
                );
            }
        }

        if (user instanceof ClientProfile clientProfile) {
            if (Boolean.FALSE.equals(clientProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "CLIENT_NOT_ACTIVE",
                        "Profilo cliente non attivo"
                );
            }
        }
    }

    private AuthResponse buildRegistrationResponse(User user) {
        return new AuthResponse(
                null,
                null,
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private UserDetails buildUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRole().name())
                .build();
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
