package it.zuperman.support_trainer.booking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingRequestResponse;
import it.zuperman.support_trainer.booking.service.BookingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingRequestResponse> createBookingRequest(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingRequestResponse response = bookingService.createBookingRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/client")
    public ResponseEntity<List<BookingRequestResponse>> getClientBookingRequests() {
        List<BookingRequestResponse> response = bookingService.getClientBookingRequests();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/professional")
    public ResponseEntity<List<BookingRequestResponse>> getProfessionalBookingRequests() {
        List<BookingRequestResponse> response = bookingService.getProfessionalBookingRequests();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingRequestId}")
    public ResponseEntity<BookingRequestResponse> getBookingRequestDetail(
            @PathVariable Long bookingRequestId
    ) {
        BookingRequestResponse response = bookingService.getBookingRequestDetail(bookingRequestId);
        return ResponseEntity.ok(response);
    }
}
