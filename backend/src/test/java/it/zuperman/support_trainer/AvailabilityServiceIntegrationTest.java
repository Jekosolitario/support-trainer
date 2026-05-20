package it.zuperman.support_trainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.service.AvailabilityService;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityServiceIntegrationTest {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve creare uno slot availability valido per un professionista")
    void shouldCreateAvailabilitySlotForProfessional() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(7).withNano(0);
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                startDateTime,
                endDateTime
        );

        AvailabilitySlotResponse response = availabilityService.createAvailabilitySlot(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStartDateTime()).isEqualTo(startDateTime);
        assertThat(response.getEndDateTime()).isEqualTo(endDateTime);
        assertThat(response.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE.name());

        List<AvailabilitySlot> savedSlots = availabilitySlotRepository.findAll();

        assertThat(savedSlots).hasSize(1);
        assertThat(savedSlots.get(0).getProfessional().getId()).isEqualTo(professional.getId());
    }

    private ProfessionalProfile createActivePersonalTrainer() {
        String email = "pro-" + UUID.randomUUID() + "@test.com";

        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                email,
                "password123",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);

        return professionalProfileRepository.save(professional);
    }

    private void authenticateAs(String email, String authority) {
        UsernamePasswordAuthenticationToken authentication
                = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
