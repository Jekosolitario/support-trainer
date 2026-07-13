package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.booking.service.BookingService;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

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
        ProfessionalProfile professional = mock(ProfessionalProfile.class);
        authenticate("professional@example.com");
        when(userRepository.findByEmail("professional@example.com")).thenReturn(Optional.of(professional));
        when(professional.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(professional.getEmailVerified()).thenReturn(true);
        when(professional.getActive()).thenReturn(true);
        when(professional.getSpecialization()).thenReturn(ProfessionalSpecialization.PERSONAL_TRAINER);
        when(professional.getId()).thenReturn(1L);
        when(professionalRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(professional));
        AvailabilityService service = new AvailabilityService(
                slotRepository,
                userRepository,
                professionalRepository,
                linkRepository,
                bookingItemRepository,
                fixedTimeProvider(),
                businessDateTimeMapper()
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
        ProfessionalClientLinkRepository linkRepository = mock(ProfessionalClientLinkRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ClientProfile client = mock(ClientProfile.class);
        ProfessionalProfile professional = mock(ProfessionalProfile.class);
        AvailabilitySlot slot = mock(AvailabilitySlot.class);
        authenticate("client@example.com");
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(client));
        when(client.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(client.getActive()).thenReturn(true);
        when(client.getId()).thenReturn(2L);
        when(slotRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(slot.getProfessional()).thenReturn(professional);
        when(slot.getStatus()).thenReturn(AvailabilitySlotStatus.AVAILABLE);
        when(slot.getStartDateTime()).thenReturn(FIXED_BUSINESS_DATE_TIME);
        when(professional.getId()).thenReturn(1L);
        when(professional.getActive()).thenReturn(true);
        when(professional.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(professional.getEmailVerified()).thenReturn(true);
        when(professional.getSpecialization()).thenReturn(ProfessionalSpecialization.PERSONAL_TRAINER);
        when(linkRepository.existsByProfessional_IdAndClient_IdAndActiveTrue(1L, 2L)).thenReturn(true);
        BookingService service = new BookingService(
                bookingRepository,
                bookingItemRepository,
                slotRepository,
                linkRepository,
                userRepository,
                fixedTimeProvider(),
                businessDateTimeMapper()
        );

        assertThatThrownBy(() -> service.createBookingRequest(new CreateBookingRequest(10L, null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo("AVAILABILITY_SLOT_NOT_BOOKABLE"));
    }

    private static void authenticate(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static ApplicationTimeProvider fixedTimeProvider() {
        TimeProperties properties = timeProperties();
        return new ApplicationTimeProvider(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), properties);
    }

    private static BusinessDateTimeMapper businessDateTimeMapper() {
        return new BusinessDateTimeMapper(timeProperties());
    }

    private static TimeProperties timeProperties() {
        return new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
    }

    private static OffsetDateTime businessOffset(LocalDateTime value) {
        return value.atZone(timeProperties().businessZone()).toOffsetDateTime();
    }
}
