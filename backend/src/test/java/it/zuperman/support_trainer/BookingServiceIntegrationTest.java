package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.booking.service.BookingService;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.AuthenticatedUserPrincipal;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private WeeklyAvailabilityRuleRepository weeklyAvailabilityRuleRepository;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private BookingRequestRepository bookingRequestRepository;

    @Autowired
    private BookingRequestItemRepository bookingRequestItemRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Cliente collegato deve creare una richiesta booking su slot disponibile")
    void shouldCreateBookingRequestForAvailableSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(13);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, " Vorrei prenotare questo slot. ");

        BookingDetailResponse response = bookingService.createBookingRequest(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getClient().getId()).isEqualTo(client.getId());
        assertThat(response.getProfessional().getId()).isEqualTo(professional.getId());
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getNote()).isEqualTo("Vorrei prenotare questo slot.");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAvailabilitySlotId()).isEqualTo(slot.getId());
        assertThat(response.getItems().get(0).getAvailabilitySlotId()).isEqualTo(slot.getId());
    }

    @Test
    @DisplayName("Cliente e professionista devono vedere solo le proprie richieste booking")
    void shouldReturnOnlyAuthenticatedUserBookingRequests() {
        ProfessionalProfile professionalA = createActivePersonalTrainer();
        ClientProfile clientA = createActiveClient();
        ProfessionalProfile professionalB = createActivePersonalTrainer();
        ClientProfile clientB = createActiveClient();

        professionalClientLinkRepository.save(new ProfessionalClientLink(professionalA, clientA));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professionalB, clientB));

        LocalDateTime startDateTimeA = futureStart(30);
        AvailabilitySlot slotA = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalA, asBusinessInstant(startDateTimeA), asBusinessInstant(startDateTimeA.plusHours(1)))
        );

        LocalDateTime startDateTimeB = futureStart(31);
        AvailabilitySlot slotB = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalB, asBusinessInstant(startDateTimeB), asBusinessInstant(startDateTimeB.plusHours(1)))
        );

        authenticateAs(clientA);
        BookingDetailResponse bookingA = bookingService.createBookingRequest(
                bookingRequestFor(slotA, "Richiesta coppia A.")
        );

        authenticateAs(clientB);
        BookingDetailResponse bookingB = bookingService.createBookingRequest(
                bookingRequestFor(slotB, "Richiesta coppia B.")
        );

        authenticateAs(clientA);
        List<Long> clientBookingIds = bookingService.getClientBookingRequests().stream()
                .map(response -> response.getId())
                .toList();

        assertThat(clientBookingIds)
                .containsExactly(bookingA.getId())
                .doesNotContain(bookingB.getId());

        authenticateAs(professionalA);
        List<Long> professionalBookingIds = bookingService.getProfessionalBookingRequests().stream()
                .map(response -> response.getId())
                .toList();

        assertThat(professionalBookingIds)
                .containsExactly(bookingA.getId())
                .doesNotContain(bookingB.getId());
    }

    @Test
    @DisplayName("Utenti estranei non devono modificare richieste booking altrui")
    void shouldNotMutateBookingRequestOwnedByAnotherPair() {
        ProfessionalProfile professionalA = createActivePersonalTrainer();
        ClientProfile clientA = createActiveClient();
        ProfessionalProfile professionalB = createActivePersonalTrainer();
        ClientProfile clientB = createActiveClient();

        professionalClientLinkRepository.save(new ProfessionalClientLink(professionalA, clientA));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professionalB, clientB));

        LocalDateTime startDateTime = futureStart(32);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalA, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(clientA);
        BookingDetailResponse booking = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Richiesta coppia A.")
        );

        authenticateAs(professionalB);

        assertThatThrownBy(() -> bookingService.confirmBookingRequest(booking.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("BOOKING_REQUEST_NOT_FOUND"));

        assertThatThrownBy(() -> bookingService.rejectBookingRequest(booking.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("BOOKING_REQUEST_NOT_FOUND"));

        authenticateAs(clientB);

        AppException foreignBookingCancellation = catchThrowableOfType(
                () -> bookingService.cancelBookingRequest(booking.getId()),
                AppException.class
        );
        AppException missingBookingCancellation = catchThrowableOfType(
                () -> bookingService.cancelBookingRequest(Long.MAX_VALUE),
                AppException.class
        );
        assertThat(foreignBookingCancellation).isNotNull();
        assertThat(missingBookingCancellation).isNotNull();
        assertThat(foreignBookingCancellation.getStatus()).isEqualTo(missingBookingCancellation.getStatus());
        assertThat(foreignBookingCancellation.getErrorCode())
                .isEqualTo(missingBookingCancellation.getErrorCode());
        assertThat(foreignBookingCancellation.getMessage())
                .isEqualTo(missingBookingCancellation.getMessage());

        BookingRequest unchangedBooking = bookingRequestRepository.findById(booking.getId())
                .orElseThrow();
        AvailabilitySlot unchangedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(unchangedBooking.getStatus().name()).isEqualTo("PENDING");
        assertThat(unchangedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    private ProfessionalProfile createActivePersonalTrainer() {
        String email = "pro-" + UUID.randomUUID() + "@test.com";

        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                email,
                "password123",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);

        return professionalProfileRepository.save(professional);
    }

    private ClientProfile createActiveClient() {
        String email = "client-" + UUID.randomUUID() + "@test.com";

        ClientProfile client = new ClientProfile(
                "Luigi",
                "Bianchi",
                email,
                "password123",
                LocalDate.now().minusYears(30),
                BigDecimal.valueOf(180),
                "Dimagrimento",
                Gender.MALE
        );

        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setActive(true);

        return clientProfileRepository.save(client);
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken authentication
                = UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(user.getId(), user.getEmail()),
                        null,
                        List.of(new SimpleGrantedAuthority(user.getRole().name()))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("Cliente non collegato non deve creare una richiesta booking")
    void shouldNotCreateBookingRequestForUnlinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        LocalDateTime startDateTime = futureStart(14);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        assertThatThrownBy(() -> bookingService.createBookingRequest(request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Client con account attivo ma email non verificata non deve creare booking")
    void shouldRejectBookingForClientWithoutVerifiedEmail() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        client.setEmailVerified(false);
        clientProfileRepository.saveAndFlush(client);
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, client));

        LocalDateTime startDateTime = futureStart(14);
        AvailabilitySlot slot = availabilitySlotRepository.saveAndFlush(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(client);

        assertThatThrownBy(() -> bookingService.createBookingRequest(bookingRequestFor(slot, null)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                    assertThat(exception.getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
                });
        assertThat(bookingRequestRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Cliente non deve creare booking su uno slot bloccato")
    void shouldNotCreateBookingRequestForBlockedSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(33);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(professional);
        availabilityService.blockAvailabilitySlot(slot.getId());

        authenticateAs(client);

        assertThatThrownBy(() -> bookingService.createBookingRequest(
                bookingRequestFor(slot, "Richiesta su slot bloccato.")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_BOOKABLE"));

        AvailabilitySlot unchangedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(unchangedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.BLOCKED);
        assertThat(bookingRequestRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Cliente non deve creare booking su uno slot già prenotato")
    void shouldNotCreateBookingRequestForBookedSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(34);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(client);
        BookingDetailResponse pendingBooking = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Prima richiesta.")
        );

        authenticateAs(professional);
        bookingService.confirmBookingRequest(pendingBooking.getId());

        authenticateAs(client);

        assertThatThrownBy(() -> bookingService.createBookingRequest(
                bookingRequestFor(slot, "Seconda richiesta.")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("CLIENT_BOOKING_TIME_OVERLAP"));

        AvailabilitySlot unchangedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(unchangedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
        assertThat(bookingRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Professionista deve confermare una richiesta senza rendere binario lo stato dello slot")
    void shouldConfirmBookingRequestWithoutChangingSlotStatus() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(15);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional);

        BookingDetailResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmedResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Professionista deve rifiutare una richiesta booking pending")
    void shouldRejectPendingBookingRequest() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(16);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional);

        BookingDetailResponse rejectedResponse
                = bookingService.rejectBookingRequest(pendingResponse.getId());

        assertThat(rejectedResponse.getStatus()).isEqualTo("REJECTED");
        assertThat(rejectedResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Cliente deve cancellare una richiesta booking pending")
    void shouldCancelPendingBookingRequestByClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(17);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        BookingDetailResponse cancelledResponse
                = bookingService.cancelBookingRequest(pendingResponse.getId());

        assertThat(cancelledResponse.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Cliente deve cancellare una richiesta booking confermata e liberare lo slot")
    void shouldCancelConfirmedBookingRequestByClientAndReleaseSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(18);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional);

        BookingDetailResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");

        authenticateAs(client);

        BookingDetailResponse cancelledResponse
                = bookingService.cancelBookingRequest(confirmedResponse.getId());

        assertThat(cancelledResponse.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Professionista non deve cancellare una richiesta booking pending")
    void shouldNotCancelPendingBookingRequestByProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(19);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional);

        assertThatThrownBy(() -> bookingService.cancelBookingRequest(pendingResponse.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Una richiesta in attesa deve essere rifiutata dal professionista");

        authenticateAs(client);

        BookingDetailResponse detailResponse
                = bookingService.getBookingRequestDetail(pendingResponse.getId());

        assertThat(detailResponse.getStatus()).isEqualTo("PENDING");
        assertThat(detailResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Utente non coinvolto non deve vedere il dettaglio booking")
    void shouldNotReturnBookingDetailForUninvolvedUser() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        ClientProfile otherClient = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(20);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse bookingResponse
                = bookingService.createBookingRequest(request);

        authenticateAs(otherClient);

        AppException foreignBooking = catchThrowableOfType(
                () -> bookingService.getBookingRequestDetail(bookingResponse.getId()),
                AppException.class
        );
        AppException missingBooking = catchThrowableOfType(
                () -> bookingService.getBookingRequestDetail(Long.MAX_VALUE),
                AppException.class
        );

        assertThat(foreignBooking).isNotNull();
        assertThat(missingBooking).isNotNull();
        assertThat(foreignBooking.getStatus()).isEqualTo(missingBooking.getStatus());
        assertThat(foreignBooking.getErrorCode()).isEqualTo(missingBooking.getErrorCode());
        assertThat(foreignBooking.getMessage()).isEqualTo(missingBooking.getMessage());

        authenticateAs(client);

        BookingDetailResponse detailResponse
                = bookingService.getBookingRequestDetail(bookingResponse.getId());

        assertThat(detailResponse.getStatus()).isEqualTo("PENDING");
        assertThat(detailResponse.getClient().getId()).isEqualTo(client.getId());
        assertThat(detailResponse.getProfessional().getId()).isEqualTo(professional.getId());
    }

    @Test
    @DisplayName("Cliente non deve creare booking su uno slot ormai passato")
    void shouldNotCreateBookingRequestForPastAvailabilitySlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().minusHours(2).withNano(0);
        LocalDateTime endDateTime = LocalDateTime.now().minusHours(1).withNano(0);

        AvailabilitySlot expiredSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(expiredSlot, "Vorrei prenotare questo slot.");

        assertThatThrownBy(() -> bookingService.createBookingRequest(request))
                .isInstanceOf(AppException.class)
                .hasMessage("La disponibilità selezionata è scaduta e non è prenotabile");
    }

    @Test
    @DisplayName("Cliente non deve creare booking su slot appartenente a un nutrizionista")
    void shouldNotCreateBookingRequestForNutritionistSlot() {
        String email = "nutritionist-" + UUID.randomUUID() + "@test.com";

        ProfessionalProfile nutritionist = new ProfessionalProfile(
                "Anna",
                "Verdi",
                email,
                "password123",
                ProfessionalSpecialization.NUTRITIONIST
        );

        nutritionist.setAccountStatus(AccountStatus.ACTIVE);
        nutritionist.setEmailVerified(true);
        nutritionist.setActive(true);

        professionalProfileRepository.save(nutritionist);

        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(nutritionist, client)
        );

        LocalDateTime startDateTime = futureStart(23);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot invalidSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(nutritionist, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(invalidSlot, "Vorrei prenotare questo slot.");

        assertThatThrownBy(() -> bookingService.createBookingRequest(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Lo slot selezionato non è prenotabile per questo professionista");
    }

    @Test
    @DisplayName("Professionista non deve confermare un booking pending con slot ormai scaduto")
    void shouldNotConfirmPendingBookingRequestWhenSlotIsExpired() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime futureStartDateTime = futureStart(22);
        LocalDateTime futureEndDateTime = futureStartDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(futureStartDateTime), asBusinessInstant(futureEndDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest request = bookingRequestFor(slot, "Vorrei prenotare questo slot.");

        BookingDetailResponse pendingResponse
                = bookingService.createBookingRequest(request);

        slot.setStartDateTime(asBusinessInstant(LocalDateTime.now().minusHours(2).withNano(0)));
        slot.setEndDateTime(asBusinessInstant(LocalDateTime.now().minusHours(1).withNano(0)));
        availabilitySlotRepository.save(slot);

        authenticateAs(professional);

        assertThatThrownBy(() -> bookingService.confirmBookingRequest(pendingResponse.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Lo slot collegato è scaduto e non è più confermabile");

        assertThat(slot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Professionista non deve confermare booking pending su slot appartenente a un nutrizionista")
    void shouldNotConfirmPendingBookingRequestForNutritionistSlot() {
        String email = "nutritionist-" + UUID.randomUUID() + "@test.com";

        ProfessionalProfile nutritionist = new ProfessionalProfile(
                "Anna",
                "Verdi",
                email,
                "password123",
                ProfessionalSpecialization.NUTRITIONIST
        );

        nutritionist.setAccountStatus(AccountStatus.ACTIVE);
        nutritionist.setEmailVerified(true);
        nutritionist.setActive(true);

        professionalProfileRepository.save(nutritionist);

        ClientProfile client = createActiveClient();

        LocalDateTime startDateTime = futureStart(24);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot invalidSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(nutritionist, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        BookingRequest bookingRequest = bookingRequestRepository.save(
                new BookingRequest(
                        client,
                        nutritionist,
                        "Richiesta anomala preesistente.",
                        "Luigi Bianchi",
                        "Anna Verdi"
                )
        );

        BookingRequestItem bookingRequestItem = bookingRequestItemRepository.save(
                new BookingRequestItem(
                        bookingRequest,
                        invalidSlot,
                        invalidSlot.getStartDateTime(),
                        invalidSlot.getEndDateTime()
                )
        );

        bookingRequest.getItems().add(bookingRequestItem);

        authenticateAs(nutritionist);

        assertThatThrownBy(() -> bookingService.confirmBookingRequest(bookingRequest.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Lo slot collegato non è confermabile per questo professionista");

        assertThat(invalidSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Cliente non deve creare una seconda richiesta pending sullo stesso slot")
    void shouldNotCreateSecondPendingBookingRequestForSameSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = futureStart(25);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client);

        CreateBookingRequest firstRequest = bookingRequestFor(slot, "Prima richiesta.");

        bookingService.createBookingRequest(firstRequest);

        CreateBookingRequest secondRequest = bookingRequestFor(slot, "Seconda richiesta.");

        assertThatThrownBy(() -> bookingService.createBookingRequest(secondRequest))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("CLIENT_BOOKING_TIME_OVERLAP"));
    }

    @Test
    @DisplayName("PENDING deve riservare capacità e un esito negativo deve liberarla")
    void shouldReserveAndReleaseCapacityAcrossDifferentClients() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile firstClient = createActiveClient();
        ClientProfile secondClient = createActiveClient();
        ClientProfile thirdClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, firstClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, secondClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, thirdClient));

        LocalDateTime start = futureStart(31);
        AvailabilitySlot unsavedSlot = new AvailabilitySlot(
                professional,
                asBusinessInstant(start),
                asBusinessInstant(start.plusHours(1))
        );
        unsavedSlot.setCapacity(2);
        AvailabilitySlot slot = availabilitySlotRepository.save(unsavedSlot);

        authenticateAs(firstClient);
        BookingDetailResponse first = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Primo posto")
        );
        authenticateAs(secondClient);
        bookingService.createBookingRequest(bookingRequestFor(slot, "Secondo posto"));

        authenticateAs(thirdClient);
        assertThatThrownBy(() -> bookingService.createBookingRequest(
                bookingRequestFor(slot, "Posto esaurito")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_CAPACITY_EXHAUSTED"));

        authenticateAs(professional);
        bookingService.rejectBookingRequest(first.getId());

        authenticateAs(thirdClient);
        BookingDetailResponse replacement = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Posto liberato")
        );
        assertThat(replacement.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CONFIRMED occupa capacitÃ  e CANCELLED la libera")
    void shouldKeepCapacityReservedUntilConfirmedBookingIsCancelled() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile firstClient = createActiveClient();
        ClientProfile secondClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, firstClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, secondClient));

        LocalDateTime start = futureStart(32);
        AvailabilitySlot slot = availabilitySlotRepository.save(new AvailabilitySlot(
                professional,
                asBusinessInstant(start),
                asBusinessInstant(start.plusHours(1))
        ));

        authenticateAs(firstClient);
        BookingDetailResponse pending = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Prenotazione da confermare")
        );

        authenticateAs(professional);
        bookingService.confirmBookingRequest(pending.getId());

        authenticateAs(secondClient);
        Long slotId = slot.getId();
        assertThatThrownBy(() -> bookingService.createBookingRequest(
                bookingRequestFor(slot, "CapacitÃ  ancora occupata")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_CAPACITY_EXHAUSTED"));

        authenticateAs(firstClient);
        bookingService.cancelBookingRequest(pending.getId());

        authenticateAs(secondClient);
        BookingDetailResponse replacement = bookingService.createBookingRequest(
                bookingRequestFor(slot, "Posto liberato dopo cancellazione")
        );
        assertThat(replacement.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Capacità è verificata su ogni sotto-intervallo con durate differenti")
    void shouldEnforceCapacityAcrossOverlappingIntervalsWithDifferentDurations() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile firstClient = createActiveClient();
        ClientProfile secondClient = createActiveClient();
        ClientProfile thirdClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, firstClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, secondClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, thirdClient));

        LocalDate date = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(40);
        AvailabilitySlot window = createWeeklyWindow(professional, date, 2);

        authenticateAs(firstClient);
        BookingDetailResponse firstBooking = bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                45,
                "Sessione breve"
        ));
        window.setLocationLabel("Studio trasferito");
        availabilitySlotRepository.saveAndFlush(window);
        assertThat(bookingService.getBookingRequestDetail(firstBooking.getId()).getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getLocationLabel()).isEqualTo("Studio"));
        authenticateAs(secondClient);
        bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                120,
                "Sessione completa"
        ));

        authenticateAs(thirdClient);
        assertThatThrownBy(() -> bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 30).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                60,
                "Intervallo saturo"
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo("AVAILABILITY_SLOT_CAPACITY_EXHAUSTED"));
    }

    @Test
    @DisplayName("Lo stesso Client può prenotare in sequenza ma non in sovrapposizione")
    void shouldRejectSameClientOverlapAndAllowAdjacentIntervals() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate date = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(41);
        AvailabilitySlot window = createWeeklyWindow(professional, date, 3);
        authenticateAs(client);

        bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                45,
                null
        ));
        bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 45).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                45,
                null
        ));

        assertThatThrownBy(() -> bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 30).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                60,
                null
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("CLIENT_BOOKING_TIME_OVERLAP"));
        assertThat(bookingRequestRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Start non allineato e combinazione fuori finestra sono rifiutati")
    void shouldRejectInvalidStartGranularityAndWindowFit() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate date = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(42);
        AvailabilitySlot window = createWeeklyWindow(professional, date, 2);
        authenticateAs(client);

        assertThatThrownBy(() -> bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(9, 7).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                45,
                null
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR"));
        assertThatThrownBy(() -> bookingService.createBookingRequest(new CreateBookingRequest(
                window.getId(),
                date.atTime(10, 30).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                60,
                null
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Durata 180 minuti è prenotabile in una finestra compatibile")
    void shouldCreateBookingWithMaximumSupportedDuration() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate date = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(43);
        WeeklyAvailabilityRule rule = weeklyAvailabilityRuleRepository.saveAndFlush(
                new WeeklyAvailabilityRule(
                        professional,
                        date.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0),
                        java.util.Set.of(180),
                        "Studio",
                        1,
                        date
                )
        );
        AvailabilitySlot slot = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                rule,
                asBusinessInstant(date.atTime(9, 0)),
                asBusinessInstant(date.atTime(12, 0)),
                "Studio",
                1
        ));
        authenticateAs(client);

        BookingDetailResponse response = bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(9, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                180,
                null
        ));

        assertThat(response.getItems()).singleElement().satisfies(item ->
                assertThat(java.time.Duration.between(
                        item.getScheduledStart().toInstant(),
                        item.getScheduledEnd().toInstant()
                ).toMinutes()).isEqualTo(180));
    }

    @Test
    @DisplayName("Nuove prenotazioni su slot manuali legacy restituiscono il contratto neutro")
    void shouldRejectNewBookingOnLegacyManualAvailability() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDateTime start = futureStart(44);
        AvailabilitySlot legacy = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                asBusinessInstant(start),
                asBusinessInstant(start.plusHours(1))
        ));
        authenticateAs(client);

        AppException legacyError = catchThrowableOfType(
                () -> bookingService.createBookingRequest(new CreateBookingRequest(
                        legacy.getId(),
                        start.atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                        60,
                        null
                )),
                AppException.class
        );
        AppException missingError = catchThrowableOfType(
                () -> bookingService.createBookingRequest(new CreateBookingRequest(
                        Long.MAX_VALUE,
                        start.atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                        60,
                        null
                )),
                AppException.class
        );

        assertThat(legacyError).isNotNull();
        assertThat(missingError).isNotNull();
        assertThat(legacyError.getStatus()).isEqualTo(missingError.getStatus());
        assertThat(legacyError.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_FOUND");
        assertThat(legacyError.getErrorCode()).isEqualTo(missingError.getErrorCode());
    }

    @Test
    @DisplayName("Booking legacy esistenti restano leggibili e completano le transizioni")
    void shouldPreserveHistoricalLegacyBookingStateMachine() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        LocalDateTime start = futureStart(45);
        AvailabilitySlot unsavedLegacy = new AvailabilitySlot(
                professional,
                asBusinessInstant(start),
                asBusinessInstant(start.plusHours(1))
        );
        unsavedLegacy.setLocationLabel("Studio storico");
        AvailabilitySlot legacy = availabilitySlotRepository.saveAndFlush(unsavedLegacy);
        BookingRequest cancellable = historicalBooking(client, professional, legacy, "Storico confermabile");
        legacy.setLocationLabel("Studio corrente");
        availabilitySlotRepository.saveAndFlush(legacy);
        OffsetDateTime expectedStart = start.atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime();
        OffsetDateTime expectedEnd = start.plusHours(1).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime();

        authenticateAs(client);
        assertThat(bookingService.getClientBookingRequests())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getId()).isEqualTo(cancellable.getId());
                    assertThat(summary.getStatus()).isEqualTo("PENDING");
                    assertThat(summary.getScheduledStart()).isEqualTo(expectedStart);
                    assertThat(summary.getScheduledEnd()).isEqualTo(expectedEnd);
                });
        BookingDetailResponse detail = bookingService.getBookingRequestDetail(cancellable.getId());
        assertThat(detail.getStatus()).isEqualTo("PENDING");
        assertThat(detail.getScheduledStart()).isEqualTo(expectedStart);
        assertThat(detail.getScheduledEnd()).isEqualTo(expectedEnd);
        assertThat(detail.getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getAvailabilitySlotId()).isEqualTo(legacy.getId());
                    assertThat(item.getScheduledStart()).isEqualTo(expectedStart);
                    assertThat(item.getScheduledEnd()).isEqualTo(expectedEnd);
                    assertThat(item.getLocationLabel()).isEqualTo("Studio storico");
                });
        authenticateAs(professional);
        assertThat(bookingService.getProfessionalBookingRequests())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getId()).isEqualTo(cancellable.getId());
                    assertThat(summary.getStatus()).isEqualTo("PENDING");
                    assertThat(summary.getScheduledStart()).isEqualTo(expectedStart);
                    assertThat(summary.getScheduledEnd()).isEqualTo(expectedEnd);
                });
        assertThat(bookingService.confirmBookingRequest(cancellable.getId()).getStatus()).isEqualTo("CONFIRMED");
        authenticateAs(client);
        assertThat(bookingService.cancelBookingRequest(cancellable.getId()).getStatus()).isEqualTo("CANCELLED");

        AvailabilitySlot secondLegacy = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                asBusinessInstant(start.plusHours(2)),
                asBusinessInstant(start.plusHours(3))
        ));
        BookingRequest rejectable = historicalBooking(client, professional, secondLegacy, "Storico rifiutabile");
        authenticateAs(professional);
        assertThat(bookingService.rejectBookingRequest(rejectable.getId()).getStatus()).isEqualTo("REJECTED");
        authenticateAs(client);
        assertThat(bookingService.getBookingRequestDetail(rejectable.getId()).getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getAvailabilitySlotId()).isEqualTo(secondLegacy.getId()));
    }

    private BookingRequest historicalBooking(
            ClientProfile client,
            ProfessionalProfile professional,
            AvailabilitySlot slot,
            String note
    ) {
        BookingRequest request = bookingRequestRepository.save(new BookingRequest(
                client,
                professional,
                note,
                client.getFirstName() + " " + client.getLastName(),
                professional.getFirstName() + " " + professional.getLastName()
        ));
        BookingRequestItem item = bookingRequestItemRepository.save(new BookingRequestItem(
                request,
                slot,
                slot.getStartDateTime(),
                slot.getEndDateTime(),
                slot.getLocationLabel()
        ));
        request.getItems().add(item);
        return request;
    }

    private CreateBookingRequest bookingRequestFor(AvailabilitySlot slot, String note) {
        if (slot.getWeeklyRule() == null) {
            var start = slot.getStartDateTime().atZone(ZoneId.of("Europe/Rome"));
            var end = slot.getEndDateTime().atZone(ZoneId.of("Europe/Rome"));
            int duration = Math.toIntExact(java.time.Duration.between(
                    slot.getStartDateTime(),
                    slot.getEndDateTime()
            ).toMinutes());
            WeeklyAvailabilityRule rule = weeklyAvailabilityRuleRepository.saveAndFlush(
                    new WeeklyAvailabilityRule(
                            slot.getProfessional(),
                            start.getDayOfWeek(),
                            start.toLocalTime(),
                            end.toLocalTime(),
                            java.util.Set.of(duration),
                            slot.getLocationLabel(),
                            slot.getCapacity(),
                            start.toLocalDate()
                    )
            );
            slot.setWeeklyRule(rule);
            availabilitySlotRepository.saveAndFlush(slot);
        }
        int duration = Math.toIntExact(java.time.Duration.between(
                slot.getStartDateTime(),
                slot.getEndDateTime()
        ).toMinutes());
        return new CreateBookingRequest(
                slot.getId(),
                slot.getStartDateTime().atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                duration,
                note
        );
    }

    private AvailabilitySlot createWeeklyWindow(
            ProfessionalProfile professional,
            LocalDate date,
            int capacity
    ) {
        WeeklyAvailabilityRule rule = weeklyAvailabilityRuleRepository.saveAndFlush(
                new WeeklyAvailabilityRule(
                        professional,
                        date.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0),
                        java.util.Set.of(45, 60, 120),
                        "Studio",
                        capacity,
                        date
                )
        );
        return availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                rule,
                asBusinessInstant(date.atTime(9, 0)),
                asBusinessInstant(date.atTime(11, 0)),
                "Studio",
                capacity
        ));
    }

    private static Instant asBusinessInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.of("Europe/Rome")).toInstant();
    }

    private static LocalDateTime futureStart(int days) {
        return LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(days).atTime(9, 0);
    }
}
