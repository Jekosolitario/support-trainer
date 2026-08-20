package it.zuperman.support_trainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
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
    BookingTransitionContractIntegrationTest.FixedClockConfiguration.class
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class BookingTransitionContractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalRepository;

    @Autowired
    private ClientProfileRepository clientRepository;

    @Autowired
    private ProfessionalClientLinkRepository linkRepository;

    @Autowired
    private AvailabilitySlotRepository slotRepository;

    @Autowired
    private BookingRequestRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private ProfessionalProfile professional;
    private ProfessionalProfile otherProfessional;
    private ProfessionalProfile nutritionist;
    private ClientProfile client;
    private ClientProfile otherClient;
    private CsrfSession professionalAuth;
    private CsrfSession otherProfessionalAuth;
    private CsrfSession nutritionistAuth;
    private CsrfSession clientAuth;
    private CsrfSession otherClientAuth;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        professional = professionalRepository.saveAndFlush(professional(
                "pt-" + suffix + "@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        ));
        otherProfessional = professionalRepository.saveAndFlush(professional(
                "other-pt-" + suffix + "@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        ));
        nutritionist = professionalRepository.saveAndFlush(professional(
                "nutritionist-" + suffix + "@example.com",
                ProfessionalSpecialization.NUTRITIONIST
        ));
        client = clientRepository.saveAndFlush(client("client-" + suffix + "@example.com"));
        otherClient = clientRepository.saveAndFlush(client("other-client-" + suffix + "@example.com"));
        linkRepository.saveAndFlush(new ProfessionalClientLink(professional, client));

        professionalAuth = login(professional.getEmail());
        otherProfessionalAuth = login(otherProfessional.getEmail());
        nutritionistAuth = login(nutritionist.getEmail());
        clientAuth = login(client.getEmail());
        otherClientAuth = login(otherClient.getEmail());
    }

    @Test
    void rejectShouldRequireValidateTrimAndPersistReason() throws Exception {
        long validId = booking(client, professional, NOW.plusSeconds(60), NOW.plusSeconds(3600)).getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", validId)
                        .with(auth(professionalAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Agenda completa  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Agenda completa"));

        assertInvalidReason("/reject", bookingFuture().getId(), "{\"reason\":null}", professionalAuth);
        assertInvalidReason("/reject", bookingFuture().getId(), "{\"reason\":\"   \"}", professionalAuth);
        assertInvalidReason(
                "/reject",
                bookingFuture().getId(),
                "{\"reason\":\"" + "x".repeat(1001) + "\"}",
                professionalAuth
        );
    }

    @Test
    void clientPendingCancellationShouldAcceptEveryOptionalBodyShapeAndSetActor() throws Exception {
        cancelPendingWithoutBody(bookingFuture().getId());
        cancelPending(bookingFuture().getId(), "{}", null);
        cancelPending(bookingFuture().getId(), "{\"reason\":null}", null);
        cancelPending(bookingFuture().getId(), "{\"reason\":\"   \"}", null);
        cancelPending(bookingFuture().getId(), "{\"reason\":\"  Cambio programma  \"}", "Cambio programma");
    }

    @Test
    void clientConfirmedCancellationShouldRequireReasonAndSetClientActor() throws Exception {
        assertConfirmedCancelRequiresReason(clientAuth, confirmedFuture().getId(), null);
        assertConfirmedCancelRequiresReason(clientAuth, confirmedFuture().getId(), "{\"reason\":null}");
        assertConfirmedCancelRequiresReason(clientAuth, confirmedFuture().getId(), "{\"reason\":\"  \"}");
        assertInvalidReason(
                "/cancel",
                confirmedFuture().getId(),
                "{\"reason\":\"" + "x".repeat(1001) + "\"}",
                clientAuth
        );

        long bookingId = confirmedFuture().getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId)
                        .with(auth(clientAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Imprevisto  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationReason").value("Imprevisto"))
                .andExpect(jsonPath("$.cancelledBy").value("CLIENT"));
    }

    @Test
    void professionalConfirmedCancellationShouldRequireReasonAndSetProfessionalActor() throws Exception {
        assertConfirmedCancelRequiresReason(professionalAuth, confirmedFuture().getId(), null);

        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", confirmedFuture().getId())
                        .with(auth(professionalAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Studio chiuso  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationReason").value("Studio chiuso"))
                .andExpect(jsonPath("$.cancelledBy").value("PROFESSIONAL"));
    }

    @Test
    void exactEndAndAfterEndShouldRejectEveryLifecycleMutation() throws Exception {
        assertEnded("/confirm", booking(client, professional, NOW.minusSeconds(3600), NOW).getId(), null);
        assertEnded("/reject", booking(client, professional, NOW.minusSeconds(3600), NOW).getId(), "{\"reason\":\"No\"}");
        assertEnded("/cancel", confirmed(client, professional, NOW.minusSeconds(3600), NOW).getId(), "{\"reason\":\"No\"}");
        assertEnded("/cancel", confirmed(client, professional, NOW.minusSeconds(7200), NOW.minusSeconds(1)).getId(), "{\"reason\":\"No\"}");
    }

    @Test
    void inProgressBookingShouldAllowConfirmRejectAndCancelAccordingToLifecycle() throws Exception {
        long confirmId = bookingInProgress().getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", confirmId).with(auth(professionalAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        long rejectId = bookingInProgress().getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", rejectId)
                        .with(auth(professionalAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Non disponibile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        long clientCancelId = confirmed(client, professional, NOW.minusSeconds(60), NOW.plusSeconds(60)).getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", clientCancelId)
                        .with(auth(clientAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Devo uscire\"}"))
                .andExpect(status().isOk());

        long professionalCancelId = confirmed(client, professional, NOW.minusSeconds(60), NOW.plusSeconds(60)).getId();
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", professionalCancelId)
                        .with(auth(professionalAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Emergenza\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void ownershipShouldRemainNeutralForOtherClientAndOtherPersonalTrainer() throws Exception {
        long bookingId = bookingFuture().getId();
        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId).with(SessionAuthTestSupport.withSession(otherClientAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", bookingId).with(auth(otherProfessionalAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", bookingId)
                        .with(auth(otherProfessionalAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_NOT_FOUND"));
    }

    @Test
    void nutritionistShouldReceiveForbiddenBeforeBookingInferenceForEveryProfessionalCapability() throws Exception {
        long bookingId = bookingFuture().getId();
        mockMvc.perform(get("/api/v1/bookings/professional").with(SessionAuthTestSupport.withSession(nutritionistAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId).with(SessionAuthTestSupport.withSession(nutritionistAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", bookingId).with(auth(nutritionistAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/bookings/{id}/reject", bookingId)
                        .with(auth(nutritionistAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId).with(auth(nutritionistAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void incompatibleLifecycleAndLegacyNullMetadataShouldRemainSupported() throws Exception {
        long rejectedId = bookingFuture().getId();
        jdbcTemplate.update(
                "UPDATE booking_requests SET status = 'REJECTED', rejected_at = ?, rejection_reason = NULL WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(1)),
                rejectedId
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/bookings/{id}", rejectedId)
                        .with(SessionAuthTestSupport.withSession(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelledBy").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", rejectedId).with(auth(professionalAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_INVALID_TRANSITION"));
    }

    private void cancelPendingWithoutBody(long bookingId) throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId).with(auth(clientAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelledBy").value("CLIENT"));
    }

    private void cancelPending(long bookingId, String body, String expectedReason) throws Exception {
        var result = mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", bookingId)
                        .with(auth(clientAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledBy").value("CLIENT"));
        if (expectedReason == null) {
            result.andExpect(jsonPath("$.cancellationReason").value(org.hamcrest.Matchers.nullValue()));
        } else {
            result.andExpect(jsonPath("$.cancellationReason").value(expectedReason));
        }
    }

    private void assertConfirmedCancelRequiresReason(CsrfSession auth, long bookingId, String body) throws Exception {
        var request = patch("/api/v1/bookings/{id}/cancel", bookingId).with(auth(auth));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reason')]").isNotEmpty());
    }

    private void assertInvalidReason(String suffix, long bookingId, String body, CsrfSession auth) throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/{id}" + suffix, bookingId)
                        .with(auth(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reason')]").isNotEmpty());
    }

    private void assertEnded(String suffix, long bookingId, String body) throws Exception {
        CsrfSession actor = suffix.equals("/cancel") ? clientAuth : professionalAuth;
        var request = patch("/api/v1/bookings/{id}" + suffix, bookingId).with(auth(actor));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_ENDED"));
    }

    private BookingRequest bookingFuture() {
        return booking(client, professional, NOW.plusSeconds(60), NOW.plusSeconds(3600));
    }

    private BookingRequest confirmedFuture() {
        return confirmed(client, professional, NOW.plusSeconds(60), NOW.plusSeconds(3600));
    }

    private BookingRequest bookingInProgress() {
        return booking(client, professional, NOW.minusSeconds(60), NOW.plusSeconds(60));
    }

    private BookingRequest confirmed(
            ClientProfile bookingClient,
            ProfessionalProfile bookingProfessional,
            Instant start,
            Instant end
    ) {
        BookingRequest booking = booking(bookingClient, bookingProfessional, start, end);
        booking.confirm(NOW.minusSeconds(120));
        return bookingRepository.saveAndFlush(booking);
    }

    private BookingRequest booking(
            ClientProfile bookingClient,
            ProfessionalProfile bookingProfessional,
            Instant start,
            Instant end
    ) {
        AvailabilitySlot slot = slotRepository.saveAndFlush(new AvailabilitySlot(
                bookingProfessional,
                start,
                end
        ));
        BookingRequest booking = new BookingRequest(
                bookingClient,
                bookingProfessional,
                null,
                bookingClient.getFirstName() + " " + bookingClient.getLastName(),
                bookingProfessional.getFirstName() + " " + bookingProfessional.getLastName()
        );
        booking.getItems().add(new BookingRequestItem(booking, slot, start, end));
        return bookingRepository.saveAndFlush(booking);
    }

    private ProfessionalProfile professional(String email, ProfessionalSpecialization specialization) {
        ProfessionalProfile value = new ProfessionalProfile(
                "Mario", "Rossi", email, passwordEncoder.encode(PASSWORD), specialization
        );
        value.setAccountStatus(AccountStatus.ACTIVE);
        value.setEmailVerified(true);
        value.setActive(true);
        return value;
    }

    private ClientProfile client(String email) {
        ClientProfile value = new ClientProfile(
                "Luigi", "Bianchi", email, passwordEncoder.encode(PASSWORD), LocalDate.of(1990, 1, 1),
                BigDecimal.valueOf(180), "Allenamento", Gender.MALE
        );
        value.setAccountStatus(AccountStatus.ACTIVE);
        value.setEmailVerified(true);
        value.setActive(true);
        return value;
    }

    private CsrfSession login(String email) throws Exception {
        return SessionAuthTestSupport.loginAndRefreshCsrf(mockMvc, email, PASSWORD);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth(CsrfSession session) {
        return SessionAuthTestSupport.withSessionAndCsrf(session);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock bookingTransitionClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
