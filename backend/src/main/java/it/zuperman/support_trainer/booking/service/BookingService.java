package it.zuperman.support_trainer.booking.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingSummaryResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.mapper.BookingResponseMapper;
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
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

@Service
public class BookingService {

    private final BookingRequestRepository bookingRequestRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final ApplicationTimeProvider timeProvider;
    private final BookingResponseMapper bookingResponseMapper;
    private final UserReadinessValidator userReadinessValidator;

    public BookingService(
            BookingRequestRepository bookingRequestRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            ApplicationTimeProvider timeProvider,
            BookingResponseMapper bookingResponseMapper,
            UserReadinessValidator userReadinessValidator
    ) {
        this.bookingRequestRepository = bookingRequestRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.timeProvider = timeProvider;
        this.bookingResponseMapper = bookingResponseMapper;
        this.userReadinessValidator = userReadinessValidator;
    }

    @Transactional
    public BookingDetailResponse createBookingRequest(CreateBookingRequest request) {
        ClientProfile client = getAuthenticatedClient();

        AvailabilitySlot slot = availabilitySlotRepository.findActiveAccessibleByIdAndClientIdForUpdate(
                        request.getAvailabilitySlotId(),
                        client.getId(),
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "AVAILABILITY_SLOT_NOT_FOUND",
                "Slot disponibilità non trovato"
        ));

        ProfessionalProfile professional = slot.getProfessional();

        validateBookableProfessionalSpecialization(professional);
        validateBookableSlot(slot);
        validateNoPendingBookingOnSlot(slot.getId());

        BookingRequest bookingRequest = new BookingRequest(
                client,
                professional,
                normalizeNote(request.getNote()),
                displayName(client.getFirstName(), client.getLastName()),
                displayName(professional.getFirstName(), professional.getLastName())
        );

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);

        BookingRequestItem bookingRequestItem = new BookingRequestItem(
                savedBookingRequest,
                slot,
                slot.getStartDateTime(),
                slot.getEndDateTime()
        );

        BookingRequestItem savedItem = bookingRequestItemRepository.save(bookingRequestItem);
        savedBookingRequest.getItems().add(savedItem);

        return bookingResponseMapper.toDetail(savedBookingRequest);
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> getClientBookingRequests() {
        ClientProfile client = getAuthenticatedClient();

        return bookingRequestRepository
                .findAllByClient_IdAndActiveTrueOrderByCreatedAtDescIdDesc(client.getId())
                .stream()
                .map(bookingResponseMapper::toClientSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> getProfessionalBookingRequests() {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        return bookingRequestRepository
                .findAllByProfessional_IdAndActiveTrueOrderByCreatedAtDescIdDesc(professional.getId())
                .stream()
                .map(bookingResponseMapper::toProfessionalSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingRequestDetail(Long bookingRequestId) {
        User user = getAuthenticatedUser();

        BookingRequest bookingRequest = bookingRequestRepository.findActiveByIdAndParticipantId(
                        bookingRequestId,
                        user.getId()
                )
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "BOOKING_REQUEST_NOT_FOUND",
                "Richiesta di prenotazione non trovata"
        ));

        return bookingResponseMapper.toDetail(bookingRequest);
    }

    @Transactional
    public BookingDetailResponse confirmBookingRequest(Long bookingRequestId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        BookingRequest bookingRequest = getActiveBookingRequestForProfessional(
                bookingRequestId,
                professional.getId()
        );

        validateBookingRequestIsPending(bookingRequest);

        List<AvailabilitySlot> slotsToBook
                = lockAndValidateSlotsCanBeConfirmed(bookingRequest);

        bookingRequest.confirm(timeProvider.nowInstant());
        markSlotsAsBooked(slotsToBook);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return bookingResponseMapper.toDetail(savedBookingRequest);
    }

    @Transactional
    public BookingDetailResponse rejectBookingRequest(Long bookingRequestId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();

        BookingRequest bookingRequest = getActiveBookingRequestForProfessional(
                bookingRequestId,
                professional.getId()
        );

        validateBookingRequestIsPending(bookingRequest);

        bookingRequest.reject(timeProvider.nowInstant());
        releaseSlotsAfterNegativeOutcome(bookingRequest);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return bookingResponseMapper.toDetail(savedBookingRequest);
    }

    @Transactional
    public BookingDetailResponse cancelBookingRequest(Long bookingRequestId) {
        User user = getAuthenticatedUser();

        BookingRequest bookingRequest = bookingRequestRepository.findActiveByIdAndParticipantIdForUpdate(
                        bookingRequestId,
                        user.getId()
                )
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "BOOKING_REQUEST_NOT_FOUND",
                "Richiesta di prenotazione non trovata"
        ));

        validateCancellationAllowed(user, bookingRequest);

        bookingRequest.cancel(timeProvider.nowInstant());
        releaseSlotsAfterNegativeOutcome(bookingRequest);

        BookingRequest savedBookingRequest = bookingRequestRepository.save(bookingRequest);
        return bookingResponseMapper.toDetail(savedBookingRequest);
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

    private String displayName(String firstName, String lastName) {
        String normalizedFirstName = firstName == null ? "" : firstName.trim();
        String normalizedLastName = lastName == null ? "" : lastName.trim();

        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOOKING_PARTICIPANT_NAME_INVALID",
                    "I dati della prenotazione non sono disponibili"
            );
        }

        return normalizedFirstName + " " + normalizedLastName;
    }

    private BookingRequest getActiveBookingRequestForProfessional(
            Long bookingRequestId,
            Long professionalId
    ) {
        return bookingRequestRepository.findActiveByIdAndProfessionalIdForUpdate(
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

    private List<AvailabilitySlot> lockAndValidateSlotsCanBeConfirmed(BookingRequest bookingRequest) {
        List<AvailabilitySlot> slotsToBook = new ArrayList<>();

        for (BookingRequestItem item : bookingRequest.getItems()) {
            AvailabilitySlot slot = availabilitySlotRepository
                    .findActiveByIdForUpdate(item.getAvailabilitySlot().getId())
                    .orElseThrow(() -> new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                    "Lo slot collegato non è più confermabile"
            ));

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

            if (!slot.getStartDateTime().isAfter(timeProvider.nowInstant())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                        "Lo slot collegato è scaduto e non è più confermabile"
                );
            }

            slotsToBook.add(slot);
        }

        return slotsToBook;
    }

    private void markSlotsAsBooked(List<AvailabilitySlot> slotsToBook) {
        for (AvailabilitySlot slot : slotsToBook) {
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

        if (!slot.getStartDateTime().isAfter(timeProvider.nowInstant())) {
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

    private void validateBookableProfessionalSpecialization(ProfessionalProfile professional) {
        if (professional.getSpecialization() != ProfessionalSpecialization.PERSONAL_TRAINER) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "Lo slot selezionato non è prenotabile per questo professionista"
            );
        }
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

        userReadinessValidator.validateOperationalUser(user);
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

}
