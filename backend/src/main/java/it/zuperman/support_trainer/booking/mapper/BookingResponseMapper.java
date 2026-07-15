package it.zuperman.support_trainer.booking.mapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingItemResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingParticipantResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingSummaryResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

@Component
public class BookingResponseMapper {

    private final BusinessDateTimeMapper businessDateTimeMapper;

    public BookingResponseMapper(BusinessDateTimeMapper businessDateTimeMapper) {
        this.businessDateTimeMapper = businessDateTimeMapper;
    }

    public BookingSummaryResponse toClientSummary(BookingRequest bookingRequest) {
        BookingTemporalData temporalData = temporalData(bookingRequest);

        return new BookingSummaryResponse(
                bookingRequest.getId(),
                status(bookingRequest),
                professionalParticipant(bookingRequest),
                toBusinessOffset(temporalData.scheduledStart()),
                toBusinessOffset(temporalData.scheduledEnd()),
                temporalData.durationMinutes(),
                bookingRequest.getNote(),
                bookingRequest.getCreatedAt()
        );
    }

    public BookingSummaryResponse toProfessionalSummary(BookingRequest bookingRequest) {
        BookingTemporalData temporalData = temporalData(bookingRequest);

        return new BookingSummaryResponse(
                bookingRequest.getId(),
                status(bookingRequest),
                clientParticipant(bookingRequest),
                toBusinessOffset(temporalData.scheduledStart()),
                toBusinessOffset(temporalData.scheduledEnd()),
                temporalData.durationMinutes(),
                bookingRequest.getNote(),
                bookingRequest.getCreatedAt()
        );
    }

    public BookingDetailResponse toDetail(BookingRequest bookingRequest) {
        BookingTemporalData temporalData = temporalData(bookingRequest);

        return new BookingDetailResponse(
                bookingRequest.getId(),
                status(bookingRequest),
                clientParticipant(bookingRequest),
                professionalParticipant(bookingRequest),
                toBusinessOffset(temporalData.scheduledStart()),
                toBusinessOffset(temporalData.scheduledEnd()),
                temporalData.durationMinutes(),
                bookingRequest.getNote(),
                bookingRequest.getCreatedAt(),
                bookingRequest.getUpdatedAt(),
                bookingRequest.getConfirmedAt(),
                bookingRequest.getRejectedAt(),
                bookingRequest.getCancelledAt(),
                temporalData.items()
        );
    }

    private BookingParticipantResponse clientParticipant(BookingRequest bookingRequest) {
        return new BookingParticipantResponse(
                bookingRequest.getClient().getId(),
                requiredDisplayName(bookingRequest.getClientDisplayName()),
                bookingRequest.getClient().getProfileImageUrl(),
                null
        );
    }

    private BookingParticipantResponse professionalParticipant(BookingRequest bookingRequest) {
        ProfessionalProfile professional = bookingRequest.getProfessional();

        return new BookingParticipantResponse(
                professional.getId(),
                requiredDisplayName(bookingRequest.getProfessionalDisplayName()),
                professional.getProfileImageUrl(),
                professional.getSpecialization() == null ? null : professional.getSpecialization().name()
        );
    }

    private BookingTemporalData temporalData(BookingRequest bookingRequest) {
        if (bookingRequest.getItems() == null || bookingRequest.getItems().isEmpty()) {
            throw inconsistentHistory();
        }

        List<BookingRequestItem> sortedItems = new ArrayList<>(bookingRequest.getItems());
        for (BookingRequestItem item : sortedItems) {
            validateItem(item);
        }
        sortedItems.sort(Comparator
                .comparing(BookingRequestItem::getScheduledStart)
                .thenComparing(BookingRequestItem::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        Instant scheduledStart = sortedItems.getFirst().getScheduledStart();
        Instant scheduledEnd = sortedItems.stream()
                .map(BookingRequestItem::getScheduledEnd)
                .max(Instant::compareTo)
                .orElseThrow(this::inconsistentHistory);
        long durationMinutes = 0;
        List<BookingItemResponse> items = new ArrayList<>(sortedItems.size());

        for (BookingRequestItem item : sortedItems) {
            long itemDurationMinutes = Duration.between(item.getScheduledStart(), item.getScheduledEnd()).toMinutes();
            durationMinutes += itemDurationMinutes;
            items.add(new BookingItemResponse(
                    item.getId(),
                    item.getAvailabilitySlot().getId(),
                    toBusinessOffset(item.getScheduledStart()),
                    toBusinessOffset(item.getScheduledEnd()),
                    itemDurationMinutes
            ));
        }

        return new BookingTemporalData(
                scheduledStart,
                scheduledEnd,
                durationMinutes,
                List.copyOf(items)
        );
    }

    private void validateItem(BookingRequestItem item) {
        if (item == null
                || item.getAvailabilitySlot() == null
                || item.getScheduledStart() == null
                || item.getScheduledEnd() == null
                || !item.getScheduledEnd().isAfter(item.getScheduledStart())) {
            throw inconsistentHistory();
        }
    }

    private OffsetDateTime toBusinessOffset(Instant value) {
        return businessDateTimeMapper.toBusinessOffsetDateTime(value);
    }

    private String status(BookingRequest bookingRequest) {
        if (bookingRequest.getStatus() == null) {
            throw inconsistentHistory();
        }
        return bookingRequest.getStatus().name();
    }

    private String requiredDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw inconsistentHistory();
        }
        return displayName;
    }

    private AppException inconsistentHistory() {
        return new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "BOOKING_HISTORY_INCONSISTENT",
                "La prenotazione non è disponibile"
        );
    }

    private record BookingTemporalData(
            Instant scheduledStart,
            Instant scheduledEnd,
            long durationMinutes,
            List<BookingItemResponse> items
    ) {
    }
}
