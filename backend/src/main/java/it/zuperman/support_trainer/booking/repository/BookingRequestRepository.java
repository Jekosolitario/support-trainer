package it.zuperman.support_trainer.booking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import jakarta.persistence.LockModeType;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    @EntityGraph(attributePaths = {"client", "professional", "items", "items.availabilitySlot"})
    Optional<BookingRequest> findByIdAndActiveTrue(Long bookingRequestId);

    @EntityGraph(attributePaths = {"client", "professional", "items", "items.availabilitySlot"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bookingRequest FROM BookingRequest bookingRequest "
            + "WHERE bookingRequest.id = :bookingRequestId "
            + "AND bookingRequest.active = true")
    Optional<BookingRequest> findActiveByIdForUpdate(
            @Param("bookingRequestId") Long bookingRequestId
    );

    @EntityGraph(attributePaths = {"client", "professional", "items", "items.availabilitySlot"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bookingRequest FROM BookingRequest bookingRequest "
            + "WHERE bookingRequest.id = :bookingRequestId "
            + "AND bookingRequest.professional.id = :professionalId "
            + "AND bookingRequest.active = true")
    Optional<BookingRequest> findActiveByIdAndProfessionalIdForUpdate(
            @Param("bookingRequestId") Long bookingRequestId,
            @Param("professionalId") Long professionalId
    );

    Optional<BookingRequest> findByIdAndClient_IdAndActiveTrue(
            Long bookingRequestId,
            Long clientId
    );

    Optional<BookingRequest> findByIdAndProfessional_IdAndActiveTrue(
            Long bookingRequestId,
            Long professionalId
    );

    @EntityGraph(attributePaths = {"client", "professional", "items", "items.availabilitySlot"})
    List<BookingRequest> findAllByClient_IdAndActiveTrueOrderByCreatedAtDescIdDesc(Long clientId);

    @EntityGraph(attributePaths = {"client", "professional", "items", "items.availabilitySlot"})
    List<BookingRequest> findAllByProfessional_IdAndActiveTrueOrderByCreatedAtDescIdDesc(Long professionalId);

    List<BookingRequest> findAllByClient_IdAndStatusAndActiveTrueOrderByCreatedAtDesc(
            Long clientId,
            BookingRequestStatus status
    );

    List<BookingRequest> findAllByProfessional_IdAndStatusAndActiveTrueOrderByCreatedAtDesc(
            Long professionalId,
            BookingRequestStatus status
    );
}
