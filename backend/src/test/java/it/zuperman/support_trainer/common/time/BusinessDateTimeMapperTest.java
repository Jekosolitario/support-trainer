package it.zuperman.support_trainer.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.exception.AppException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessDateTimeMapperTest {

    private final BusinessDateTimeMapper mapper = new BusinessDateTimeMapper(
            new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"))
    );

    @Test
    void shouldAcceptSummerOffsetAndConvertRequestToLocalDateTime() {
        OffsetDateTime value = OffsetDateTime.parse("2026-07-13T17:30:00+02:00");

        assertThat(mapper.toBusinessLocalDateTime(value))
                .isEqualTo(LocalDateTime.parse("2026-07-13T17:30:00"));
    }

    @Test
    void shouldAcceptWinterOffsetAndConvertRequestToLocalDateTime() {
        OffsetDateTime value = OffsetDateTime.parse("2026-01-13T17:30:00+01:00");

        assertThat(mapper.toBusinessLocalDateTime(value))
                .isEqualTo(LocalDateTime.parse("2026-01-13T17:30:00"));
    }

    @Test
    void shouldMapSummerLocalDateTimeToExpectedOffset() {
        assertThat(mapper.toBusinessOffsetDateTime(LocalDateTime.parse("2026-07-13T17:30:00")))
                .isEqualTo(OffsetDateTime.parse("2026-07-13T17:30:00+02:00"));
    }

    @Test
    void shouldMapWinterLocalDateTimeToExpectedOffset() {
        assertThat(mapper.toBusinessOffsetDateTime(LocalDateTime.parse("2026-01-13T17:30:00")))
                .isEqualTo(OffsetDateTime.parse("2026-01-13T17:30:00+01:00"));
    }

    @Test
    void shouldRejectWrongSummerOffset() {
        assertInvalidRequest("2026-07-13T17:30:00+01:00");
    }

    @Test
    void shouldRejectWrongWinterOffset() {
        assertInvalidRequest("2026-01-13T17:30:00+02:00");
    }

    @Test
    void shouldRejectUtcOffsetWhenNotValidForRome() {
        assertInvalidRequest("2026-07-13T17:30:00Z");
    }

    @Test
    void shouldRejectSpringGapWithoutNormalizingIt() {
        assertInvalidRequest("2026-03-29T02:30:00+01:00");
    }

    @Test
    void shouldRejectAutumnOverlapWithSummerOffset() {
        assertInvalidRequest("2026-10-25T02:30:00+02:00");
    }

    @Test
    void shouldRejectAutumnOverlapWithWinterOffset() {
        assertInvalidRequest("2026-10-25T02:30:00+01:00");
    }

    @Test
    void shouldAcceptWholeSeconds() {
        assertThatCode(() -> mapper.validateRequestDateTime(
                OffsetDateTime.parse("2026-07-13T17:30:59+02:00")
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFractionsWithoutTruncatingOrRounding() {
        assertInvalidRequest("2026-07-13T17:30:00.123456+02:00");
    }

    @Test
    void shouldRejectPersistedSpringGapWithoutInventingOffset() {
        assertInvalidStoredValue(LocalDateTime.parse("2026-03-29T02:30:00"));
    }

    @Test
    void shouldRejectPersistedAutumnOverlapWithoutChoosingOffset() {
        assertInvalidStoredValue(LocalDateTime.parse("2026-10-25T02:30:00"));
    }

    @Test
    void shouldRejectPersistedFractionsWithoutRounding() {
        assertInvalidStoredValue(LocalDateTime.parse("2026-07-13T17:30:00.000001"));
    }

    @Test
    void shouldBeIndependentFromJvmDefaultTimezone() {
        TimeZone originalDefault = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

            assertThat(mapper.toBusinessOffsetDateTime(LocalDateTime.parse("2026-07-13T17:30:00")))
                    .isEqualTo(OffsetDateTime.parse("2026-07-13T17:30:00+02:00"));
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    private void assertInvalidRequest(String value) {
        assertThatThrownBy(() -> mapper.toBusinessLocalDateTime(OffsetDateTime.parse(value)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    private void assertInvalidStoredValue(LocalDateTime value) {
        assertThatThrownBy(() -> mapper.toBusinessOffsetDateTime(value))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(500);
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_STORED_SLOT_DATETIME");
                });
    }
}
