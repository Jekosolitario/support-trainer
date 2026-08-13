package it.zuperman.support_trainer.availability.dto.response;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;

public record WeeklyAvailabilityRuleResponse(
        Long id,
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<Integer> allowedDurations,
        String locationLabel,
        Integer capacityPerSlot,
        Boolean active,
        LocalDate validFrom,
        Instant createdAt,
        Instant updatedAt
) {

    public static WeeklyAvailabilityRuleResponse fromEntity(WeeklyAvailabilityRule rule) {
        return new WeeklyAvailabilityRuleResponse(
                rule.getId(),
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getAllowedDurations().stream().sorted().toList(),
                rule.getLocationLabel(),
                rule.getCapacityPerSlot(),
                rule.getActive(),
                rule.getValidFrom(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
