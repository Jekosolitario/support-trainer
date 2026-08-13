package it.zuperman.support_trainer.booking.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityCapacityService;
import it.zuperman.support_trainer.availability.service.AvailabilityWindowPolicy;
import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingSummaryResponse;
import it.zuperman.support_trainer.booking.entity.BookingRequest;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.mapper.BookingResponseMapper;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;

@Service
public class BookingService {

    private static final Set<BookingRequestStatus> OCCUPYING_STATUSES = Set.of(
            BookingRequestStatus.PENDING,
            BookingRequestStatus.CONFIRMED
    );

    private final BookingRequestRepository bookingRequestRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AuthenticatedUserLoader authenticatedUserLoader;
    private final ApplicationTimeProvider timeProvider;
    private final BookingResponseMapper bookingResponseMapper;
    private final UserReadinessValidator userReadinessValidator;
    private final BusinessDateTimeMapper businessDateTimeMapper;
    private final AvailabilityCapacityService capacityService;

    public BookingService(
            BookingRequestRepository bookingRequestRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ClientProfileRepository clientProfileRepository,
            AuthenticatedUserLoader authenticatedUserLoader,
            ApplicationTimeProvider timeProvider,
            BookingResponseMapper bookingResponseMapper,
            UserReadinessValidator userReadinessValidator,
            BusinessDateTimeMapper businessDateTimeMapper,
            AvailabilityCapacityService capacityService
    ) {
        this.bookingRequestRepository = bookingRequestRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.authenticatedUserLoader = authenticatedUserLoader;
        this.timeProvider = timeProvider;
        this.bookingResponseMapper = bookingResponseMapper;
        this.userReadinessValidator = userReadinessValidator;
        this.businessDateTimeMapper = businessDateTimeMapper;
        this.capacityService = capacityService;
    }

    @Transactional
    public BookingDetailResponse createBookingRequest(CreateBookingRequest request) {
        ClientProfile authenticatedClient = getAuthenticatedClient();
        ClientProfile client = clientProfileRepository.findByIdForUpdate(authenticatedClient.getId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "CLIENT_NOT_FOUND",
                        "Cliente non trovato"
                ));

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
        validateBookableWindow(slot);
        BookingInterval interval = resolveBookingInterval(request, slot);
        validateClientHasNoOverlappingBooking(client, professional, interval);
        validateCapacity(slot, interval);

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
                interval.start(),
                interval.end(),
                slot.getLocationLabel()
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

        lockAndValidateSlotsCanBeConfirmed(bookingRequest);

        bookingRequest.confirm(timeProvider.nowInstant());

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

    private void lockAndValidateSlotsCanBeConfirmed(BookingRequest bookingRequest) {
        for (BookingRequestItem item : bookingRequest.getItems()) {
            AvailabilitySlot slot = availabilitySlotRepository
                    .findByIdForUpdate(item.getAvailabilitySlot().getId())
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

            if (!slot.getStartDateTime().isAfter(timeProvider.nowInstant())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_SLOT_NOT_CONFIRMABLE",
                        "Lo slot collegato è scaduto e non è più confermabile"
                );
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

    private void validateBookableWindow(AvailabilitySlot slot) {
        if (!Boolean.TRUE.equals(slot.getActive()) || Boolean.TRUE.equals(slot.getBlocked())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "La disponibilità selezionata non è prenotabile"
            );
        }

        if (!slot.getStartDateTime().isAfter(timeProvider.nowInstant())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "La disponibilità selezionata è scaduta e non è prenotabile"
            );
        }
    }

    private BookingInterval resolveBookingInterval(
            CreateBookingRequest request,
            AvailabilitySlot slot
    ) {
        if (request.getStartDateTime() == null || request.getDurationMinutes() == null) {
            throw invalidBookingInterval("Orario di inizio e durata sono obbligatori");
        }

        businessDateTimeMapper.validateRequestDateTime(request.getStartDateTime());
        LocalTime localStart = request.getStartDateTime().toLocalTime();
        if (!AvailabilityWindowPolicy.isAligned(localStart)) {
            throw invalidBookingInterval("L'orario di inizio deve essere allineato a 15 minuti");
        }
        int duration = request.getDurationMinutes();
        if (!AvailabilityWindowPolicy.isAllowedDuration(duration)) {
            throw invalidBookingInterval("La durata deve essere tra 15 e 180 minuti e multipla di 15");
        }
        List<Integer> allowedDurations = AvailabilityWindowPolicy.allowedDurations(slot);
        if (!allowedDurations.contains(duration)) {
            throw invalidBookingInterval("La durata selezionata non è disponibile per questa fascia");
        }

        AvailabilityWindowPolicy.ConcreteWindow resolved = AvailabilityWindowPolicy.resolveBookingInterval(
                slot,
                request.getStartDateTime(),
                duration,
                timeProvider.businessZone()
        ).orElseThrow(() -> invalidBookingInterval(
                "La combinazione temporale non è valida o non rientra nella fascia"
        ));
        if (!resolved.start().isAfter(timeProvider.nowInstant())) {
            throw invalidBookingInterval("La prenotazione deve iniziare nel futuro");
        }
        return new BookingInterval(resolved.start(), resolved.end());
    }

    private void validateClientHasNoOverlappingBooking(
            ClientProfile client,
            ProfessionalProfile professional,
            BookingInterval interval
    ) {
        if (bookingRequestItemRepository.existsOccupyingBookingForClientOverlappingProfessional(
                client.getId(),
                professional.getId(),
                interval.start(),
                interval.end(),
                OCCUPYING_STATUSES
        )) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "CLIENT_BOOKING_TIME_OVERLAP",
                    "Hai già una prenotazione sovrapposta con questo professionista"
            );
        }
    }

    private void validateCapacity(AvailabilitySlot slot, BookingInterval interval) {
        if (!capacityService.hasCapacity(slot, interval.start(), interval.end())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_CAPACITY_EXHAUSTED",
                    "La fascia non ha capacità disponibile per tutto l'intervallo richiesto"
            );
        }
    }

    private AppException invalidBookingInterval(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
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
        User user = authenticatedUserLoader.requireAuthenticatedUser();
        userReadinessValidator.validateOperationalUser(user);
        return user;
    }

    private record BookingInterval(Instant start, Instant end) {
    }

}
