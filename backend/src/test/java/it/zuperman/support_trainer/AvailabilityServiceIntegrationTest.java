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
                startDateTime,
                endDateTime
        );

        AvailabilitySlotResponse response = availabilityService.createAvailabilitySlot(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStartDateTime()).isEqualTo(startDateTime);
        assertThat(response.getEndDateTime()).isEqualTo(endDateTime);
        assertThat(response.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());

        List<AvailabilitySlot> savedSlots = availabilitySlotRepository.findAll();

        assertThat(savedSlots).hasSize(1);
        assertThat(savedSlots.get(0).getProfessional().getId()).isEqualTo(professional.getId());
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
                firstStartDateTime,
                firstEndDateTime
        );

        availabilityService.createAvailabilitySlot(firstRequest);

        CreateAvailabilitySlotRequest overlappingRequest = new CreateAvailabilitySlotRequest(
                firstStartDateTime.plusMinutes(30),
                firstStartDateTime.plusHours(1).plusMinutes(30)
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        List<AvailabilitySlotResponse> response
                = availabilityService.getAvailableSlotsByProfessional(professional.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(slot.getId());
        assertThat(response.get(0).getStartDateTime()).isEqualTo(startDateTime);
        assertThat(response.get(0).getEndDateTime()).isEqualTo(endDateTime);
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
    @DisplayName("Non deve aggiornare uno slot availability se non è disponibile")
    void shouldNotUpdateAvailabilitySlotWhenNotAvailable() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(12).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        availabilityService.blockAvailabilitySlot(slot.getId());

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                startDateTime.plusDays(1),
                endDateTime.plusDays(1)
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
                        LocalDateTime.now().minusHours(2).withNano(0),
                        LocalDateTime.now().minusHours(1).withNano(0)
                )
        );

        AvailabilitySlot futureSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(
                        professional,
                        LocalDateTime.now().plusDays(21).withNano(0),
                        LocalDateTime.now().plusDays(21).plusHours(1).withNano(0)
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
                startDateTime,
                endDateTime
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
        );

        authenticateAs(client.getEmail(), "CLIENT");

        bookingService.createBookingRequest(
                new CreateBookingRequest(slot.getId(), "Vorrei prenotare questo slot.")
        );

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                startDateTime.plusDays(1),
                endDateTime.plusDays(1)
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
                new AvailabilitySlot(professional, startDateTime, endDateTime)
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
}
