package it.zuperman.support_trainer.booking.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;

public class BookingRequestResponse {

    private Long id;
    private Long clientId;
    private Long professionalId;
    private String status;
    private String note;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BookingRequestItemResponse> items;

    public BookingRequestResponse() {
    }

    public BookingRequestResponse(
            Long id,
            Long clientId,
            Long professionalId,
            String status,
            String note,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<BookingRequestItemResponse> items
    ) {
        this.id = id;
        this.clientId = clientId;
        this.professionalId = professionalId;
        this.status = status;
        this.note = note;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.items = items;
    }

    public static BookingRequestResponse fromEntity(
            BookingRequest bookingRequest,
            BusinessDateTimeMapper businessDateTimeMapper
    ) {
        return new BookingRequestResponse(
                bookingRequest.getId(),
                bookingRequest.getClient().getId(),
                bookingRequest.getProfessional().getId(),
                bookingRequest.getStatus() != null ? bookingRequest.getStatus().name() : null,
                bookingRequest.getNote(),
                bookingRequest.getActive(),
                bookingRequest.getCreatedAt(),
                bookingRequest.getUpdatedAt(),
                bookingRequest.getItems()
                        .stream()
                        .map(item -> BookingRequestItemResponse.fromEntity(item, businessDateTimeMapper))
                        .toList()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getClientId() {
        return clientId;
    }

    public Long getProfessionalId() {
        return professionalId;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<BookingRequestItemResponse> getItems() {
        return items;
    }
}
