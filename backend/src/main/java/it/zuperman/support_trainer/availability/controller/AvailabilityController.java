package it.zuperman.support_trainer.availability.controller;

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

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/availability")
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<AvailabilitySlotResponse> createAvailabilitySlot(
            @Valid @RequestBody CreateAvailabilitySlotRequest request
    ) {
        AvailabilitySlotResponse response = availabilityService.createAvailabilitySlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<AvailabilitySlotResponse>> getMyAvailabilitySlots() {
        List<AvailabilitySlotResponse> response = availabilityService.getMyAvailabilitySlots();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{slotId}")
    public ResponseEntity<AvailabilitySlotResponse> updateAvailabilitySlot(
            @PathVariable Long slotId,
            @Valid @RequestBody UpdateAvailabilitySlotRequest request
    ) {
        AvailabilitySlotResponse response = availabilityService.updateAvailabilitySlot(slotId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{slotId}/block")
    public ResponseEntity<AvailabilitySlotResponse> blockAvailabilitySlot(@PathVariable Long slotId) {
        AvailabilitySlotResponse response = availabilityService.blockAvailabilitySlot(slotId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{slotId}/unblock")
    public ResponseEntity<AvailabilitySlotResponse> unblockAvailabilitySlot(@PathVariable Long slotId) {
        AvailabilitySlotResponse response = availabilityService.unblockAvailabilitySlot(slotId);
        return ResponseEntity.ok(response);
    }
}
