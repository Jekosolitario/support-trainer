package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalOperationalStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.enums.Role;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "it.zuperman.support_trainer.security.session.CapturingStatementInspector"
})
@ActiveProfiles("test")
@Transactional
class UserSecuritySnapshotRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @BeforeEach
    void clearCapturedSql() {
        CapturingStatementInspector.clear();
    }

    @Test
    @DisplayName("Deve caricare lo snapshot readiness per ID senza sottoprofili")
    void shouldLoadSecuritySnapshotByIdWithoutSubclassJoins() {
        ClientProfile client = createActiveClient();
        ProfessionalProfile professional = createActiveProfessional();

        CapturingStatementInspector.clear();
        Optional<UserSecuritySnapshot> snapshot = userRepository.findSecuritySnapshotById(client.getId());

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getId()).isEqualTo(client.getId());
        assertThat(snapshot.get().getRole()).isEqualTo(Role.CLIENT);
        assertThat(snapshot.get().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(snapshot.get().getEmailVerified()).isTrue();

        Optional<UserSecuritySnapshot> professionalSnapshot
                = userRepository.findSecuritySnapshotById(professional.getId());
        assertThat(professionalSnapshot).isPresent();
        assertThat(professionalSnapshot.get().getRole()).isEqualTo(Role.PROFESSIONAL);

        String joinedSql = String.join("\n", CapturingStatementInspector.statements()).toLowerCase(Locale.ROOT);
        assertThat(joinedSql).contains("from users");
        assertThat(joinedSql).doesNotContain("client_profiles");
        assertThat(joinedSql).doesNotContain("professional_profiles");
        assertThat(joinedSql).doesNotContain("specialization");
        assertThat(joinedSql).doesNotContain("primary_goal");
        assertThat(joinedSql).doesNotContain("height_cm");
        assertThat(joinedSql).doesNotContain("password");
    }

    @Test
    @DisplayName("Deve restituire empty per utente inesistente")
    void shouldReturnEmptyForMissingUser() {
        assertThat(userRepository.findSecuritySnapshotById(9_999_999L)).isEmpty();
    }

    private ClientProfile createActiveClient() {
        ClientProfile client = new ClientProfile(
                "Ada",
                "Client",
                "snapshot.client." + UUID.randomUUID() + "@example.com",
                "encoded-password",
                LocalDate.of(1990, 1, 1),
                new BigDecimal("170.00"),
                "Goal",
                Gender.FEMALE
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setOperationalStatus(ClientOperationalStatus.ATTIVO);
        client.setActive(true);
        return clientProfileRepository.save(client);
    }

    private ProfessionalProfile createActiveProfessional() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Bob",
                "Pro",
                "snapshot.pro." + UUID.randomUUID() + "@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setOperationalStatus(ProfessionalOperationalStatus.DISPONIBILE);
        professional.setActive(true);
        return professionalProfileRepository.save(professional);
    }
}
