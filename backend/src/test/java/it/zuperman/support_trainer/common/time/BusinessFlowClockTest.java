package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotChangeRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityCapacityService;
import it.zuperman.support_trainer.availability.service.AvailabilityMaterializationService;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.mapper.BookingResponseMapper;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.booking.service.BookingService;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.AuthenticatedUserIdResolver;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;
import it.zuperman.support_trainer.security.session.AuthenticatedUserPrincipal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessFlowClockTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-13T15:30:45Z");
    private static final LocalDateTime FIXED_BUSINESS_DATE_TIME = LocalDateTime.of(2026, 7, 13, 17, 30, 45);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectAvailabilityStartingAtFixedPresent() {
        AvailabilitySlotRepository slotRepository = mock(AvailabilitySlotRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProfessionalProfileRepository professionalRepository = mock(ProfessionalProfileRepository.class);
        ProfessionalClientLinkRepository linkRepository = mock(ProfessionalClientLinkRepository.class);
        BookingRequestItemRepository bookingItemRepository = mock(BookingRequestItemRepository.class);
        WeeklyAvailabilityRuleRepository weeklyRuleRepository = mock(WeeklyAvailabilityRuleRepository.class);
        ProfessionalProfile professional = mock(ProfessionalProfile.class);
        authenticate(1L, "professional@example.com", "PROFESSIONAL");
        when(userRepository.findById(1L)).thenReturn(Optional.of(professional));
        when(professional.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(professional.getEmailVerified()).thenReturn(true);
        when(professional.getActive()).thenReturn(true);
        when(professional.getSpecialization()).thenReturn(ProfessionalSpecialization.PERSONAL_TRAINER);
        when(professional.getId()).thenReturn(1L);
        when(professionalRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(professional));
        when(weeklyRuleRepository.lockProfessionalAvailability(1L)).thenReturn(Optional.of(1L));
        when(professionalRepository.findById(1L)).thenReturn(Optional.of(professional));
        AvailabilityService service = new AvailabilityService(
                slotRepository,
                mock(AvailabilitySlotChangeRepository.class),
                authenticatedUserLoader(userRepository),
                professionalRepository,
                linkRepository,
                bookingItemRepository,
                fixedTimeProvider(),
                businessDateTimeMapper(),
                new it.zuperman.support_trainer.common.security.UserReadinessValidator(),
                weeklyRuleRepository,
                mock(AvailabilityMaterializationService.class),
                mock(AvailabilityCapacityService.class)
        );
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                businessOffset(FIXED_BUSINESS_DATE_TIME),
                businessOffset(FIXED_BUSINESS_DATE_TIME.plusHours(1))
        );

        assertThatThrownBy(() -> service.createAvailabilitySlot(request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo("AVAILABILITY_SLOT_IN_PAST"));
    }

    @Test
    void shouldRejectBookingForSlotStartingAtFixedPresent() {
        BookingRequestRepository bookingRepository = mock(BookingRequestRepository.class);
        BookingRequestItemRepository bookingItemRepository = mock(BookingRequestItemRepository.class);
        AvailabilitySlotRepository slotRepository = mock(AvailabilitySlotRepository.class);
        ClientProfileRepository clientRepository = mock(ClientProfileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientProfile client = mock(ClientProfile.class);
        ProfessionalProfile professional = mock(ProfessionalProfile.class);
        AvailabilitySlot slot = mock(AvailabilitySlot.class);
        authenticate(2L, "client@example.com", "CLIENT");
        when(userRepository.findById(2L)).thenReturn(Optional.of(client));
        when(client.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(client.getEmailVerified()).thenReturn(true);
        when(client.getActive()).thenReturn(true);
        when(client.getId()).thenReturn(2L);
        when(clientRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(client));
        when(slotRepository.findActiveAccessibleByIdAndClientIdForUpdate(
                10L,
                2L,
                AccountStatus.ACTIVE
        )).thenReturn(Optional.of(slot));
        when(slot.getProfessional()).thenReturn(professional);
        when(slot.getStatus()).thenReturn(AvailabilitySlotStatus.AVAILABLE);
        when(slot.getStartDateTime()).thenReturn(FIXED_INSTANT);
        when(professional.getId()).thenReturn(1L);
        when(professional.getActive()).thenReturn(true);
        when(professional.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(professional.getEmailVerified()).thenReturn(true);
        when(professional.getSpecialization()).thenReturn(ProfessionalSpecialization.PERSONAL_TRAINER);
        BookingService service = new BookingService(
                bookingRepository,
                bookingItemRepository,
                slotRepository,
                clientRepository,
                authenticatedUserLoader(userRepository),
                fixedTimeProvider(),
                bookingResponseMapper(),
                new it.zuperman.support_trainer.common.security.UserReadinessValidator(),
                businessDateTimeMapper(),
                mock(AvailabilityCapacityService.class)
        );

        assertThatThrownBy(() -> service.createBookingRequest(new CreateBookingRequest(
                10L,
                FIXED_INSTANT.atOffset(ZoneOffset.UTC),
                60,
                null
        )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo("AVAILABILITY_SLOT_NOT_BOOKABLE"));
    }

    private static void authenticate(Long userId, String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(userId, email),
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    private static AuthenticatedUserLoader authenticatedUserLoader(UserRepository userRepository) {
        return new AuthenticatedUserLoader(new AuthenticatedUserIdResolver(), userRepository);
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        TimeProperties properties = timeProperties();
        return new ApplicationTimeProvider(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), properties);
    }

    private static BusinessDateTimeMapper businessDateTimeMapper() {
        return new BusinessDateTimeMapper(timeProperties());
    }

    private static BookingResponseMapper bookingResponseMapper() {
        return new BookingResponseMapper(businessDateTimeMapper());
    }

    private static TimeProperties timeProperties() {
        return new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
    }

    private static OffsetDateTime businessOffset(LocalDateTime value) {
        return value.atZone(timeProperties().businessZone()).toOffsetDateTime();
    }
}
