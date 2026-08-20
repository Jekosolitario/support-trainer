package it.zuperman.support_trainer.booking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.enums.BookingCancellationActor;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;

class BookingRequestTest {

    private static final Instant TRANSITION_TIME = Instant.parse("2026-08-20T10:00:00Z");

    @Test
    void statusMustNotExposeAPublicSetter() {
        assertThat(Arrays.stream(BookingRequest.class.getMethods()).map(Method::getName))
                .doesNotContain("setStatus");
    }

    @Test
    void confirmMustRejectNullTimestampBeforeMutation() {
        BookingRequest pending = booking();

        assertThatThrownBy(() -> pending.confirm(null))
                .isInstanceOf(NullPointerException.class);

        assertPendingWithoutTransitionMetadata(pending);
    }

    @Test
    void rejectMustRejectNullTimestampBeforeMutation() {
        BookingRequest pending = booking();

        assertThatThrownBy(() -> pending.reject(null, "Agenda completa"))
                .isInstanceOf(NullPointerException.class);

        assertPendingWithoutTransitionMetadata(pending);
    }

    @Test
    void cancelMustRejectNullTimestampBeforeMutation() {
        BookingRequest pending = booking();

        assertThatThrownBy(() -> pending.cancel(
                null,
                "Imprevisto",
                BookingCancellationActor.CLIENT
        )).isInstanceOf(NullPointerException.class);

        assertPendingWithoutTransitionMetadata(pending);
    }

    @Test
    void rejectMustRequireAValidReason() {
        assertThatThrownBy(() -> booking().reject(TRANSITION_TIME, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> booking().reject(TRANSITION_TIME, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> booking().reject(TRANSITION_TIME, "x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);

        BookingRequest rejected = booking();
        rejected.reject(TRANSITION_TIME, "Agenda completa");

        assertThat(rejected.getStatus()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(rejected.getRejectedAt()).isEqualTo(TRANSITION_TIME);
        assertThat(rejected.getRejectionReason()).isEqualTo("Agenda completa");
    }

    @Test
    void cancelMustRequireAnActorAndKeepPendingReasonOptional() {
        assertThatThrownBy(() -> booking().cancel(TRANSITION_TIME, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> booking().cancel(
                TRANSITION_TIME,
                "x".repeat(1001),
                BookingCancellationActor.CLIENT
        )).isInstanceOf(IllegalArgumentException.class);

        BookingRequest cancelled = booking();
        cancelled.cancel(TRANSITION_TIME, null, BookingCancellationActor.CLIENT);

        assertThat(cancelled.getStatus()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(TRANSITION_TIME);
        assertThat(cancelled.getCancellationReason()).isNull();
        assertThat(cancelled.getCancelledBy()).isEqualTo(BookingCancellationActor.CLIENT);
    }

    private BookingRequest booking() {
        return new BookingRequest(null, null, null, "Cliente Test", "Personal Trainer Test");
    }

    private void assertPendingWithoutTransitionMetadata(BookingRequest bookingRequest) {
        assertThat(bookingRequest.getStatus()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(bookingRequest.getConfirmedAt()).isNull();
        assertThat(bookingRequest.getRejectedAt()).isNull();
        assertThat(bookingRequest.getCancelledAt()).isNull();
        assertThat(bookingRequest.getRejectionReason()).isNull();
        assertThat(bookingRequest.getCancellationReason()).isNull();
        assertThat(bookingRequest.getCancelledBy()).isNull();
    }
}
