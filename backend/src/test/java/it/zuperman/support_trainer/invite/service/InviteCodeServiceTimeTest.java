package it.zuperman.support_trainer.invite.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
        assertInviteValidity(fixedInstant);
    }

    @Test
    void shouldKeepExactOneHundredSixtyEightHourValidityAcrossSpringDstChange() {
        assertInviteValidity(Instant.parse("2026-03-25T12:00:00Z"));
    }

    @Test
    void shouldKeepExactOneHundredSixtyEightHourValidityAcrossAutumnDstChange() {
        assertInviteValidity(Instant.parse("2026-10-21T12:00:00Z"));
    }

    private static void assertInviteValidity(Instant fixedInstant) {
        InviteCodeRepository inviteCodeRepository = mock(InviteCodeRepository.class);
        ProfessionalProfileRepository professionalRepository = mock(ProfessionalProfileRepository.class);
        ProfessionalProfile professional = activeProfessional();
        when(professionalRepository.findById(42L)).thenReturn(Optional.of(professional));
        when(inviteCodeRepository.saveAndFlush(any(InviteCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InviteCodeService service = new InviteCodeService(
                inviteCodeRepository,
                professionalRepository,
                fixedTimeProvider(fixedInstant),
                new it.zuperman.support_trainer.common.security.UserReadinessValidator()
        );

        InviteCode inviteCode = service.createInviteCode(42L);

        assertThat(inviteCode.getExpiresAt()).isEqualTo(fixedInstant.plus(Duration.ofHours(168)));
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
