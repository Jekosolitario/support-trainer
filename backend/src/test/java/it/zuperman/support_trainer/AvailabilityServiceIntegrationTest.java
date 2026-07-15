package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
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

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
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
class AvailabilityServiceIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    @Autowired
    private BookingService bookingService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve creare uno slot availability valido per un professionista")
    void shouldCreateAvailabilitySlotForProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(7).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime),
                asBusinessOffset(endDateTime)
        );

        AvailabilitySlotResponse response = availabilityService.createAvailabilitySlot(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStartDateTime()).isEqualTo(asBusinessOffset(startDateTime));
        assertThat(response.getEndDateTime()).isEqualTo(asBusinessOffset(endDateTime));
        assertThat(response.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());

        List<AvailabilitySlot> savedSlots = availabilitySlotRepository.findAll();

        assertThat(savedSlots).hasSize(1);
        assertThat(savedSlots.get(0).getProfessional().getId()).isEqualTo(professional.getId());
    }

    @Test
    @DisplayName("Professionista deve aggiornare parzialmente un proprio slot availability")
    void shouldPartiallyUpdateAndReturnOwnAvailabilitySlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(7).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlotResponse createdSlot = availabilityService.createAvailabilitySlot(
                new CreateAvailabilitySlotRequest(
                        asBusinessOffset(startDateTime),
                        asBusinessOffset(endDateTime)
                )
        );

        LocalDateTime updatedEndDateTime = endDateTime.plusMinutes(30);
        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                null,
                asBusinessOffset(updatedEndDateTime)
        );

        AvailabilitySlotResponse updatedSlot = availabilityService.updateAvailabilitySlot(
                createdSlot.getId(),
                updateRequest
        );

        List<AvailabilitySlotResponse> ownSlots = availabilityService.getMyAvailabilitySlots();

        assertThat(updatedSlot.getStartDateTime()).isEqualTo(asBusinessOffset(startDateTime));
        assertThat(updatedSlot.getEndDateTime()).isEqualTo(asBusinessOffset(updatedEndDateTime));
        assertThat(updatedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());
        assertThat(updatedSlot.getActive()).isTrue();

        assertThat(ownSlots).singleElement()
                .satisfies(slot -> {
                    assertThat(slot.getId()).isEqualTo(createdSlot.getId());
                    assertThat(slot.getStartDateTime()).isEqualTo(asBusinessOffset(startDateTime));
                    assertThat(slot.getEndDateTime()).isEqualTo(asBusinessOffset(updatedEndDateTime));
                    assertThat(slot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());
                    assertThat(slot.getActive()).isTrue();
                });

        AvailabilitySlot savedSlot = availabilitySlotRepository.findById(createdSlot.getId())
                .orElseThrow();

        assertThat(savedSlot.getProfessional().getId()).isEqualTo(professional.getId());
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
    @DisplayName("Non deve creare uno slot availability sovrapposto per lo stesso professionista")
    void shouldNotCreateOverlappingAvailabilitySlotForSameProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime firstStartDateTime = LocalDateTime.now().plusDays(8).withNano(0);
        LocalDateTime firstEndDateTime = firstStartDateTime.plusHours(2);

        CreateAvailabilitySlotRequest firstRequest = new CreateAvailabilitySlotRequest(
                asBusinessOffset(firstStartDateTime),
                asBusinessOffset(firstEndDateTime)
        );

        availabilityService.createAvailabilitySlot(firstRequest);

        CreateAvailabilitySlotRequest overlappingRequest = new CreateAvailabilitySlotRequest(
                asBusinessOffset(firstStartDateTime.plusMinutes(30)),
                asBusinessOffset(firstStartDateTime.plusHours(1).plusMinutes(30))
        );

        assertThatThrownBy(() -> availabilityService.createAvailabilitySlot(overlappingRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Esiste già uno slot sovrapposto per questo professionista");

        assertThat(availabilitySlotRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Cliente collegato deve vedere gli slot availability disponibili del professionista")
    void shouldReturnAvailableSlotsForLinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(9).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        List<AvailabilitySlotResponse> response
                = availabilityService.getAvailableSlotsByProfessional(professional.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(slot.getId());
        assertThat(response.get(0).getStartDateTime()).isEqualTo(asBusinessOffset(startDateTime));
        assertThat(response.get(0).getEndDateTime()).isEqualTo(asBusinessOffset(endDateTime));
        assertThat(response.get(0).getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());
    }

    @Test
    @DisplayName("Cliente non collegato non deve vedere gli slot availability del professionista")
    void shouldNotReturnAvailableSlotsForUnlinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(10).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        assertThatThrownBy(() -> availabilityService.getAvailableSlotsByProfessional(professional.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Professionista deve bloccare e sbloccare un proprio slot availability")
    void shouldBlockAndUnblockAvailabilitySlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(11).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        AvailabilitySlotResponse blockedResponse
                = availabilityService.blockAvailabilitySlot(slot.getId());

        assertThat(blockedResponse.getId()).isEqualTo(slot.getId());
        assertThat(blockedResponse.getStatus()).isEqualTo(AvailabilitySlotStatus.BLOCKED.name());

        AvailabilitySlotResponse unblockedResponse
                = availabilityService.unblockAvailabilitySlot(slot.getId());

        assertThat(unblockedResponse.getId()).isEqualTo(slot.getId());
        assertThat(unblockedResponse.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());
    }

    @Test
    @DisplayName("Professionista non deve modificare slot availability di un altro professionista")
    void shouldNotMutateAvailabilitySlotOwnedByAnotherProfessional() {
        ProfessionalProfile owner = createActivePersonalTrainer();
        ProfessionalProfile otherProfessional = createActivePersonalTrainer();

        authenticateAs(owner.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(12).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlotResponse createdSlot = availabilityService.createAvailabilitySlot(
                new CreateAvailabilitySlotRequest(
                        asBusinessOffset(startDateTime),
                        asBusinessOffset(endDateTime)
                )
        );

        authenticateAs(otherProfessional.getEmail(), "PROFESSIONAL");

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        AppException foreignSlot = catchThrowableOfType(
                () -> availabilityService.updateAvailabilitySlot(createdSlot.getId(), updateRequest),
                AppException.class
        );
        AppException missingSlot = catchThrowableOfType(
                () -> availabilityService.updateAvailabilitySlot(Long.MAX_VALUE, updateRequest),
                AppException.class
        );
        assertThat(foreignSlot).isNotNull();
        assertThat(missingSlot).isNotNull();
        assertThat(foreignSlot.getStatus()).isEqualTo(missingSlot.getStatus());
        assertThat(foreignSlot.getErrorCode()).isEqualTo(missingSlot.getErrorCode());
        assertThat(foreignSlot.getMessage()).isEqualTo(missingSlot.getMessage());

        assertThatThrownBy(() -> availabilityService.blockAvailabilitySlot(createdSlot.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_FOUND"));

        assertThatThrownBy(() -> availabilityService.unblockAvailabilitySlot(createdSlot.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_NOT_FOUND"));

        AvailabilitySlot unchangedSlot = availabilitySlotRepository.findById(createdSlot.getId())
                .orElseThrow();

        assertThat(unchangedSlot.getStartDateTime()).isEqualTo(asBusinessInstant(startDateTime));
        assertThat(unchangedSlot.getEndDateTime()).isEqualTo(asBusinessInstant(endDateTime));
        assertThat(unchangedSlot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Non deve aggiornare uno slot availability se non è disponibile")
    void shouldNotUpdateAvailabilitySlotWhenNotAvailable() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(12).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        availabilityService.blockAvailabilitySlot(slot.getId());

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(slot.getId(), updateRequest))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Cliente collegato non deve vedere slot availability scaduti")
    void shouldNotReturnExpiredAvailableSlotsForLinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        AvailabilitySlot expiredSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(
                        professional,
                        asBusinessInstant(LocalDateTime.now().minusHours(2).withNano(0)),
                        asBusinessInstant(LocalDateTime.now().minusHours(1).withNano(0))
                )
        );

        AvailabilitySlot futureSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(
                        professional,
                        asBusinessInstant(LocalDateTime.now().plusDays(21).withNano(0)),
                        asBusinessInstant(LocalDateTime.now().plusDays(21).plusHours(1).withNano(0))
                )
        );

        authenticateAs(client.getEmail(), "CLIENT");

        List<AvailabilitySlotResponse> response
                = availabilityService.getAvailableSlotsByProfessional(professional.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(futureSlot.getId());
        assertThat(response.get(0).getId()).isNotEqualTo(expiredSlot.getId());
    }

    @Test
    @DisplayName("Nutrizionista non deve creare slot availability")
    void shouldNotCreateAvailabilitySlotForNutritionist() {
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

        authenticateAs(nutritionist.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(7).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime),
                asBusinessOffset(endDateTime)
        );

        assertThatThrownBy(() -> availabilityService.createAvailabilitySlot(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Il modulo availability è disponibile solo per i personal trainer");

        assertThat(availabilitySlotRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Professionista non deve modificare uno slot con booking pending")
    void shouldNotUpdateAvailabilitySlotWithPendingBooking() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(26).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Vorrei prenotare questo slot.")
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(slot.getId(), updateRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Uno slot con una richiesta di prenotazione in attesa non può essere modificato o bloccato");
    }

    @Test
    @DisplayName("Professionista non deve bloccare uno slot con booking pending")
    void shouldNotBlockAvailabilitySlotWithPendingBooking() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(27).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Vorrei prenotare questo slot.")
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        assertThatThrownBy(() -> availabilityService.blockAvailabilitySlot(slot.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("Uno slot con una richiesta di prenotazione in attesa non può essere modificato o bloccato");
    }

    @Test
    @DisplayName("Cliente collegato non deve vedere uno slot con booking pending")
    void shouldNotReturnAvailableSlotWithPendingBookingForLinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile requestingClient = createActiveClient();
        ClientProfile otherClient = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, requestingClient)
        );

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, otherClient)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(28).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(requestingClient.getEmail(), "CLIENT");

        bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Vorrei prenotare questo slot.")
        );

        authenticateAs(otherClient.getEmail(), "CLIENT");

        List<AvailabilitySlotResponse> response
                = availabilityService.getAvailableSlotsByProfessional(professional.getId());

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("Professionista non deve ripianificare uno slot già coinvolto in un booking rifiutato")
    void shouldNotUpdateAvailabilitySlotAfterRejectedBookingHistory() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();

        professionalClientLinkRepository.save(
                new ProfessionalClientLink(professional, client)
        );

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(29).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(client.getEmail(), "CLIENT");

        var pendingBooking = bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Vorrei prenotare questo slot.")
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        bookingService.rejectBookingRequest(pendingBooking.getId());

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(slot.getId(), updateRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Uno slot già coinvolto in una richiesta di prenotazione non può essere ripianificato");
    }

    private static OffsetDateTime asBusinessOffset(LocalDateTime localDateTime) {
        return localDateTime.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private static Instant asBusinessInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(BUSINESS_ZONE).toInstant();
    }
}
