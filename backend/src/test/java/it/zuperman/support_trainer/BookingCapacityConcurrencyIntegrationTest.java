package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.service.BookingService;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingCapacityConcurrencyIntegrationTest {

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
    private WeeklyAvailabilityRuleRepository weeklyAvailabilityRuleRepository;

    @Autowired
    private BookingRequestItemRepository bookingRequestItemRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Due Client concorrenti non prenotano entrambi l'ultimo posto")
    void shouldAtomicallyReserveTheLastAvailablePlace() throws Exception {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile existingClient = createActiveClient();
        ClientProfile firstClient = createActiveClient();
        ClientProfile secondClient = createActiveClient();
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, existingClient));
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, firstClient));
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, secondClient));

        Instant start = LocalDateTime.now()
                .plusDays(30)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .atZone(ZoneId.of("Europe/Rome"))
                .toInstant();
        AvailabilitySlot slot = createWeeklySlot(professional, start, 2);
        Long slotId = slot.getId();

        authenticateAs(existingClient);
        bookingService.createBookingRequest(bookingRequest(slotId, start, "Posto già occupato"));
        SecurityContextHolder.clearContext();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startRace = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(
                    () -> attemptBooking(firstClient, slotId, start, ready, startRace)
            );
            Future<String> second = executor.submit(
                    () -> attemptBooking(secondClient, slotId, start, ready, startRace)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startRace.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(
                    "SUCCESS",
                    "AVAILABILITY_SLOT_CAPACITY_EXHAUSTED"
            );
        }

        assertThat(bookingRequestItemRepository.countOccupiedCapacity(
                slot.getId(),
                Set.of(BookingRequestStatus.PENDING, BookingRequestStatus.CONFIRMED)
        )).isEqualTo(2);
    }

    @Test
    @DisplayName("Due richieste concorrenti dello stesso Client non creano overlap duplicati")
    void shouldAtomicallyRejectConcurrentOverlapForSameClient() throws Exception {
        ProfessionalProfile professional = createActivePersonalTrainer();
        ClientProfile client = createActiveClient();
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, client));
        Instant start = LocalDateTime.now()
                .plusDays(31)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .atZone(ZoneId.of("Europe/Rome"))
                .toInstant();
        AvailabilitySlot slot = createWeeklySlot(professional, start, 3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startRace = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Long slotId = slot.getId();
            Future<String> first = executor.submit(
                    () -> attemptBooking(client, slotId, start, ready, startRace)
            );
            Future<String> second = executor.submit(
                    () -> attemptBooking(client, slotId, start, ready, startRace)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startRace.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder("SUCCESS", "CLIENT_BOOKING_TIME_OVERLAP");
        }

        assertThat(bookingRequestItemRepository.countOccupiedCapacity(
                slot.getId(),
                Set.of(BookingRequestStatus.PENDING, BookingRequestStatus.CONFIRMED)
        )).isEqualTo(1);
    }

    private String attemptBooking(
            ClientProfile client,
            Long slotId,
            Instant slotStart,
            CountDownLatch ready,
            CountDownLatch startRace
    ) throws InterruptedException {
        authenticateAs(client);
        ready.countDown();
        try {
            if (!startRace.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La contesa non è partita entro il timeout");
            }
            bookingService.createBookingRequest(bookingRequest(slotId, slotStart, "Contesa ultimo posto"));
            return "SUCCESS";
        } catch (AppException exception) {
            return exception.getErrorCode();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AvailabilitySlot createWeeklySlot(
            ProfessionalProfile professional,
            Instant start,
            int capacity
    ) {
        var zonedStart = start.atZone(ZoneId.of("Europe/Rome"));
        WeeklyAvailabilityRule rule = weeklyAvailabilityRuleRepository.saveAndFlush(
                new WeeklyAvailabilityRule(
                        professional,
                        zonedStart.getDayOfWeek(),
                        zonedStart.toLocalTime(),
                        zonedStart.toLocalTime().plusHours(1),
                        Set.of(60),
                        null,
                        capacity,
                        zonedStart.toLocalDate()
                )
        );
        return availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                rule,
                start,
                start.plus(1, ChronoUnit.HOURS),
                null,
                capacity
        ));
    }

    private CreateBookingRequest bookingRequest(Long slotId, Instant start, String note) {
        return new CreateBookingRequest(
                slotId,
                start.atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime(),
                60,
                note
        );
    }

    private ProfessionalProfile createActivePersonalTrainer() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                "pro-" + UUID.randomUUID() + "@test.com",
                "password123",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        return professionalProfileRepository.saveAndFlush(professional);
    }

    private ClientProfile createActiveClient() {
        ClientProfile client = new ClientProfile(
                "Luigi",
                "Bianchi",
                "client-" + UUID.randomUUID() + "@test.com",
                "password123",
                LocalDate.now().minusYears(30),
                BigDecimal.valueOf(180),
                "Allenamento",
                Gender.MALE
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setActive(true);
        return clientProfileRepository.saveAndFlush(client);
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
}
