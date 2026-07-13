package it.zuperman.support_trainer.invite.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InviteCodeServiceTimeTest {

    @Test
    void shouldCreateInviteExpiryFromFixedClock() {
        Instant fixedInstant = Instant.parse("2026-07-13T15:30:45Z");
        LocalDateTime fixedBusinessDateTime = LocalDateTime.of(2026, 7, 13, 17, 30, 45);
        InviteCodeRepository inviteCodeRepository = mock(InviteCodeRepository.class);
        ProfessionalProfileRepository professionalRepository = mock(ProfessionalProfileRepository.class);
        ProfessionalProfile professional = activeProfessional();
        when(professionalRepository.findByEmail("professional@example.com"))
                .thenReturn(Optional.of(professional));
        when(inviteCodeRepository.saveAndFlush(any(InviteCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InviteCodeService service = new InviteCodeService(
                inviteCodeRepository,
                professionalRepository,
                fixedTimeProvider(fixedInstant)
        );

        InviteCode inviteCode = service.createInviteCode("professional@example.com");

        assertThat(inviteCode.getExpiresAt()).isEqualTo(fixedBusinessDateTime.plusDays(7));
    }

    private static ProfessionalProfile activeProfessional() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                "professional@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setActive(true);
        professional.setEmailVerified(true);
        professional.setAccountStatus(AccountStatus.ACTIVE);
        return professional;
    }

    private static ApplicationTimeProvider fixedTimeProvider(Instant instant) {
        TimeProperties properties = new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
        return new ApplicationTimeProvider(Clock.fixed(instant, ZoneOffset.UTC), properties);
    }
}
