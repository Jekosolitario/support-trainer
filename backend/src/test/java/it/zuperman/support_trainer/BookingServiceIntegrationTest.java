package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingRequestResponse;
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                " Vorrei prenotare questo slot. "
        );

        BookingRequestResponse response = bookingService.createBookingRequest(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getClientId()).isEqualTo(client.getId());
        assertThat(response.getProfessionalId()).isEqualTo(professional.getId());
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getNote()).isEqualTo("Vorrei prenotare questo slot.");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAvailabilitySlotId()).isEqualTo(slot.getId());
        assertThat(response.getItems().get(0).getSlotStatus()).isEqualTo("AVAILABLE");
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        BookingRequestResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmedResponse.getItems()).hasSize(1);
        assertThat(confirmedResponse.getItems().get(0).getSlotStatus()).isEqualTo("BOOKED");

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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        BookingRequestResponse rejectedResponse
                = bookingService.rejectBookingRequest(pendingResponse.getId());

        assertThat(rejectedResponse.getStatus()).isEqualTo("REJECTED");
        assertThat(rejectedResponse.getItems()).hasSize(1);
        assertThat(rejectedResponse.getItems().get(0).getSlotStatus()).isEqualTo("AVAILABLE");

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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse = bookingService.createBookingRequest(request);

        BookingRequestResponse cancelledResponse
                = bookingService.cancelBookingRequest(pendingResponse.getId());

        assertThat(cancelledResponse.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledResponse.getItems()).hasSize(1);
        assertThat(cancelledResponse.getItems().get(0).getSlotStatus()).isEqualTo("AVAILABLE");

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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        BookingRequestResponse confirmedResponse
                = bookingService.confirmBookingRequest(pendingResponse.getId());

        assertThat(confirmedResponse.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmedResponse.getItems().get(0).getSlotStatus()).isEqualTo("BOOKED");

        authenticateAs(client.getEmail(), "CLIENT");

        BookingRequestResponse cancelledResponse
                = bookingService.cancelBookingRequest(confirmedResponse.getId());

        assertThat(cancelledResponse.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledResponse.getItems()).hasSize(1);
        assertThat(cancelledResponse.getItems().get(0).getSlotStatus()).isEqualTo("AVAILABLE");

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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse = bookingService.createBookingRequest(request);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        assertThatThrownBy(() -> bookingService.cancelBookingRequest(pendingResponse.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Una richiesta in attesa deve essere rifiutata dal professionista");

        authenticateAs(client.getEmail(), "CLIENT");

        BookingRequestResponse detailResponse
                = bookingService.getBookingRequestDetail(pendingResponse.getId());

        assertThat(detailResponse.getStatus()).isEqualTo("PENDING");
        assertThat(detailResponse.getItems()).hasSize(1);
        assertThat(detailResponse.getItems().get(0).getSlotStatus()).isEqualTo("AVAILABLE");

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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse bookingResponse
                = bookingService.createBookingRequest(request);

        authenticateAs(otherClient.getEmail(), "CLIENT");

        assertThatThrownBy(() -> bookingService.getBookingRequestDetail(bookingResponse.getId()))
                .isInstanceOf(AppException.class);

        authenticateAs(client.getEmail(), "CLIENT");

        BookingRequestResponse detailResponse
                = bookingService.getBookingRequestDetail(bookingResponse.getId());

        assertThat(detailResponse.getStatus()).isEqualTo("PENDING");
        assertThat(detailResponse.getClientId()).isEqualTo(client.getId());
        assertThat(detailResponse.getProfessionalId()).isEqualTo(professional.getId());
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
                new AvailabilitySlot(nutritionist, startDateTime, endDateTime)
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
                new AvailabilitySlot(professional, futureStartDateTime, futureEndDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        CreateBookingRequest request = new CreateBookingRequest(
                slot.getId(),
                "Vorrei prenotare questo slot."
        );

        BookingRequestResponse pendingResponse
                = bookingService.createBookingRequest(request);

        slot.setStartDateTime(LocalDateTime.now().minusHours(2).withNano(0));
        slot.setEndDateTime(LocalDateTime.now().minusHours(1).withNano(0));
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
                new AvailabilitySlot(nutritionist, startDateTime, endDateTime)
        );

        BookingRequest bookingRequest = bookingRequestRepository.save(
                new BookingRequest(client, nutritionist, "Richiesta anomala preesistente.")
        );

        BookingRequestItem bookingRequestItem = bookingRequestItemRepository.save(
                new BookingRequestItem(bookingRequest, invalidSlot)
        );

        bookingRequest.getItems().add(bookingRequestItem);

        authenticateAs(nutritionist.getEmail(), "PROFESSIONAL");

        assertThatThrownBy(() -> bookingService.confirmBookingRequest(bookingRequest.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Lo slot collegato non è confermabile per questo professionista");

        assertThat(invalidSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }
}
