package it.zuperman.support_trainer.availability.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ClientAvailabilitySlotResponse(
        Long occurrenceId,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        List<Integer> allowedDurations,
        int startIntervalMinutes,
        String location,
        int capacity,
        List<ClientBookableOptionResponse> bookableOptions
) {
}
