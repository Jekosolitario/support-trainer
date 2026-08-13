package it.zuperman.support_trainer.availability.dto.response;

public record WeeklyAvailabilityRuleImpactResponse(
        boolean impactDetected,
        long impactedBookingCount,
        boolean changeReasonRequired
) {
}
