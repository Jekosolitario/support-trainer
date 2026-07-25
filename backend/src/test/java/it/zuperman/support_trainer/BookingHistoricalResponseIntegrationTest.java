package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.booking.service.BookingService;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

@SpringBootTest(classes = {
    SupportTrainerApplication.class,
    BookingHistoricalResponseIntegrationTest.FixedClockConfiguration.class
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class BookingHistoricalResponseIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

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
    private BookingService bookingService;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ProfessionalProfile professional;
    private ClientProfile client;
    private ProfessionalClientLink link;
    private CsrfSession professionalAuth;
    private CsrfSession clientAuth;

    @BeforeEach
    void setUpParticipants() throws Exception {
        String suffix = UUID.randomUUID().toString();
        professional = professionalProfileRepository.saveAndFlush(
                activeProfessional("professional-" + suffix + "@example.com")
        );
        client = clientProfileRepository.saveAndFlush(activeClient("client-" + suffix + "@example.com"));
        link = professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, client));
        professionalAuth = SessionAuthTestSupport.loginAndRefreshCsrf(mockMvc, professional.getEmail(), PASSWORD);
        clientAuth = SessionAuthTestSupport.loginAndRefreshCsrf(mockMvc, client.getEmail(), PASSWORD);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeSelfContainedSummaryAndDetailWithoutSensitiveOrLiveSlotData() throws Exception {
        AvailabilitySlot slot = saveSlot("2026-07-20T15:30:00Z", "2026-07-20T16:30:00Z");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilitySlotId\":" + slot.getId() + ",\"note\":\"Richiesta sicura\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.client.displayName").value("Luigi Bianchi"))
                .andExpect(jsonPath("$.client.profileImageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.professional.displayName").value("Mario Rossi"))
                .andExpect(jsonPath("$.professional.specialization").value("PERSONAL_TRAINER"))
                .andExpect(jsonPath("$.scheduledStart").value("2026-07-20T17:30:00+02:00"))
                .andExpect(jsonPath("$.scheduledEnd").value("2026-07-20T18:30:00+02:00"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.items[0].availabilitySlotId").value(slot.getId()))
                .andExpect(jsonPath("$.items[0].scheduledStart").value("2026-07-20T17:30:00+02:00"))
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.slotStatus").doesNotExist())
                .andExpect(jsonPath("$.primaryGoal").doesNotExist())
                .andExpect(jsonPath("$.medicalNotes").doesNotExist())
                .andExpect(jsonPath("$.injuryNotes").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.gender").doesNotExist())
                .andExpect(jsonPath("$.heightCm").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.accountStatus").doesNotExist());

        BookingRequest booking = bookingRequestRepository.findAll().getFirst();

        mockMvc.perform(get("/api/v1/bookings/client").with(SessionAuthTestSupport.withSession(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterparty.displayName").value("Mario Rossi"))
                .andExpect(jsonPath("$[0].counterparty.specialization").value("PERSONAL_TRAINER"));
        mockMvc.perform(get("/api/v1/bookings/professional").with(SessionAuthTestSupport.withSession(professionalAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterparty.displayName").value("Luigi Bianchi"))
                .andExpect(jsonPath("$[0].counterparty.specialization").doesNotExist());
        mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId())
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()));
    }

    @Test
    void shouldKeepNamesAndScheduleStableWhileUsingCurrentProfileImagesAndHistoricalLinkAccess() throws Exception {
        AvailabilitySlot slot = saveSlot("2026-07-21T15:30:00Z", "2026-07-21T16:30:00Z");
        long bookingId = createBooking(slot);

        client.setFirstName("Giuseppe");
        client.setLastName("Neri");
        client.setProfileImageUrl("https://images.test/client-new.png");
        clientProfileRepository.saveAndFlush(client);
        professional.setFirstName("Andrea");
        professional.setLastName("Blu");
        professional.setProfileImageUrl("https://images.test/professional-new.png");
        professionalProfileRepository.saveAndFlush(professional);
        slot.setStartDateTime(Instant.parse("2026-07-30T15:30:00Z"));
        slot.setEndDateTime(Instant.parse("2026-07-30T16:30:00Z"));
        availabilitySlotRepository.saveAndFlush(slot);
        link.setActive(false);
        professionalClientLinkRepository.saveAndFlush(link);

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.displayName").value("Luigi Bianchi"))
                .andExpect(jsonPath("$.professional.displayName").value("Mario Rossi"))
                .andExpect(jsonPath("$.client.profileImageUrl").value("https://images.test/client-new.png"))
                .andExpect(jsonPath("$.professional.profileImageUrl").value("https://images.test/professional-new.png"))
                .andExpect(jsonPath("$.scheduledStart").value("2026-07-21T17:30:00+02:00"))
                .andExpect(jsonPath("$.scheduledEnd").value("2026-07-21T18:30:00+02:00"));
        mockMvc.perform(get("/api/v1/bookings/client").with(SessionAuthTestSupport.withSession(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId));
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/bookings")
                .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availabilitySlotId\":" + slot.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExposeTransitionTimestampsAndOrderSummariesByCreatedAtThenIdDescending() throws Exception {
        AvailabilitySlot firstSlot = saveSlot("2026-07-22T15:30:00Z", "2026-07-22T16:30:00Z");
        AvailabilitySlot secondSlot = saveSlot("2026-07-23T15:30:00Z", "2026-07-23T16:30:00Z");
        AvailabilitySlot thirdSlot = saveSlot("2026-07-24T15:30:00Z", "2026-07-24T16:30:00Z");
        long firstBookingId = createBooking(firstSlot);
        long secondBookingId = createBooking(secondSlot);
        long thirdBookingId = createBooking(thirdSlot);

        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", firstBookingId)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(professionalAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.rejectedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelledAt").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", firstBookingId)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-01-15T10:00:00Z"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", secondBookingId)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(professionalAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectedAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.confirmedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelledAt").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/v1/bookings/client").with(SessionAuthTestSupport.withSession(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(thirdBookingId))
                .andExpect(jsonPath("$[1].id").value(secondBookingId))
                .andExpect(jsonPath("$[2].id").value(firstBookingId));

        assertThat(availabilitySlotRepository.findById(firstSlot.getId()).orElseThrow().getStatus().name())
                .isEqualTo("AVAILABLE");
        assertThat(availabilitySlotRepository.findById(secondSlot.getId()).orElseThrow().getStatus().name())
                .isEqualTo("AVAILABLE");
    }

    @Test
    void shouldUseConstantQueryCountForClientSummaryList() throws Exception {
        createBooking(saveSlot("2026-07-25T15:30:00Z", "2026-07-25T16:30:00Z"));
        createBooking(saveSlot("2026-07-26T15:30:00Z", "2026-07-26T16:30:00Z"));
        createBooking(saveSlot("2026-07-27T15:30:00Z", "2026-07-27T16:30:00Z"));
        entityManager.flush();
        entityManager.clear();
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        sessionFactory.getStatistics().clear();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new it.zuperman.support_trainer.security.session.AuthenticatedUserPrincipal(
                                client.getId(),
                                client.getEmail()
                        ),
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("CLIENT"))
                )
        );

        assertThat(bookingService.getClientBookingRequests()).hasSize(3);
        assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private long createBooking(AvailabilitySlot slot) throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilitySlotId\":" + slot.getId() + "}"))
                .andExpect(status().isCreated());
        return bookingRequestRepository.findAll().stream()
                .map(BookingRequest::getId)
                .max(Long::compareTo)
                .orElseThrow();
    }

    private AvailabilitySlot saveSlot(String start, String end) {
        return availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                Instant.parse(start),
                Instant.parse(end)
        ));
    }

    private ProfessionalProfile activeProfessional(String email) {
        ProfessionalProfile value = new ProfessionalProfile(
                "Mario", "Rossi", email, passwordEncoder.encode(PASSWORD), ProfessionalSpecialization.PERSONAL_TRAINER
        );
        value.setAccountStatus(AccountStatus.ACTIVE);
        value.setEmailVerified(true);
        value.setActive(true);
        return value;
    }

    private ClientProfile activeClient(String email) {
        ClientProfile value = new ClientProfile(
                "Luigi", "Bianchi", email, passwordEncoder.encode(PASSWORD), LocalDate.of(1990, 1, 1),
                BigDecimal.valueOf(180), "Allenamento", Gender.MALE
        );
        value.setAccountStatus(AccountStatus.ACTIVE);
        value.setEmailVerified(true);
        value.setActive(true);
        return value;
    }

    private static RequestPostProcessor withCsrf(CsrfSession csrfSession) {
        return SessionAuthTestSupport.withCsrf(csrfSession);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock bookingHistoryClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
