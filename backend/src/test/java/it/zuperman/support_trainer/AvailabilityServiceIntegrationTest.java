package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import it.zuperman.support_trainer.availability.dto.request.ChangeAvailabilitySlotBlockRequest;
import it.zuperman.support_trainer.availability.dto.request.CreateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.DeactivateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.dto.response.ClientAvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilityRuleChangeRepository;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.availability.service.WeeklyAvailabilityRuleService;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
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
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.AuthenticatedUserPrincipal;

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

    @Autowired
    private WeeklyAvailabilityRuleService weeklyRuleService;

    @Autowired
    private WeeklyAvailabilityRuleRepository weeklyRuleRepository;

    @Autowired
    private AvailabilityRuleChangeRepository ruleChangeRepository;

    @Autowired
    private BookingRequestItemRepository bookingRequestItemRepository;

    @Autowired
    private BookingRequestRepository bookingRequestRepository;

    @Autowired
    private ApplicationTimeProvider timeProvider;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve creare uno slot availability valido per un professionista")
    void shouldCreateAvailabilitySlotForProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);

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
        authenticateAs(professional);

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
    @DisplayName("Non deve creare uno slot availability sovrapposto per lo stesso professionista")
    void shouldNotCreateOverlappingAvailabilitySlotForSameProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);

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

        LocalDateTime startDateTime = LocalDate.now(BUSINESS_ZONE).plusDays(9).atTime(9, 0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );
        attachWeeklyRule(slot);

        authenticateAs(client);

        List<ClientAvailabilitySlotResponse> response
                = availabilityService.getClientAvailableSlotsByProfessional(professional.getId());

        assertThat(response)
                .filteredOn(candidate -> candidate.occurrenceId().equals(slot.getId()))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.windowStart()).isEqualTo(asBusinessOffset(startDateTime));
                    assertThat(candidate.windowEnd()).isEqualTo(asBusinessOffset(endDateTime));
                    assertThat(candidate.capacity()).isEqualTo(1);
                    assertThat(candidate.allowedDurations()).containsExactly(60);
                    assertThat(candidate.bookableOptions()).isNotEmpty();
                });
    }

    @Test
    @DisplayName("Cliente collegato non scopre uno slot manuale legacy")
    void shouldNotExposeLegacyManualSlotToLinkedClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDateTime start = LocalDate.now(BUSINESS_ZONE).plusDays(10).atTime(9, 0);
        AvailabilitySlot legacy = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                asBusinessInstant(start),
                asBusinessInstant(start.plusHours(1))
        ));
        authenticateAs(client);

        assertThat(availabilityService.getClientAvailableSlotsByProfessional(professional.getId()))
                .extracting(ClientAvailabilitySlotResponse::occurrenceId)
                .doesNotContain(legacy.getId());
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

        authenticateAs(client);

        assertThatThrownBy(() -> availabilityService.getClientAvailableSlotsByProfessional(professional.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Professionista deve bloccare e sbloccare un proprio slot availability")
    void shouldBlockAndUnblockAvailabilitySlot() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);

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

        authenticateAs(owner);

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(12).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlotResponse createdSlot = availabilityService.createAvailabilitySlot(
                new CreateAvailabilitySlotRequest(
                        asBusinessOffset(startDateTime),
                        asBusinessOffset(endDateTime)
                )
        );

        authenticateAs(otherProfessional);

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
        authenticateAs(professional);

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
                        asBusinessInstant(LocalDate.now(BUSINESS_ZONE).minusDays(1).atTime(9, 0)),
                        asBusinessInstant(LocalDate.now(BUSINESS_ZONE).minusDays(1).atTime(10, 0))
                )
        );

        LocalDateTime futureStart = LocalDate.now(BUSINESS_ZONE).plusDays(21).atTime(9, 0);
        AvailabilitySlot futureSlot = availabilitySlotRepository.save(
                new AvailabilitySlot(
                        professional,
                        asBusinessInstant(futureStart),
                        asBusinessInstant(futureStart.plusHours(1))
                )
        );
        attachWeeklyRule(expiredSlot);
        attachWeeklyRule(futureSlot);

        authenticateAs(client);

        List<ClientAvailabilitySlotResponse> response
                = availabilityService.getClientAvailableSlotsByProfessional(professional.getId());

        assertThat(response)
                .extracting(ClientAvailabilitySlotResponse::occurrenceId)
                .contains(futureSlot.getId())
                .doesNotContain(expiredSlot.getId());
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

        authenticateAs(nutritionist);

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

        authenticateAs(client);

        createHistoricalBooking(client, professional, slot, "Vorrei prenotare questo slot.");

        authenticateAs(professional);

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(slot.getId(), updateRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Uno slot con una richiesta di prenotazione in attesa non può essere modificato o bloccato");
    }

    @Test
    @DisplayName("Blocco con booking pending richiede motivo e preserva la prenotazione")
    void shouldRequireReasonToBlockAvailabilityWithPendingBooking() {
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

        authenticateAs(client);

        createHistoricalBooking(client, professional, slot, "Vorrei prenotare questo slot.");

        authenticateAs(professional);

        assertThatThrownBy(() -> availabilityService.blockAvailabilitySlot(slot.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("AVAILABILITY_CHANGE_REASON_REQUIRED"));

        AvailabilitySlotResponse blocked = availabilityService.blockAvailabilitySlot(
                slot.getId(),
                new ChangeAvailabilitySlotBlockRequest("Chiusura straordinaria")
        );
        assertThat(blocked.getBlocked()).isTrue();
        assertThat(bookingRequestItemRepository.findAll()).hasSize(1);
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

        LocalDateTime startDateTime = LocalDate.now(BUSINESS_ZONE).plusDays(28).atTime(9, 0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        AvailabilitySlot slot = availabilitySlotRepository.save(
                new AvailabilitySlot(professional, asBusinessInstant(startDateTime), asBusinessInstant(endDateTime))
        );

        authenticateAs(requestingClient);

        attachWeeklyRule(slot);
        createHistoricalBooking(requestingClient, professional, slot, "Vorrei prenotare questo slot.");

        authenticateAs(otherClient);

        List<ClientAvailabilitySlotResponse> response
                = availabilityService.getClientAvailableSlotsByProfessional(professional.getId());

        assertThat(response)
                .noneMatch(candidate -> candidate.occurrenceId().equals(slot.getId()));

        var nextOccurrenceStart = startDateTime.plusWeeks(1)
                .atZone(BUSINESS_ZONE)
                .toOffsetDateTime();
        assertThat(response)
                .filteredOn(candidate -> candidate.windowStart().equals(nextOccurrenceStart))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.bookableOptions())
                        .filteredOn(option -> option.startDateTime().equals(nextOccurrenceStart))
                        .singleElement()
                        .satisfies(option -> assertThat(option.allowedDurations()).containsExactly(60)));
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

        authenticateAs(client);

        BookingRequest pendingBooking = createHistoricalBooking(
                client,
                professional,
                slot,
                "Vorrei prenotare questo slot."
        );

        authenticateAs(professional);

        bookingService.rejectBookingRequest(pendingBooking.getId());

        UpdateAvailabilitySlotRequest updateRequest = new UpdateAvailabilitySlotRequest(
                asBusinessOffset(startDateTime.plusDays(1)),
                asBusinessOffset(endDateTime.plusDays(1))
        );

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(slot.getId(), updateRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("Uno slot già coinvolto in una richiesta di prenotazione non può essere ripianificato");
    }

    @Test
    @DisplayName("Regola settimanale materializza una finestra al giorno con durate multiple")
    void shouldMaterializeWeeklyRuleAcrossRollingHorizon() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        var response = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(12, 30),
                List.of(45, 60, 120),
                "Palestra X",
                3,
                validFrom
        ));

        var materialized = availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(response.id()))
                .toList();

        assertThat(materialized).isNotEmpty();
        assertThat(response.allowedDurations()).containsExactly(45, 60, 120);
        assertThat(materialized).allSatisfy(slot -> {
            assertThat(java.time.Duration.between(slot.getStartDateTime(), slot.getEndDateTime()).toMinutes())
                    .isEqualTo(210);
            assertThat(slot.getCapacity()).isEqualTo(3);
            assertThat(slot.getLocationLabel()).isEqualTo("Palestra X");
        });
        assertThat(materialized.stream()
                .map(slot -> slot.getStartDateTime().atZone(BUSINESS_ZONE).toLocalDate()))
                .doesNotHaveDuplicates();
        List<LocalDate> expectedDates = new java.util.ArrayList<>();
        LocalDate horizonEnd = timeProvider.todayBusiness().plusMonths(6);
        for (LocalDate date = validFrom; !date.isAfter(horizonEnd); date = date.plusWeeks(1)) {
            expectedDates.add(date);
        }
        assertThat(materialized.stream()
                .map(slot -> slot.getStartDateTime().atZone(BUSINESS_ZONE).toLocalDate()))
                .containsExactlyInAnyOrderElementsOf(expectedDates);
    }

    @Test
    @DisplayName("Durate multiple valide includono i boundary 15, 45, 60, 120 e 180")
    void shouldAcceptSupportedMultipleDurations() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(2);

        var response = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 15),
                LocalTime.of(12, 15),
                List.of(15, 45, 60, 120, 180),
                "Studio",
                2,
                validFrom
        ));

        assertThat(response.allowedDurations()).containsExactly(15, 45, 60, 120, 180);
        assertThat(weeklyRuleRepository.findById(response.id()).orElseThrow().getAllowedDurations())
                .containsExactlyInAnyOrder(15, 45, 60, 120, 180);
    }

    @Test
    @DisplayName("Durate vuote, fuori range, non multiple o fuori finestra sono rifiutate")
    void shouldRejectInvalidAllowedDurations() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(3);
        List<List<Integer>> invalidDurations = List.of(
                List.of(),
                List.of(5),
                List.of(17),
                List.of(181),
                List.of(60, 60),
                List.of(120)
        );

        invalidDurations.forEach(durations -> assertThatThrownBy(() ->
                weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                        validFrom.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        durations,
                        null,
                        1,
                        validFrom
                ))
        ).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Start della fascia deve essere allineato a 15 minuti")
    void shouldRejectWeeklyWindowStartOutsidePlatformGranularity() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(4);

        assertThatThrownBy(() -> weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 7),
                LocalTime.of(11, 0),
                List.of(45),
                null,
                1,
                validFrom
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Modifica che coinvolge PENDING deve richiedere la motivazione")
    void shouldRequireChangeReasonWhenWeeklyRuleImpactsBooking() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60),
                "Studio",
                2,
                validFrom
        ));
        AvailabilitySlot slot = availabilitySlotRepository.findAll().stream()
                .filter(candidate -> candidate.getWeeklyRule() != null)
                .filter(candidate -> candidate.getWeeklyRule().getId().equals(rule.id()))
                .findFirst()
                .orElseThrow();

        authenticateAs(client);
        bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                slot.getStartDateTime().atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                "Prenotazione"
        ));

        authenticateAs(professional);
        var impact = weeklyRuleService.previewImpact(rule.id());
        assertThat(impact.impactDetected()).isTrue();
        assertThat(impact.impactedBookingCount()).isEqualTo(1);

        UpdateWeeklyAvailabilityRuleRequest update = new UpdateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                List.of(60),
                "Nuovo studio",
                2,
                null
        );
        assertThatThrownBy(() -> weeklyRuleService.update(rule.id(), update))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_CHANGE_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("Regole settimanali adiacenti sono ammesse")
    void shouldAllowAdjacentWeeklyRules() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                List.of(60),
                "Palestra X",
                2,
                validFrom
        ));
        weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(13, 0),
                LocalTime.of(17, 0),
                List.of(30),
                "Studio",
                1,
                validFrom
        ));

        assertThat(weeklyRuleService.listMine()).hasSize(2);
    }

    @Test
    @DisplayName("Regole settimanali sovrapposte sono rifiutate")
    void shouldRejectOverlappingWeeklyRules() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                List.of(60),
                null,
                1,
                validFrom
        ));

        assertThatThrownBy(() -> weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(12, 30),
                LocalTime.of(15, 0),
                List.of(30),
                null,
                1,
                validFrom
        ))).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("WEEKLY_AVAILABILITY_RULE_OVERLAP"));
    }

    @Test
    @DisplayName("Modifica della regola ha effetto immediato senza data programmata")
    void shouldApplyRuleChangeImmediately() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(60),
                null,
                1,
                validFrom
        ));
        var updated = weeklyRuleService.update(
                rule.id(),
                new UpdateWeeklyAvailabilityRuleRequest(
                        validFrom.getDayOfWeek(),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        List.of(30, 60),
                        null,
                        1,
                        null
                )
        );

        assertThat(updated.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(updated.allowedDurations()).containsExactly(30, 60);
        assertThat(ruleChangeRepository.findAll()).singleElement()
                .satisfies(change -> assertThat(change.getEffectiveFrom())
                        .isEqualTo(timeProvider.todayBusiness()));
    }

    @Test
    @DisplayName("Riduzione capacitÃ  sotto occupancy deve essere rifiutata")
    void shouldRejectCapacityBelowExistingOccupancy() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile firstClient = createActiveClient();
        ClientProfile secondClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, firstClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, secondClient));
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(60),
                "Studio",
                2,
                validFrom
        ));
        AvailabilitySlot slot = firstSlotForRule(rule.id());

        authenticateAs(firstClient);
        bookingService.createBookingRequest(bookingRequestFor(slot, "Primo posto"));
        authenticateAs(secondClient);
        bookingService.createBookingRequest(bookingRequestFor(slot, "Secondo posto"));

        authenticateAs(professional);
        UpdateWeeklyAvailabilityRuleRequest update = new UpdateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(60),
                "Studio",
                1,
                "Riduzione della classe"
        );

        assertThatThrownBy(() -> weeklyRuleService.update(rule.id(), update))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("AVAILABILITY_CAPACITY_BELOW_OCCUPANCY"));
    }

    @Test
    @DisplayName("Modifica rigenera solo gli slot futuri senza prenotazioni")
    void shouldRegenerateOnlyUnbookedFutureSlotsFromEffectiveDate() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        authenticateAs(professional);

        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60),
                "Palestra X",
                2,
                validFrom
        ));
        List<Long> originalSlotIds = availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(rule.id()))
                .map(AvailabilitySlot::getId)
                .toList();

        weeklyRuleService.update(rule.id(), new UpdateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                List.of(30),
                "Studio nuovo",
                4,
                null
        ));

        List<AvailabilitySlot> allRuleSlots = availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(rule.id()))
                .toList();
        assertThat(allRuleSlots)
                .filteredOn(slot -> originalSlotIds.contains(slot.getId()))
                .allSatisfy(slot -> assertThat(slot.getActive()).isFalse());
        assertThat(allRuleSlots)
                .filteredOn(slot -> !originalSlotIds.contains(slot.getId()))
                .isNotEmpty()
                .allSatisfy(slot -> {
                    assertThat(slot.getActive()).isTrue();
                    assertThat(slot.getLocationLabel()).isEqualTo("Studio nuovo");
                    assertThat(slot.getCapacity()).isEqualTo(4);
                    assertThat(java.time.Duration.between(
                            slot.getStartDateTime(),
                            slot.getEndDateTime()
                    ).toMinutes()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("Disattivazione conserva Booking e registra la motivazione")
    void shouldPreserveBookingAndReasonWhenDeactivatingRule() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(60),
                "Palestra X",
                1,
                validFrom
        ));
        AvailabilitySlot bookedSlot = firstSlotForRule(rule.id());
        Instant agreedStart = bookedSlot.getStartDateTime();
        Instant agreedEnd = bookedSlot.getEndDateTime();

        authenticateAs(client);
        bookingService.createBookingRequest(bookingRequestFor(bookedSlot, "Prenotazione"));

        authenticateAs(professional);
        weeklyRuleService.deactivate(
                rule.id(),
                new DeactivateWeeklyAvailabilityRuleRequest("Chiusura programmata")
        );

        AvailabilitySlot preserved = availabilitySlotRepository.findById(bookedSlot.getId()).orElseThrow();
        assertThat(preserved.getStartDateTime()).isEqualTo(agreedStart);
        assertThat(preserved.getEndDateTime()).isEqualTo(agreedEnd);
        assertThat(preserved.getActive()).isFalse();
        assertThat(bookingRequestItemRepository.findAll()).singleElement()
                .satisfies(item -> assertThat(item.getAvailabilitySlot().getId()).isEqualTo(bookedSlot.getId()));
        assertThat(ruleChangeRepository.findAll()).singleElement().satisfies(change -> {
            assertThat(change.getChangeReason()).isEqualTo("Chiusura programmata");
            assertThat(change.getImpactedBookingCount()).isEqualTo(1);
            assertThat(change.getEffectiveFrom()).isEqualTo(timeProvider.todayBusiness());
        });
    }

    @Test
    @DisplayName("Modifica da oggi non riscrive un'occorrenza giÃ  trascorsa")
    void shouldNotRewritePastOccurrenceWhenUpdateIsEffectiveToday() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        LocalDate today = timeProvider.todayBusiness();
        WeeklyAvailabilityRule rule = weeklyRuleRepository.saveAndFlush(new WeeklyAvailabilityRule(
                professional,
                today.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                java.util.Set.of(60),
                "Studio storico",
                1,
                today
        ));
        Instant pastStart = timeProvider.nowInstant().minusSeconds(3600);
        AvailabilitySlot pastSlot = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                rule,
                pastStart,
                pastStart.plusSeconds(1800),
                "Studio storico",
                1
        ));

        authenticateAs(professional);
        weeklyRuleService.update(rule.getId(), new UpdateWeeklyAvailabilityRuleRequest(
                today.getDayOfWeek(),
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                List.of(60),
                "Studio nuovo",
                1,
                null
        ));

        AvailabilitySlot preserved = availabilitySlotRepository.findById(pastSlot.getId()).orElseThrow();
        assertThat(preserved.getActive()).isTrue();
        assertThat(preserved.getStartDateTime()).isEqualTo(pastStart);
        assertThat(preserved.getLocationLabel()).isEqualTo("Studio storico");
    }

    @Test
    @DisplayName("PATCH legacy non può ripianificare un'occorrenza generata")
    void shouldRejectLegacyPatchForWeeklyOccurrence() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(5);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(45, 60),
                "Studio",
                2,
                validFrom
        ));
        AvailabilitySlot occurrence = firstSlotForRule(rule.id());

        assertThatThrownBy(() -> availabilityService.updateAvailabilitySlot(
                occurrence.getId(),
                new UpdateAvailabilitySlotRequest(
                        occurrence.getStartDateTime().atZone(BUSINESS_ZONE)
                                .plusHours(1).toOffsetDateTime(),
                        occurrence.getEndDateTime().atZone(BUSINESS_ZONE)
                                .plusHours(1).toOffsetDateTime()
                )
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo("WEEKLY_AVAILABILITY_OCCURRENCE_NOT_PATCHABLE"));
    }

    @Test
    @DisplayName("Occorrenze trascorse non sono elencate né bloccabili")
    void shouldExcludeAndRejectPastOccurrences() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        Instant pastStart = timeProvider.nowInstant().minusSeconds(3600);
        AvailabilitySlot past = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                pastStart,
                pastStart.plusSeconds(1800)
        ));
        authenticateAs(professional);

        assertThat(availabilityService.getMyAvailabilitySlots())
                .extracting(AvailabilitySlotResponse::getId)
                .doesNotContain(past.getId());
        assertThatThrownBy(() -> availabilityService.blockAvailabilitySlot(past.getId()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("AVAILABILITY_SLOT_IN_PAST"));
    }

    @Test
    @DisplayName("Client riceve solo dati minimizzati e capacitÃ  residua")
    void shouldReturnMinimizedClientAvailabilityWithRemainingCapacity() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile bookedClient = createActiveClient();
        ClientProfile viewingClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, bookedClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, viewingClient));
        LocalDate validFrom = LocalDate.now(BUSINESS_ZONE).plusDays(1);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                validFrom.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(60),
                "Palestra X",
                3,
                validFrom
        ));
        AvailabilitySlot slot = firstSlotForRule(rule.id());

        authenticateAs(bookedClient);
        bookingService.createBookingRequest(bookingRequestFor(slot, "Un posto"));

        authenticateAs(viewingClient);
        ClientAvailabilitySlotResponse response = availabilityService
                .getClientAvailableSlotsByProfessional(professional.getId())
                .stream()
                .filter(candidate -> candidate.occurrenceId().equals(slot.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.location()).isEqualTo("Palestra X");
        assertThat(response.capacity()).isEqualTo(3);
        assertThat(response.allowedDurations()).containsExactly(60);
        assertThat(response.startIntervalMinutes()).isEqualTo(15);
        assertThat(response.bookableOptions()).isNotEmpty();
        assertThat(java.util.Arrays.stream(ClientAvailabilitySlotResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly(
                        "occurrenceId",
                        "windowStart",
                        "windowEnd",
                        "allowedDurations",
                        "startIntervalMinutes",
                        "location",
                        "capacity",
                        "bookableOptions"
                );
    }

    @Test
    @DisplayName("Client riceve opzioni server-side coerenti con capacity e stati Booking")
    void shouldReturnOnlyServerCalculatedBookableOptions() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile bookingClient = createActiveClient();
        ClientProfile viewingClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, bookingClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, viewingClient));
        LocalDate date = LocalDate.now(BUSINESS_ZONE).plusDays(8);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                date.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60, 120),
                "Studio",
                1,
                date
        ));
        AvailabilitySlot slot = firstSlotForRule(rule.id());

        authenticateAs(bookingClient);
        var shortBooking = bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(10, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                null
        ));

        authenticateAs(viewingClient);
        ClientAvailabilitySlotResponse pendingResponse = clientWindow(professional.getId(), slot.getId());
        assertThat(pendingResponse.bookableOptions())
                .filteredOn(option -> option.startDateTime().toLocalTime().equals(LocalTime.of(9, 0)))
                .singleElement()
                .satisfies(option -> assertThat(option.allowedDurations()).containsExactly(60));

        authenticateAs(professional);
        bookingService.confirmBookingRequest(shortBooking.getId());
        authenticateAs(viewingClient);
        assertThat(clientWindow(professional.getId(), slot.getId()).bookableOptions())
                .filteredOn(option -> option.startDateTime().toLocalTime().equals(LocalTime.of(9, 0)))
                .singleElement()
                .satisfies(option -> assertThat(option.allowedDurations()).containsExactly(60));

        authenticateAs(bookingClient);
        bookingService.cancelBookingRequest(shortBooking.getId());
        authenticateAs(viewingClient);
        assertThat(clientWindow(professional.getId(), slot.getId()).bookableOptions())
                .filteredOn(option -> option.startDateTime().toLocalTime().equals(LocalTime.of(9, 0)))
                .singleElement()
                .satisfies(option -> assertThat(option.allowedDurations()).containsExactly(60, 120));

        authenticateAs(bookingClient);
        var fullBooking = bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(9, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                120,
                null
        ));
        authenticateAs(viewingClient);
        assertThat(availabilityService.getClientAvailableSlotsByProfessional(professional.getId()))
                .extracting(ClientAvailabilitySlotResponse::occurrenceId)
                .doesNotContain(slot.getId());

        authenticateAs(professional);
        bookingService.rejectBookingRequest(fullBooking.getId());
        authenticateAs(viewingClient);
        assertThat(clientWindow(professional.getId(), slot.getId()).bookableOptions()).isNotEmpty();
    }

    @Test
    @DisplayName("Client non riceve opzioni sovrapposte ai propri Booking occupanti")
    void shouldFilterOwnPendingAndConfirmedBookingsButNotTerminalStates() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate date = LocalDate.now(BUSINESS_ZONE).plusDays(9);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                date.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60),
                "Studio",
                2,
                date
        ));
        AvailabilitySlot slot = firstSlotForRule(rule.id());

        authenticateAs(client);
        var pendingBooking = bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(9, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                null
        ));

        ClientAvailabilitySlotResponse pendingResponse = clientWindow(professional.getId(), slot.getId());
        assertNotBookableAt(pendingResponse, LocalTime.of(9, 0));
        assertNotBookableAt(pendingResponse, LocalTime.of(9, 30));
        assertBookableAt(pendingResponse, LocalTime.of(10, 0), 60);

        authenticateAs(professional);
        bookingService.confirmBookingRequest(pendingBooking.getId());
        authenticateAs(client);
        ClientAvailabilitySlotResponse confirmedResponse = clientWindow(professional.getId(), slot.getId());
        assertNotBookableAt(confirmedResponse, LocalTime.of(9, 0));
        assertNotBookableAt(confirmedResponse, LocalTime.of(9, 30));
        assertBookableAt(confirmedResponse, LocalTime.of(10, 0), 60);

        bookingService.cancelBookingRequest(pendingBooking.getId());
        assertBookableAt(
                clientWindow(professional.getId(), slot.getId()),
                LocalTime.of(9, 0),
                60
        );

        var bookingToReject = bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(9, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                null
        ));
        authenticateAs(professional);
        bookingService.rejectBookingRequest(bookingToReject.getId());
        authenticateAs(client);
        assertBookableAt(
                clientWindow(professional.getId(), slot.getId()),
                LocalTime.of(9, 0),
                60
        );
    }

    @Test
    @DisplayName("Overlap Client resta scoped al Professional e distinto dalla capacity altrui")
    void shouldScopeOwnOverlapToRequestedProfessionalAndKeepOtherClientCapacityBookable() {
        ProfessionalProfile requestedProfessional = createActivePersonalTrainer();
        ProfessionalProfile otherProfessional = createActivePersonalTrainer();
        ClientProfile viewingClient = createActiveClient();
        ClientProfile otherClient = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(requestedProfessional, viewingClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(requestedProfessional, otherClient));
        professionalClientLinkRepository.save(new ProfessionalClientLink(otherProfessional, viewingClient));
        LocalDate date = LocalDate.now(BUSINESS_ZONE).plusDays(10);

        authenticateAs(requestedProfessional);
        var requestedRule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                date.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60),
                "Studio A",
                2,
                date
        ));
        AvailabilitySlot requestedSlot = firstSlotForRule(requestedRule.id());

        authenticateAs(otherProfessional);
        var otherRule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                date.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(60),
                "Studio B",
                2,
                date
        ));
        AvailabilitySlot otherSlot = firstSlotForRule(otherRule.id());

        authenticateAs(viewingClient);
        bookingService.createBookingRequest(new CreateBookingRequest(
                otherSlot.getId(),
                date.atTime(9, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                null
        ));

        authenticateAs(otherClient);
        bookingService.createBookingRequest(new CreateBookingRequest(
                requestedSlot.getId(),
                date.atTime(9, 0).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                60,
                null
        ));

        authenticateAs(viewingClient);
        assertBookableAt(
                clientWindow(requestedProfessional.getId(), requestedSlot.getId()),
                LocalTime.of(9, 0),
                60
        );
        ClientAvailabilitySlotResponse ownBookingWindow = clientWindow(
                otherProfessional.getId(),
                otherSlot.getId()
        );
        assertNotBookableAt(ownBookingWindow, LocalTime.of(9, 0));
        assertBookableAt(ownBookingWindow, LocalTime.of(10, 0), 60);
    }

    @Test
    @DisplayName("Overlap Client filtra solo le durate incompatibili e preserva l'adiacenza")
    void shouldKeepNonOverlappingDurationsAndAdjacentOptionsForSameClient() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
        LocalDate date = LocalDate.now(BUSINESS_ZONE).plusDays(11);

        authenticateAs(professional);
        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                date.getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                List.of(45, 60, 90),
                "Studio",
                2,
                date
        ));
        AvailabilitySlot slot = firstSlotForRule(rule.id());

        authenticateAs(client);
        bookingService.createBookingRequest(new CreateBookingRequest(
                slot.getId(),
                date.atTime(9, 45).atZone(BUSINESS_ZONE).toOffsetDateTime(),
                45,
                null
        ));

        ClientAvailabilitySlotResponse response = clientWindow(professional.getId(), slot.getId());
        assertBookableAt(response, LocalTime.of(9, 0), 45);
        assertNotBookableAt(response, LocalTime.of(9, 30));
        assertBookableAt(response, LocalTime.of(10, 30), 45, 60, 90);
    }

    @Test
    @DisplayName("Materializzazione salta l'overlap DST e continua nelle settimane successive")
    void shouldContinueMaterializationAfterInvalidDstOccurrence() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional);
        LocalDate firstSunday = timeProvider.todayBusiness().with(
                java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY)
        );
        LocalDate overlapSunday = LocalDate.of(2026, 10, 25);
        assertThat(firstSunday).isBeforeOrEqualTo(overlapSunday);

        var rule = weeklyRuleService.create(new CreateWeeklyAvailabilityRuleRequest(
                java.time.DayOfWeek.SUNDAY,
                LocalTime.of(2, 0),
                LocalTime.of(3, 0),
                List.of(60),
                null,
                1,
                firstSunday
        ));

        assertThat(availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(rule.id()))
                .map(slot -> slot.getStartDateTime().atZone(BUSINESS_ZONE).toLocalDate()))
                .doesNotContain(overlapSunday)
                .contains(overlapSunday.minusWeeks(1), overlapSunday.plusWeeks(1));
    }

    private ClientAvailabilitySlotResponse clientWindow(Long professionalId, Long slotId) {
        return availabilityService.getClientAvailableSlotsByProfessional(professionalId).stream()
                .filter(window -> window.occurrenceId().equals(slotId))
                .findFirst()
                .orElseThrow();
    }

    private void assertBookableAt(
            ClientAvailabilitySlotResponse response,
            LocalTime start,
            Integer... expectedDurations
    ) {
        assertThat(response.bookableOptions())
                .filteredOn(option -> option.startDateTime().toLocalTime().equals(start))
                .singleElement()
                .satisfies(option -> assertThat(option.allowedDurations())
                .containsExactly(expectedDurations));
    }

    private void assertNotBookableAt(ClientAvailabilitySlotResponse response, LocalTime start) {
        assertThat(response.bookableOptions())
                .noneMatch(option -> option.startDateTime().toLocalTime().equals(start));
    }

    private BookingRequest createHistoricalBooking(
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

    private void attachWeeklyRule(AvailabilitySlot slot) {
        if (slot.getWeeklyRule() != null) {
            return;
        }
        var start = slot.getStartDateTime().atZone(BUSINESS_ZONE);
        var end = slot.getEndDateTime().atZone(BUSINESS_ZONE);
        int duration = Math.toIntExact(java.time.Duration.between(
                slot.getStartDateTime(),
                slot.getEndDateTime()
        ).toMinutes());
        WeeklyAvailabilityRule rule = weeklyRuleRepository.saveAndFlush(new WeeklyAvailabilityRule(
                slot.getProfessional(),
                start.getDayOfWeek(),
                start.toLocalTime(),
                end.toLocalTime(),
                java.util.Set.of(duration),
                slot.getLocationLabel(),
                slot.getCapacity(),
                start.toLocalDate()
        ));
        slot.setWeeklyRule(rule);
        availabilitySlotRepository.saveAndFlush(slot);
    }

    private CreateBookingRequest bookingRequestFor(AvailabilitySlot slot, String note) {
        return new CreateBookingRequest(
                slot.getId(),
                slot.getStartDateTime().atZone(BUSINESS_ZONE).toOffsetDateTime(),
                Math.toIntExact(java.time.Duration.between(
                        slot.getStartDateTime(),
                        slot.getEndDateTime()
                ).toMinutes()),
                note
        );
    }

    private AvailabilitySlot firstSlotForRule(Long ruleId) {
        return availabilitySlotRepository.findAll().stream()
                .filter(slot -> slot.getWeeklyRule() != null)
                .filter(slot -> slot.getWeeklyRule().getId().equals(ruleId))
                .findFirst()
                .orElseThrow();
    }

    private static OffsetDateTime asBusinessOffset(LocalDateTime localDateTime) {
        return localDateTime.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private static Instant asBusinessInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(BUSINESS_ZONE).toInstant();
    }
}
