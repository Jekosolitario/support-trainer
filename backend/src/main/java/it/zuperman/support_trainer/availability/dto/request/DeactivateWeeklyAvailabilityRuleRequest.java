package it.zuperman.support_trainer.availability.dto.request;

import jakarta.validation.constraints.Size;

public record DeactivateWeeklyAvailabilityRuleRequest(
        @Size(max = 1000, message = "La motivazione non può superare 1000 caratteri")
        String changeReason
) {
}
