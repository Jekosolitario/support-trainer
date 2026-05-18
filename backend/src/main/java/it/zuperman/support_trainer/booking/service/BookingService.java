package it.zuperman.support_trainer.booking.service;

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
        validateProfessionalAccess(client.getId(), professional.getId());
        validateBookableSlot(slot);
        validateNoPendingBookingOnSlot(slot.getId());

        BookingRequest bookingRequest = new BookingRequest(
                client,
                professional,
                request.getNote()
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

    private void validateBookableSlot(AvailabilitySlot slot) {
        if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BOOKABLE",
                    "Lo slot selezionato non è prenotabile"
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