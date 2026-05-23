package it.zuperman.support_trainer.booking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingRequestResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

@Service
public class BookingService {

    private final BookingRequestRepository bookingRequestRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final UserRepository userRepository;

    public BookingService(
            BookingRequestRepository bookingRequestRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            UserRepository userRepository
    ) {
        this.bookingRequestRepository = bookingRequestRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BookingRequestResponse createBookingRequest(CreateBookingRequest request) {
        ClientProfile client = getAuthenticatedClient();

        AvailabilitySlot slot = availabilitySlotRepository.findByIdAndActiveTrue(request.getAvailabilitySlotId())
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "AVAILABILITY_SLOT_NOT_FOUND",
                "Slot disponibilità non trovato"
        ));

        ProfessionalProfile professional = slot.getProfessional();

        validateReadableProfessional(professional);
        validateBookableProfessionalSpecialization(professional);
        validateProfessionalAccess(client.getId(), professional.getId());
        validateBookableSlot(slot);
        validateNoPendingBookingOnSlot(slot.getId());

        BookingRequest bookingRequest = new BookingRequest(
                client,
                professional,
                normalizeNote(request.getNote())
        );

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);

        BookingRequestItem bookingRequestItem = new BookingRequestItem(
                savedBookingRequest,
                slot
        );

        BookingRequestItem savedItem = bookingRequestItemRepository.save(bookingRequestItem);
        savedBookingRequest.getItems().add(savedItem);

        return BookingRequestResponse.fromEntity(savedBookingRequest);
    }

    @Transactional(readOnly = true)
    public List<BookingRequestResponse> getClientBookingRequests() {
        ClientProfile client = getAuthenticatedClient();

        return bookingRequestRepository
                .findAllByClient_IdAndActiveTrueOrderByCreatedAtDesc(client.getId())
                .stream()
                .map(BookingRequestResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingRequestResponse> getProfessionalBookingRequests() {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        return bookingRequestRepository
                .findAllByProfessional_IdAndActiveTrueOrderByCreatedAtDesc(professional.getId())
                .stream()
                .map(BookingRequestResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingRequestResponse getBookingRequestDetail(Long bookingRequestId) {
        User user = getAuthenticatedUser();

        BookingRequest bookingRequest = bookingRequestRepository.findByIdAndActiveTrue(bookingRequestId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "BOOKING_REQUEST_NOT_FOUND",
                "Richiesta di prenotazione non trovata"
        ));

        validateBookingRequestVisibility(user, bookingRequest);

        return BookingRequestResponse.fromEntity(bookingRequest);
    }

    @Transactional
    public BookingRequestResponse confirmBookingRequest(Long bookingRequestId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        BookingRequest bookingRequest = getActiveBookingRequestForProfessional(
                bookingRequestId,
                professional.getId()
        );

        validateBookingRequestIsPending(bookingRequest);
        validateSlotsCanBeConfirmed(bookingRequest);

        bookingRequest.setStatus(BookingRequestStatus.CONFIRMED);
        markSlotsAsBooked(bookingRequest);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return BookingRequestResponse.fromEntity(savedBookingRequest);
    }

    @Transactional
    public BookingRequestResponse rejectBookingRequest(Long bookingRequestId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        BookingRequest bookingRequest = getActiveBookingRequestForProfessional(
                bookingRequestId,
                professional.getId()
        );

        validateBookingRequestIsPending(bookingRequest);

        bookingRequest.setStatus(BookingRequestStatus.REJECTED);
        releaseSlotsAfterNegativeOutcome(bookingRequest);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return BookingRequestResponse.fromEntity(savedBookingRequest);
    }

    @Transactional
    public BookingRequestResponse cancelBookingRequest(Long bookingRequestId) {
        User user = getAuthenticatedUser();

        BookingRequest bookingRequest = bookingRequestRepository.findByIdAndActiveTrue(bookingRequestId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "BOOKING_REQUEST_NOT_FOUND",
                "Richiesta di prenotazione non trovata"
        ));

        validateBookingRequestVisibility(user, bookingRequest);
        validateCancellationAllowed(user, bookingRequest);

        bookingRequest.setStatus(BookingRequestStatus.CANCELLED);
        releaseSlotsAfterNegativeOutcome(bookingRequest);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return BookingRequestResponse.fromEntity(savedBookingRequest);
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }

        String normalizedNote = note.trim();

        if (normalizedNote.isBlank()) {
            return null;
        }

        return normalizedNote;
    }

    private BookingRequest getActiveBookingRequestForProfessional(
            Long bookingRequestId,
            Long professionalId
    ) {
        return bookingRequestRepository.findByIdAndProfessional_IdAndActiveTrue(
                bookingRequestId,
                professionalId
        ).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "BOOKING_REQUEST_NOT_FOUND",
                "Richiesta di prenotazione non trovata"
        ));
    }

    private void validateBookingRequestIsPending(BookingRequest bookingRequest) {
        if (bookingRequest.getStatus() != BookingRequestStatus.PENDING) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_REQUEST_INVALID_TRANSITION",
                    "La richiesta di prenotazione non è in stato PENDING"
            );
        }
    }

    private void validateSlotsCanBeConfirmed(BookingRequest bookingRequest) {
        for (BookingRequestItem item : bookingRequest.getItems()) {
            AvailabilitySlot slot = item.getAvailabilitySlot();

            if (slot.getProfessional().getSpecialization() != ProfessionalSpecialization.PERSONAL_TRAINER) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                        "Lo slot collegato non è confermabile per questo professionista"
                );
            }

            if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                        "Lo slot collegato non è più confermabile"
                );
            }

            if (!slot.getStartDateTime().isAfter(LocalDateTime.now())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                        "Lo slot collegato è scaduto e non è più confermabile"
                );
            }
        }
    }

    private void markSlotsAsBooked(BookingRequest bookingRequest) {
        for (BookingRequestItem item : bookingRequest.getItems()) {
            AvailabilitySlot slot = item.getAvailabilitySlot();
            slot.setStatus(AvailabilitySlotStatus.BOOKED);
            availabilitySlotRepository.save(slot);
        }
    }

    private void releaseSlotsAfterNegativeOutcome(BookingRequest bookingRequest) {
        for (BookingRequestItem item : bookingRequest.getItems()) {
            AvailabilitySlot slot = item.getAvailabilitySlot();

            if (slot.getStatus() == AvailabilitySlotStatus.BOOKED) {
                slot.setStatus(AvailabilitySlotStatus.AVAILABLE);
                availabilitySlotRepository.save(slot);
            }
        }
    }

    private void validateCancellationAllowed(User user, BookingRequest bookingRequest) {
        BookingRequestStatus status = bookingRequest.getStatus();

        if (status == BookingRequestStatus.CANCELLED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_REQUEST_ALREADY_CANCELLED",
                    "La richiesta di prenotazione è già stata cancellata"
            );
        }

        if (status == BookingRequestStatus.REJECTED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_REQUEST_ALREADY_REJECTED",
                    "La richiesta di prenotazione è già stata rifiutata"
            );
        }

        if (user instanceof ClientProfile) {
            if (status == BookingRequestStatus.PENDING || status == BookingRequestStatus.CONFIRMED) {
                return;
            }
        }

        if (user instanceof ProfessionalProfile) {
            if (status == BookingRequestStatus.CONFIRMED) {
                return;
            }

            if (status == BookingRequestStatus.PENDING) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "BOOKING_REQUEST_REJECT_REQUIRED",
                        "Una richiesta in attesa deve essere rifiutata dal professionista"
                );
            }
        }

        throw new AppException(
                HttpStatus.CONFLICT,
                "BOOKING_REQUEST_INVALID_TRANSITION",
                "La richiesta di prenotazione non può essere cancellata nello stato attuale"
        );
    }

    private void validateBookableSlot(AvailabilitySlot slot) {
        if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "Lo slot selezionato non è prenotabile"
            );
        }

        if (!slot.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "Lo slot selezionato è scaduto e non è prenotabile"
            );
        }
    }

    private void validateNoPendingBookingOnSlot(Long availabilitySlotId) {
        boolean alreadyRequested = bookingRequestItemRepository
                .existsByAvailabilitySlot_IdAndBookingRequest_StatusAndBookingRequest_ActiveTrue(
                        availabilitySlotId,
                        BookingRequestStatus.PENDING
                );

        if (alreadyRequested) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_REQUEST_ALREADY_PENDING",
                    "Esiste già una richiesta di prenotazione in attesa per questo slot"
            );
        }
    }

    private void validateReadableProfessional(ProfessionalProfile professional) {
        if (!Boolean.TRUE.equals(professional.getActive())
                || professional.getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(professional.getEmailVerified())) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "PROFESSIONAL_NOT_FOUND",
                    "Professionista non trovato"
            );
        }
    }

    private void validateBookableProfessionalSpecialization(ProfessionalProfile professional) {
        if (professional.getSpecialization() != ProfessionalSpecialization.PERSONAL_TRAINER) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "Lo slot selezionato non è prenotabile per questo professionista"
            );
        }
    }

    private void validateProfessionalAccess(Long clientId, Long professionalId) {
        boolean linked = professionalClientLinkRepository.existsByProfessional_IdAndClient_IdAndActiveTrue(
                professionalId,
                clientId
        );

        if (!linked) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_ACCESS_DENIED",
                    "Non puoi prenotare slot di questo professionista"
            );
        }
    }

    private void validateBookingRequestVisibility(User user, BookingRequest bookingRequest) {
        if (user instanceof ClientProfile clientProfile) {
            if (!bookingRequest.getClient().getId().equals(clientProfile.getId())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "BOOKING_REQUEST_ACCESS_DENIED",
                        "Non puoi accedere a questa richiesta di prenotazione"
                );
            }

            return;
        }

        if (user instanceof ProfessionalProfile professionalProfile) {
            if (!bookingRequest.getProfessional().getId().equals(professionalProfile.getId())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "BOOKING_REQUEST_ACCESS_DENIED",
                        "Non puoi accedere a questa richiesta di prenotazione"
                );
            }

            return;
        }

        throw new AppException(
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED",
                "Solo cliente o professionista possono accedere a questa risorsa"
        );
    }

    private ClientProfile getAuthenticatedClient() {
        User user = getAuthenticatedUser();

        if (!(user instanceof ClientProfile clientProfile)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_NOT_ALLOWED",
                    "Solo il cliente può accedere a questa risorsa"
            );
        }

        return clientProfile;
    }

    private ProfessionalProfile getAuthenticatedProfessional() {
        User user = getAuthenticatedUser();

        if (!(user instanceof ProfessionalProfile professionalProfile)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_NOT_ALLOWED",
                    "Solo il professionista può accedere a questa risorsa"
            );
        }

        return professionalProfile;
    }

    private User getAuthenticatedUser() {
        String email = getAuthenticatedEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATED_USER_NOT_FOUND",
                "Utente autenticato non trovato"
        ));

        validateAuthenticatedUserAccess(user);
        return user;
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Utente non autenticato"
            );
        }

        return authentication.getName().trim().toLowerCase();
    }

    private void validateAuthenticatedUserAccess(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Account non attivo"
            );
        }

        if (user instanceof ClientProfile clientProfile) {
            if (!Boolean.TRUE.equals(clientProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "CLIENT_NOT_ACTIVE",
                        "Profilo cliente non attivo"
                );
            }
        }

        if (user instanceof ProfessionalProfile professionalProfile) {
            if (!Boolean.TRUE.equals(professionalProfile.getEmailVerified())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "EMAIL_NOT_VERIFIED",
                        "Email non verificata"
                );
            }

            if (!Boolean.TRUE.equals(professionalProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "PROFESSIONAL_NOT_ACTIVE",
                        "Profilo professionista non attivo"
                );
            }
        }
    }
}
