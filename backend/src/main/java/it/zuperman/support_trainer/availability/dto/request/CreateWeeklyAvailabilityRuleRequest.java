package it.zuperman.support_trainer.availability.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWeeklyAvailabilityRuleRequest(
        @NotNull(message = "Il giorno della settimana è obbligatorio")
        DayOfWeek dayOfWeek,
        @NotNull(message = "L'orario di inizio è obbligatorio")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,
        @NotNull(message = "L'orario di fine è obbligatorio")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,
        @NotEmpty(message = "Seleziona almeno una durata")
        @Size(max = 12, message = "Sono ammesse al massimo 12 durate")
        List<@NotNull @Min(15) @Max(180) Integer> allowedDurations,
        @Size(max = 255, message = "Il luogo non può superare 255 caratteri")
        String locationLabel,
        @NotNull(message = "La capacità è obbligatoria")
        @Min(value = 1, message = "La capacità deve essere almeno 1")
        Integer capacityPerSlot,
        @NotNull(message = "La data di validità è obbligatoria")
        LocalDate validFrom
) {
}
