package it.zuperman.support_trainer.availability.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.CreateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.ChangeAvailabilitySlotBlockRequest;
import it.zuperman.support_trainer.availability.dto.request.DeactivateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.dto.response.WeeklyAvailabilityRuleImpactResponse;
import it.zuperman.support_trainer.availability.dto.response.WeeklyAvailabilityRuleResponse;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.availability.service.WeeklyAvailabilityRuleService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/availability")
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final WeeklyAvailabilityRuleService weeklyRuleService;

    public AvailabilityController(
            AvailabilityService availabilityService,
            WeeklyAvailabilityRuleService weeklyRuleService
    ) {
        this.availabilityService = availabilityService;
        this.weeklyRuleService = weeklyRuleService;
    }

    @PostMapping("/weekly-rules")
    public ResponseEntity<WeeklyAvailabilityRuleResponse> createWeeklyRule(
            @Valid @RequestBody CreateWeeklyAvailabilityRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weeklyRuleService.create(request));
    }

    @GetMapping("/weekly-rules/my")
    public ResponseEntity<List<WeeklyAvailabilityRuleResponse>> getMyWeeklyRules() {
        return ResponseEntity.ok(weeklyRuleService.listMine());
    }

    @GetMapping("/weekly-rules/{ruleId}/impact")
    public ResponseEntity<WeeklyAvailabilityRuleImpactResponse> previewWeeklyRuleImpact(
            @PathVariable Long ruleId
    ) {
        return ResponseEntity.ok(weeklyRuleService.previewImpact(ruleId));
    }

    @PutMapping("/weekly-rules/{ruleId}")
    public ResponseEntity<WeeklyAvailabilityRuleResponse> updateWeeklyRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateWeeklyAvailabilityRuleRequest request
    ) {
        return ResponseEntity.ok(weeklyRuleService.update(ruleId, request));
    }

    @PatchMapping("/weekly-rules/{ruleId}/deactivate")
    public ResponseEntity<Void> deactivateWeeklyRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody DeactivateWeeklyAvailabilityRuleRequest request
    ) {
        weeklyRuleService.deactivate(ruleId, request);
        return ResponseEntity.noContent().build();
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
    public ResponseEntity<AvailabilitySlotResponse> blockAvailabilitySlot(
            @PathVariable Long slotId,
            @Valid @RequestBody(required = false) ChangeAvailabilitySlotBlockRequest request
    ) {
        AvailabilitySlotResponse response = availabilityService.blockAvailabilitySlot(slotId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{slotId}/unblock")
    public ResponseEntity<AvailabilitySlotResponse> unblockAvailabilitySlot(@PathVariable Long slotId) {
        AvailabilitySlotResponse response = availabilityService.unblockAvailabilitySlot(slotId);
        return ResponseEntity.ok(response);
    }
}
