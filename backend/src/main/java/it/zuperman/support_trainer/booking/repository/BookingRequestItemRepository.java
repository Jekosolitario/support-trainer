package it.zuperman.support_trainer.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;

public interface BookingRequestItemRepository extends JpaRepository<BookingRequestItem, Long> {

    List<BookingRequestItem> findAllByBookingRequest_Id(Long bookingRequestId);

    boolean existsByAvailabilitySlot_Id(Long availabilitySlotId);

    boolean existsByAvailabilitySlot_IdAndBookingRequest_StatusAndBookingRequest_ActiveTrue(
            Long availabilitySlotId,
            BookingRequestStatus status
    );

    @Query("SELECT COUNT(item.id) FROM BookingRequestItem item "
            + "WHERE item.availabilitySlot.id = :availabilitySlotId "
            + "AND item.bookingRequest.active = true "
            + "AND item.bookingRequest.status IN :occupyingStatuses")
    long countOccupiedCapacity(
            @Param("availabilitySlotId") Long availabilitySlotId,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );

    @Query("SELECT COUNT(DISTINCT item.bookingRequest.id) FROM BookingRequestItem item "
            + "WHERE item.availabilitySlot.weeklyRule.id = :weeklyRuleId "
            + "AND item.scheduledStart > :effectiveFrom "
            + "AND item.bookingRequest.active = true "
            + "AND item.bookingRequest.status IN :occupyingStatuses")
    long countImpactedBookings(
            @Param("weeklyRuleId") Long weeklyRuleId,
            @Param("effectiveFrom") Instant effectiveFrom,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );

    @Query("SELECT CASE WHEN COUNT(item.id) > 0 THEN true ELSE false END "
            + "FROM BookingRequestItem item "
            + "WHERE item.availabilitySlot.professional.id = :professionalId "
            + "AND item.availabilitySlot.startDateTime < :endDateTime "
            + "AND item.availabilitySlot.endDateTime > :startDateTime "
            + "AND item.bookingRequest.active = true "
            + "AND item.bookingRequest.status IN :occupyingStatuses")
    boolean existsOccupyingBookingOverlapping(
            @Param("professionalId") Long professionalId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );

    @Query("SELECT item FROM BookingRequestItem item "
            + "WHERE item.bookingRequest.professional.id = :professionalId "
            + "AND item.scheduledStart < :endDateTime "
            + "AND item.scheduledEnd > :startDateTime "
            + "AND item.bookingRequest.active = true "
            + "AND item.bookingRequest.status IN :occupyingStatuses")
    List<BookingRequestItem> findOccupyingBookingsOverlappingProfessional(
            @Param("professionalId") Long professionalId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );

    @Query("SELECT item FROM BookingRequestItem item "
            + "JOIN FETCH item.bookingRequest bookingRequest "
            + "WHERE bookingRequest.professional.id = :professionalId "
            + "AND item.scheduledStart < :rangeEnd "
            + "AND item.scheduledEnd > :rangeStart "
            + "AND bookingRequest.active = true "
            + "AND bookingRequest.status IN :occupyingStatuses")
    List<BookingRequestItem> findOccupyingBookingsForAvailabilityRange(
            @Param("professionalId") Long professionalId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );

    @Query("SELECT CASE WHEN COUNT(item.id) > 0 THEN true ELSE false END "
            + "FROM BookingRequestItem item "
            + "WHERE item.bookingRequest.client.id = :clientId "
            + "AND item.bookingRequest.professional.id = :professionalId "
            + "AND item.scheduledStart < :endDateTime "
            + "AND item.scheduledEnd > :startDateTime "
            + "AND item.bookingRequest.active = true "
            + "AND item.bookingRequest.status IN :occupyingStatuses")
    boolean existsOccupyingBookingForClientOverlappingProfessional(
            @Param("clientId") Long clientId,
            @Param("professionalId") Long professionalId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime,
            @Param("occupyingStatuses") Set<BookingRequestStatus> occupyingStatuses
    );
}
