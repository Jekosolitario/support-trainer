package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalOperationalStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.enums.Role;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class UserPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Test
    @DisplayName("Deve salvare e leggere correttamente un ProfessionalProfile")
    void shouldSaveAndReadProfessionalProfile() {
        String email = "pro-" + UUID.randomUUID() + "@test.com";

        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                email,
                "password123",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        professional.setPhoneNumber("3331234567");
        professional.setBio("Personal trainer specializzato in ricomposizione corporea");
        professional.setCity("Milano");
        professional.setOperationalStatus(ProfessionalOperationalStatus.DISPONIBILE);
        professional.setActive(true);

        ProfessionalProfile saved = professionalProfileRepository.save(professional);

        assertNotNull(saved.getId());

        Optional<ProfessionalProfile> foundByChildRepo =
                professionalProfileRepository.findByEmail(email);

        assertTrue(foundByChildRepo.isPresent());
        assertEquals(email, foundByChildRepo.get().getEmail());
        assertEquals(Role.PROFESSIONAL, foundByChildRepo.get().getRole());
        assertEquals(ProfessionalSpecialization.PERSONAL_TRAINER, foundByChildRepo.get().getSpecialization());

        Optional<User> foundByParentRepo = userRepository.findByEmail(email);

        assertTrue(foundByParentRepo.isPresent());
        assertInstanceOf(ProfessionalProfile.class, foundByParentRepo.get());
    }

    @Test
    @DisplayName("Deve salvare e leggere correttamente un ClientProfile")
    void shouldSaveAndReadClientProfile() {
        String email = "client-" + UUID.randomUUID() + "@test.com";

        ClientProfile client = new ClientProfile(
                "Luca",
                "Bianchi",
                email,
                "password123",
                LocalDate.of(1998, 5, 10),
                new BigDecimal("178.50"),
                "Perdere massa grassa",
                Gender.MALE
        );

        client.setOperationalStatus(ClientOperationalStatus.ATTIVO);
        client.setMedicalNotes("Nessuna patologia rilevante");
        client.setInjuryNotes("Pregresso fastidio alla spalla destra");
        client.setNotes("Preferisce allenarsi la mattina");
        client.setActive(true);

        ClientProfile saved = clientProfileRepository.save(client);

        assertNotNull(saved.getId());

        Optional<ClientProfile> foundByChildRepo =
                clientProfileRepository.findByEmail(email);

        assertTrue(foundByChildRepo.isPresent());
        assertEquals(email, foundByChildRepo.get().getEmail());
        assertEquals(Role.CLIENT, foundByChildRepo.get().getRole());
        assertEquals(new BigDecimal("178.50"), foundByChildRepo.get().getHeightCm());

        Optional<User> foundByParentRepo = userRepository.findByEmail(email);

        assertTrue(foundByParentRepo.isPresent());
        assertInstanceOf(ClientProfile.class, foundByParentRepo.get());
    }
}
