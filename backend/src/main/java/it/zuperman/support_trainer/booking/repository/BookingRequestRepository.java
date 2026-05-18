package it.zuperman.support_trainer.booking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    Optional<BookingRequest> findByIdAndActiveTrue(Long bookingRequestId);

    Optional<BookingRequest> findByIdAndClient_IdAndActiveTrue(
            Long bookingRequestId,
            Long clientId
    );

    Optional<BookingRequest> findByIdAndProfessional_IdAndActiveTrue(
            Long bookingRequestId,
            Long professionalId
    );

    List<BookingRequest> findAllByClient_IdAndActiveTrueOrderByCreatedAtDesc(Long clientId);

    List<BookingRequest> findAllByProfessional_IdAndActiveTrueOrderByCreatedAtDesc(Long professionalId);

    List<BookingRequest> findAllByClient_IdAndStatusAndActiveTrueOrderByCreatedAtDesc(
            Long clientId,
            BookingRequestStatus status
    );

    List<BookingRequest> findAllByProfessional_IdAndStatusAndActiveTrueOrderByCreatedAtDesc(
            Long professionalId,
            BookingRequestStatus status
    );
}