package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.jwt.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    SupportTrainerApplication.class,
    AvailabilityTemporalContractIntegrationTest.FixedClockConfiguration.class
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class AvailabilityTemporalContractIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    @Autowired
    private JwtService jwtService;

    private ProfessionalProfile professional;
    private String professionalAuthorization;

    @BeforeEach
    void createProfessional() {
        professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                "temporal-professional@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        professional = professionalProfileRepository.saveAndFlush(professional);
        professionalAuthorization = bearer(professional);
    }

    @Test
    void shouldCreateAvailabilityWithOffsetAndPreserveIsoResponse() throws Exception {
        mockMvc.perform(post("/api/v1/availability")
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotPayload(
                                "2026-07-13T17:30:00+02:00",
                                "2026-07-13T18:30:00+02:00"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDateTime").value("2026-07-13T17:30:00+02:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-07-13T18:30:00+02:00"));
    }

    @Test
    void shouldUpdateAvailabilityAndListItWithOffset() throws Exception {
        AvailabilitySlot slot = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                LocalDateTime.parse("2026-01-20T17:30:00"),
                LocalDateTime.parse("2026-01-20T18:30:00")
        ));

        mockMvc.perform(patch("/api/v1/availability/{slotId}", slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDateTime\":\"2026-01-20T19:00:00+01:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDateTime").value("2026-01-20T17:30:00+01:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-01-20T19:00:00+01:00"));

        mockMvc.perform(get("/api/v1/availability/my")
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startDateTime").value("2026-01-20T17:30:00+01:00"))
                .andExpect(jsonPath("$[0].endDateTime").value("2026-01-20T19:00:00+01:00"));
    }

    @Test
    void shouldAcceptIncreasingInstantAcrossSpringOffsetChange() throws Exception {
        mockMvc.perform(post("/api/v1/availability")
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotPayload(
                                "2026-03-29T01:30:00+01:00",
                                "2026-03-29T03:00:00+02:00"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDateTime").value("2026-03-29T01:30:00+01:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-03-29T03:00:00+02:00"));
    }

    @ParameterizedTest
    @MethodSource("invalidSlotPayloads")
    void shouldRejectInvalidTemporalPayloads(String payload, String expectedErrorCode) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/availability")
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.message").isNotEmpty());

        if ("VALIDATION_ERROR".equals(expectedErrorCode)) {
            result.andExpect(jsonPath("$.validationErrors.startDateTime").isNotEmpty());
        }
    }

    @ParameterizedTest
    @MethodSource("invalidIntervals")
    void shouldRejectNonIncreasingIntervalsComparedAsInstants(String start, String end) throws Exception {
        mockMvc.perform(post("/api/v1/availability")
                        .header(HttpHeaders.AUTHORIZATION, professionalAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotPayload(start, end)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldExposeBookingSlotTimesWithBusinessOffsetWhileLeavingAuditContractUnchanged() throws Exception {
        ClientProfile client = createClient();
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, client));
        AvailabilitySlot slot = availabilitySlotRepository.saveAndFlush(new AvailabilitySlot(
                professional,
                LocalDateTime.parse("2026-07-20T17:30:00"),
                LocalDateTime.parse("2026-07-20T18:30:00")
        ));

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availabilitySlotId\":" + slot.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].startDateTime")
                        .value("2026-07-20T17:30:00+02:00"))
                .andExpect(jsonPath("$.items[0].endDateTime")
                        .value("2026-07-20T18:30:00+02:00"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    private static Stream<Arguments> invalidSlotPayloads() {
        return Stream.of(
                Arguments.of(slotPayload(
                        "2026-07-13T17:30:00+01:00",
                        "2026-07-13T18:30:00+02:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-01-20T17:30:00+02:00",
                        "2026-01-20T18:30:00+01:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-07-13T17:30:00Z",
                        "2026-07-13T18:30:00+02:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-07-13T17:30:00",
                        "2026-07-13T18:30:00+02:00"
                ), "MALFORMED_REQUEST"),
                Arguments.of(slotPayload(
                        "not-a-date",
                        "2026-07-13T18:30:00+02:00"
                ), "MALFORMED_REQUEST"),
                Arguments.of(slotPayload(
                        "2026-03-29T02:30:00+01:00",
                        "2026-03-29T04:00:00+02:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-10-25T02:30:00+02:00",
                        "2026-10-25T04:00:00+01:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-10-25T02:30:00+01:00",
                        "2026-10-25T04:00:00+01:00"
                ), "VALIDATION_ERROR"),
                Arguments.of(slotPayload(
                        "2026-07-13T17:30:00.123456+02:00",
                        "2026-07-13T18:30:00+02:00"
                ), "VALIDATION_ERROR")
        );
    }

    private static Stream<Arguments> invalidIntervals() {
        return Stream.of(
                Arguments.of(
                        "2026-07-13T17:30:00+02:00",
                        "2026-07-13T17:30:00+02:00"
                ),
                Arguments.of(
                        "2026-07-13T18:30:00+02:00",
                        "2026-07-13T17:30:00+02:00"
                )
        );
    }

    private ClientProfile createClient() {
        ClientProfile client = new ClientProfile(
                "Luigi",
                "Bianchi",
                "temporal-client@example.com",
                "encoded-password",
                LocalDate.of(1990, 1, 1),
                BigDecimal.valueOf(180),
                "Allenamento",
                Gender.MALE
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setActive(true);
        return clientProfileRepository.saveAndFlush(client);
    }

    private String bearer(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRole().name())
                .build();
        return "Bearer " + jwtService.generateAccessToken(userDetails);
    }

    private static String slotPayload(String startDateTime, String endDateTime) {
        return "{\"startDateTime\":\"" + startDateTime
                + "\",\"endDateTime\":\"" + endDateTime + "\"}";
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock temporalContractClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
