package it.zuperman.support_trainer.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.jwt.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTimeTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-13T15:30:45Z");
    private static final LocalDateTime FIXED_BUSINESS_DATE_TIME = LocalDateTime.of(2026, 7, 13, 17, 30, 45);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfessionalProfileRepository professionalProfileRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private InviteCodeService inviteCodeService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                professionalProfileRepository,
                emailVerificationTokenRepository,
                clientProfileRepository,
                inviteCodeService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                professionalClientLinkRepository,
                fixedTimeProvider()
        );
    }

    @Test
    void shouldCreateEmailVerificationExpiryFromFixedClock() {
        RegisterProfessionalRequest request = new RegisterProfessionalRequest(
                "Mario",
                "Rossi",
                "mario.rossi@example.com",
                "Password1!",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        when(userRepository.findByEmail("mario.rossi@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
        when(professionalProfileRepository.saveAndFlush(any(ProfessionalProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.registerProfessional(request);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getExpiresAt()).isEqualTo(FIXED_BUSINESS_DATE_TIME.plusHours(24));
    }

    @Test
    void shouldConsumeEmailVerificationTokenAtFixedBusinessTime() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                "mario.rossi@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        EmailVerificationToken verificationToken = new EmailVerificationToken(
                professional,
                "verification-token",
                FIXED_BUSINESS_DATE_TIME.plusMinutes(1)
        );
        when(emailVerificationTokenRepository.findByTokenForUpdate("verification-token"))
                .thenReturn(Optional.of(verificationToken));

        authService.verifyEmail("verification-token");

        assertThat(verificationToken.getUsed()).isTrue();
        assertThat(verificationToken.getUsedAt()).isEqualTo(FIXED_BUSINESS_DATE_TIME);
        assertThat(professional.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(professional.getEmailVerified()).isTrue();
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        TimeProperties properties = new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
        return new ApplicationTimeProvider(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), properties);
    }
}
