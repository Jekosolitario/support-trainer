package it.zuperman.support_trainer.booking.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingSummaryResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

class BookingResponseMapperTest {

    private final BookingResponseMapper mapper = new BookingResponseMapper(
            new BusinessDateTimeMapper(new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC")))
    );

    @Test
    void shouldMapSummaryAndDetailFromSnapshotsWithOrderedItemsAndCurrentImage() {
        ClientProfile client = client();
        client.setProfileImageUrl(null);
        ProfessionalProfile professional = professional();
        professional.setProfileImageUrl("https://images.test/professional.png");
        BookingRequest booking = new BookingRequest(
                client,
                professional,
                "Nota booking",
                "Luigi Bianchi",
                "Mario Rossi"
        );
        AvailabilitySlot earlySlot = new AvailabilitySlot(
                professional,
                Instant.parse("2026-07-20T07:00:00Z"),
                Instant.parse("2026-07-20T08:00:00Z")
        );
        AvailabilitySlot lateSlot = new AvailabilitySlot(
                professional,
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-20T10:30:00Z")
        );
        booking.getItems().add(new BookingRequestItem(
                booking,
                lateSlot,
                lateSlot.getStartDateTime(),
                lateSlot.getEndDateTime()
        ));
        booking.getItems().add(new BookingRequestItem(
                booking,
                earlySlot,
                earlySlot.getStartDateTime(),
                earlySlot.getEndDateTime()
        ));

        BookingSummaryResponse clientSummary = mapper.toClientSummary(booking);
        BookingSummaryResponse professionalSummary = mapper.toProfessionalSummary(booking);
        BookingDetailResponse detail = mapper.toDetail(booking);

        assertThat(clientSummary.getCounterparty().getDisplayName()).isEqualTo("Mario Rossi");
        assertThat(clientSummary.getCounterparty().getSpecialization()).isEqualTo("PERSONAL_TRAINER");
        assertThat(professionalSummary.getCounterparty().getDisplayName()).isEqualTo("Luigi Bianchi");
        assertThat(professionalSummary.getCounterparty().getSpecialization()).isNull();
        assertThat(detail.getProfessional().getProfileImageUrl()).isEqualTo("https://images.test/professional.png");
        assertThat(detail.getClient().getProfileImageUrl()).isNull();
        assertThat(detail.getScheduledStart().toString()).isEqualTo("2026-07-20T09:00+02:00");
        assertThat(detail.getScheduledEnd().toString()).isEqualTo("2026-07-20T12:30+02:00");
        assertThat(detail.getDurationMinutes()).isEqualTo(150);
        assertThat(detail.getItems()).extracting(item -> item.getScheduledStart().toString())
                .containsExactly("2026-07-20T09:00+02:00", "2026-07-20T11:00+02:00");
    }

    @Test
    void shouldRejectMissingOrIncoherentHistoricalItems() {
        BookingRequest emptyBooking = booking();

        assertThatThrownBy(() -> mapper.toDetail(emptyBooking))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("BOOKING_HISTORY_INCONSISTENT"));

        AvailabilitySlot slot = new AvailabilitySlot(
                emptyBooking.getProfessional(),
                Instant.parse("2026-07-20T08:00:00Z"),
                Instant.parse("2026-07-20T09:00:00Z")
        );
        emptyBooking.getItems().add(new BookingRequestItem(
                emptyBooking,
                slot,
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-20T08:00:00Z")
        ));

        assertThatThrownBy(() -> mapper.toDetail(emptyBooking))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("BOOKING_HISTORY_INCONSISTENT"));
    }

    private static BookingRequest booking() {
        return new BookingRequest(client(), professional(), null, "Luigi Bianchi", "Mario Rossi");
    }

    private static ClientProfile client() {
        return new ClientProfile(
                "Luigi",
                "Bianchi",
                "client@example.com",
                "encoded-password",
                LocalDate.of(1990, 1, 1),
                BigDecimal.valueOf(180),
                "Allenamento",
                Gender.MALE
        );
    }

    private static ProfessionalProfile professional() {
        return new ProfessionalProfile(
                "Mario",
                "Rossi",
                "professional@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
    }
}
