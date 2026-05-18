package it.zuperman.support_trainer.booking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;

public interface BookingRequestItemRepository extends JpaRepository<BookingRequestItem, Long> {

    List<BookingRequestItem> findAllByBookingRequest_Id(Long bookingRequestId);

    boolean existsByAvailabilitySlot_IdAndBookingRequest_StatusAndBookingRequest_ActiveTrue(
            Long availabilitySlotId,
            BookingRequestStatus status
    );
}