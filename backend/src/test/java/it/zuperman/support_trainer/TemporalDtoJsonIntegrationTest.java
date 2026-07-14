package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import it.zuperman.support_trainer.booking.dto.response.BookingRequestItemResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingRequestResponse;
import it.zuperman.support_trainer.invite.dto.response.InviteCodeResponse;
import it.zuperman.support_trainer.invite.dto.response.ValidateInviteCodeResponse;
import it.zuperman.support_trainer.profile.dto.response.MyAccountResponse;
import it.zuperman.support_trainer.profile.dto.response.MyProfileResponse;

@SpringBootTest
@ActiveProfiles("test")
class TemporalDtoJsonIntegrationTest {

    private static final Instant INSTANT = Instant.parse("2026-07-13T15:30:45.123456Z");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeAuditAndExpiryInstantsWithUtcZ() throws Exception {
        InviteCodeResponse invite = new InviteCodeResponse(
                1L, "CODE", 2L, INSTANT, false, null, true, INSTANT
        );
        ValidateInviteCodeResponse validation = new ValidateInviteCodeResponse(true, "CODE", 2L, INSTANT);
        MyAccountResponse account = new MyAccountResponse(
                3L, "user@example.com", "CLIENT", "ACTIVE", true, INSTANT, INSTANT
        );

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(invite)).get("expiresAt").stringValue())
                .isEqualTo("2026-07-13T15:30:45.123456Z");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(validation)).get("expiresAt").stringValue())
                .isEqualTo("2026-07-13T15:30:45.123456Z");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(account)).get("createdAt").stringValue())
                .isEqualTo("2026-07-13T15:30:45.123456Z");
    }

    @Test
    void shouldKeepBookingSlotOffsetWhileSerializingBookingAuditAsUtc() throws Exception {
        BookingRequestItemResponse item = new BookingRequestItemResponse(
                10L,
                20L,
                OffsetDateTime.parse("2026-07-13T17:30:00+02:00"),
                OffsetDateTime.parse("2026-07-13T18:30:00+02:00"),
                "AVAILABLE"
        );
        BookingRequestResponse booking = new BookingRequestResponse(
                30L, 40L, 50L, "PENDING", null, true, INSTANT, INSTANT, List.of(item)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(booking));
        assertThat(json.get("createdAt").stringValue()).isEqualTo("2026-07-13T15:30:45.123456Z");
        assertThat(json.at("/items/0/startDateTime").stringValue()).isEqualTo("2026-07-13T17:30:00+02:00");
    }

    @Test
    void shouldKeepCivilBirthDateWithoutTimezoneConversion() throws Exception {
        MyProfileResponse profile = new MyProfileResponse();
        profile.setBirthDate(LocalDate.parse("1998-05-10"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(profile));
        assertThat(json.get("birthDate").stringValue()).isEqualTo("1998-05-10");
    }
}
