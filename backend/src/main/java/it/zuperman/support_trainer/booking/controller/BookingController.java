package it.zuperman.support_trainer.booking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.booking.dto.request.CreateBookingRequest;
import it.zuperman.support_trainer.booking.dto.response.BookingDetailResponse;
import it.zuperman.support_trainer.booking.dto.response.BookingSummaryResponse;
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
    public ResponseEntity<BookingDetailResponse> createBookingRequest(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingDetailResponse response = bookingService.createBookingRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/client")
    public ResponseEntity<List<BookingSummaryResponse>> getClientBookingRequests() {
        List<BookingSummaryResponse> response = bookingService.getClientBookingRequests();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/professional")
    public ResponseEntity<List<BookingSummaryResponse>> getProfessionalBookingRequests() {
        List<BookingSummaryResponse> response = bookingService.getProfessionalBookingRequests();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingRequestId}")
    public ResponseEntity<BookingDetailResponse> getBookingRequestDetail(
            @PathVariable Long bookingRequestId
    ) {
        BookingDetailResponse response = bookingService.getBookingRequestDetail(bookingRequestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingRequestId}/confirm")
    public ResponseEntity<BookingDetailResponse> confirmBookingRequest(
            @PathVariable Long bookingRequestId
    ) {
        BookingDetailResponse response = bookingService.confirmBookingRequest(bookingRequestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingRequestId}/reject")
    public ResponseEntity<BookingDetailResponse> rejectBookingRequest(
            @PathVariable Long bookingRequestId
    ) {
        BookingDetailResponse response = bookingService.rejectBookingRequest(bookingRequestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingRequestId}/cancel")
    public ResponseEntity<BookingDetailResponse> cancelBookingRequest(
            @PathVariable Long bookingRequestId
    ) {
        BookingDetailResponse response = bookingService.cancelBookingRequest(bookingRequestId);
        return ResponseEntity.ok(response);
    }
}
