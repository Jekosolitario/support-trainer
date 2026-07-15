package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
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
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(13).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                " Vorrei prenotare questo slot. "
        );

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

        LocalDateTime startDateTimeA = LocalDateTime.now().plusDays(30).withNano(0);
        AvailabilitySlot slotA = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalA, asBusinessInstant(startDateTimeA), asBusinessInstant(startDateTimeA.plusHours(1)))
        );

        LocalDateTime startDateTimeB = LocalDateTime.now().plusDays(31).withNano(0);
        AvailabilitySlot slotB = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalB, asBusinessInstant(startDateTimeB), asBusinessInstant(startDateTimeB.plusHours(1)))
        );

        authenticateAs(clientA.getEmail(), "CLIENT");
        BookingDetailResponse bookingA = bookingService.createBookingRequest(
                new CreateBookingRequest(slotA.getId(), "Richiesta coppia A.")
        );

        authenticateAs(clientB.getEmail(), "CLIENT");
        BookingDetailResponse bookingB = bookingService.createBookingRequest(
                new CreateBookingRequest(slotB.getId(), "Richiesta coppia B.")
        );

        authenticateAs(clientA.getEmail(), "CLIENT");
        List<Long> clientBookingIds = bookingService.getClientBookingRequests().stream()
                .map(response -> response.getId())
                .toList();

        assertThat(clientBookingIds)
                .containsExactly(bookingA.getId())
                .doesNotContain(bookingB.getId());

        authenticateAs(professionalA.getEmail(), "PROFESSIONAL");
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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(32).withNano(0);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professionalA, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(clientA.getEmail(), "CLIENT");
        BookingDetailResponse booking = bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Richiesta coppia A.")
        );

        authenticateAs(professionalB.getEmail(), "PROFESSIONAL");

        assertThatThrownBy(() -> bookingService.confirmBookingRequest(booking.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("BOOKING_REQUEST_NOT_FOUND"));

        assertThatThrownBy(() -> bookingService.rejectBookingRequest(booking.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("BOOKING_REQUEST_NOT_FOUND"));

        authenticateAs(clientB.getEmail(), "CLIENT");

        assertThatThrownBy(() -> bookingService.cancelBookingRequest(booking.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("BOOKING_REQUEST_ACCESS_DENIED"));

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

    private void authenticateAs(String email, String authority) {
        UsernamePasswordAuthenticationToken authentication
                = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("Cliente non collegato non deve creare una richiesta booking")
    void shouldNotCreateBookingRequestForUnlinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(14).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        assertThatThrownBy(() -> bookingService.createBookingRequest(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Cliente non deve creare booking su uno slot bloccato")
    void shouldNotCreateBookingRequestForBlockedSlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(33).withNano(0);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");
        availabilityService.blockAvailabilitySlot(slot.getId());

        authenticateAs(client.getEmail(), "CLIENT");

        assertThatThrownBy(() -> bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Richiesta su slot bloccato.")
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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(34).withNano(0);
        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(startDateTime.plusHours(1)))
        );

        authenticateAs(client.getEmail(), "CLIENT");
        BookingDetailResponse pendingBooking = bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Prima richiesta.")
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");
        bookingService.confirmBookingRequest(pendingBooking.getId());

        authenticateAs(client.getEmail(), "CLIENT");

        assertThatThrownBy(() -> bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Seconda richiesta.")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_BOOKABLE"));

        AvailabilitySlot unchangedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(unchangedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.BOOKED);
        assertThat(bookingRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Professionista deve confermare una richiesta booking e segnare lo slot come booked")
    void shouldConfirmBookingRequestAndMarkSlotAsBooked() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(15).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        BookingDetailResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmedResponse.getItems()).hasSize(1);

        AvailabilitySlot updatedSlot = availabilitySlotRepository.findById(slot.getId())
                .orElseThrow();

        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.BOOKED);
    }

    @Test
    @DisplayName("Professionista deve rifiutare una richiesta booking pending")
    void shouldRejectPendingBookingRequest() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(16).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(17).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(18).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        BookingDetailResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");

        authenticateAs(client.getEmail(), "CLIENT");

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(19).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        assertThatThrownBy(() -> bookingService.cancelBookingRequest(pendingResponse.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Una richiesta in attesa deve essere rifiutata dal professionista");

        authenticateAs(client.getEmail(), "CLIENT");

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(20).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse bookingResponse
                = bookingService.createBookingRequest(request);

        authenticateAs(otherClient.getEmail(), "CLIENT");

        assertThatThrownBy(() -> bookingService.getBookingRequestDetail(bookingResponse.getId()))
                .isInstanceOf(AppException.class);

        authenticateAs(client.getEmail(), "CLIENT");

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

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                expiredSlot.getId(),
                "Vorrei prenotare questo slot."
        );

        assertThatThrownBy(() -> bookingService.createBookingRequest(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Lo slot selezionato è scaduto e non è prenotabile");
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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(23).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot invalidSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(nutritionist, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                invalidSlot.getId(),
                "Vorrei prenotare questo slot."
        );

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

        LocalDateTime futureStartDateTime = LocalDateTime.now().plusDays(22).withNano(0);
        LocalDateTime futureEndDateTime = futureStartDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(futureStartDateTime), asBusinessInstant(futureEndDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingDetailResponse pendingResponse
                = bookingService.createBookingRequest(request);

        slot.setStartDateTime(asBusinessInstant(LocalDateTime.now().minusHours(2).withNano(0)));
        slot.setEndDateTime(asBusinessInstant(LocalDateTime.now().minusHours(1).withNano(0)));
        availabilitySlotRepository.save(slot);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(24).withNano(0);
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

        authenticateAs(nutritionist.getEmail(), "PROFESSIONAL");

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

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(25).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest firstRequest = new CreateBookingRequest(
                slot.getId(),
                "Prima richiesta."
        );

        bookingService.createBookingRequest(firstRequest);

        CreateBookingRequest secondRequest = new CreateBookingRequest(
                slot.getId(),
                "Seconda richiesta."
        );

        assertThatThrownBy(() -> bookingService.createBookingRequest(secondRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Esiste già una richiesta di prenotazione in attesa per questo slot");
    }
    private static Instant asBusinessInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.of("Europe/Rome")).toInstant();
    }
}
