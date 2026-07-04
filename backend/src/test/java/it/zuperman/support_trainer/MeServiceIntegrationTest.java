package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalOperationalStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.profile.dto.request.UpdateMyProfileRequest;
import it.zuperman.support_trainer.profile.dto.request.UpdateOperationalStatusRequest;
import it.zuperman.support_trainer.profile.dto.response.MyAccountResponse;
import it.zuperman.support_trainer.profile.dto.response.MyProfileResponse;
import it.zuperman.support_trainer.profile.service.MeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeServiceIntegrationTest {

    @Autowired
    private MeService meService;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private Validator validator;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

       @Test
    @DisplayName("Cliente autenticato deve leggere il proprio profilo e account")
    void shouldReturnAuthenticatedClientProfileAndAccount() {
        ClientProfile client = createActiveClient();
        authenticateAs(client.getEmail(), "CLIENT");

        MyProfileResponse profile = meService.getMyProfile();
        MyAccountResponse account = meService.getMyAccount();

        assertThat(profile.getId()).isEqualTo(client.getId());
        assertThat(profile.getRole().name()).isEqualTo("CLIENT");
        assertThat(profile.getFirstName()).isEqualTo(client.getFirstName());
        assertThat(profile.getLastName()).isEqualTo(client.getLastName());
        assertThat(profile.getBirthDate()).isEqualTo(client.getBirthDate());
        assertThat(profile.getHeightCm()).isEqualByComparingTo(client.getHeightCm());
        assertThat(profile.getPrimaryGoal()).isEqualTo(client.getPrimaryGoal());
        assertThat(profile.getGender()).isEqualTo(client.getGender());
        assertThat(profile.getSpecialization()).isNull();
        assertThat(profile.getPhoneNumber()).isNull();
        assertThat(profile.getBio()).isNull();

        assertThat(account.getId()).isEqualTo(client.getId());
        assertThat(account.getEmail()).isEqualTo(client.getEmail());
        assertThat(account.getRole()).isEqualTo("CLIENT");
        assertThat(account.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(account.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Professionista autenticato deve leggere il proprio profilo e account")
    void shouldReturnAuthenticatedProfessionalProfileAndAccount() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        professional.setPhoneNumber("+39 333 1234567");
        professional.setBio("Personal trainer certificato.");
        professional.setWorkplaceName("Support Gym");
        professional.setCity("Roma");
        professionalProfileRepository.save(professional);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        MyProfileResponse profile = meService.getMyProfile();
        MyAccountResponse account = meService.getMyAccount();

        assertThat(profile.getId()).isEqualTo(professional.getId());
        assertThat(profile.getRole().name()).isEqualTo("PROFESSIONAL");
        assertThat(profile.getFirstName()).isEqualTo(professional.getFirstName());
        assertThat(profile.getLastName()).isEqualTo(professional.getLastName());
        assertThat(profile.getSpecialization()).isEqualTo(professional.getSpecialization());
        assertThat(profile.getPhoneNumber()).isEqualTo(professional.getPhoneNumber());
        assertThat(profile.getBio()).isEqualTo(professional.getBio());
        assertThat(profile.getWorkplaceName()).isEqualTo(professional.getWorkplaceName());
        assertThat(profile.getCity()).isEqualTo(professional.getCity());
        assertThat(profile.getBirthDate()).isNull();
        assertThat(profile.getHeightCm()).isNull();
        assertThat(profile.getPrimaryGoal()).isNull();
        assertThat(profile.getGender()).isNull();

        assertThat(account.getId()).isEqualTo(professional.getId());
        assertThat(account.getEmail()).isEqualTo(professional.getEmail());
        assertThat(account.getRole()).isEqualTo("PROFESSIONAL");
        assertThat(account.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(account.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Cliente deve aggiornare solo i campi consentiti del proprio profilo")
    void shouldPartiallyUpdateClientProfileAndRejectProfessionalFields() {
        ClientProfile client = createActiveClient();
        authenticateAs(client.getEmail(), "CLIENT");

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setFirstName(" Luca ");
        request.setHeightCm(new BigDecimal("175.50"));
        request.setPrimaryGoal(" Aumentare la forza ");
        request.setMedicalNotes(" Nessuna controindicazione. ");

        MyProfileResponse response = meService.updateMyProfile(request);

        assertThat(response.getFirstName()).isEqualTo("Luca");
        assertThat(response.getLastName()).isEqualTo(client.getLastName());
        assertThat(response.getBirthDate()).isEqualTo(client.getBirthDate());
        assertThat(response.getHeightCm()).isEqualByComparingTo("175.50");
        assertThat(response.getPrimaryGoal()).isEqualTo("Aumentare la forza");
        assertThat(response.getMedicalNotes()).isEqualTo("Nessuna controindicazione.");
        assertThat(response.getGender()).isEqualTo(client.getGender());
        assertThat(response.getSpecialization()).isNull();
        assertThat(response.getBio()).isNull();

        UpdateMyProfileRequest invalidRequest = new UpdateMyProfileRequest();
        invalidRequest.setBio("Campo riservato al professionista.");

        assertThatThrownBy(() -> meService.updateMyProfile(invalidRequest))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("PROFILE_FIELDS_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Professionista deve aggiornare solo i campi consentiti del proprio profilo")
    void shouldPartiallyUpdateProfessionalProfileAndRejectClientFields() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setLastName(" Verdi ");
        request.setPhoneNumber(" +39 333 1234567 ");
        request.setBio(" Personal trainer certificato. ");
        request.setWorkplaceName(" Support Gym ");
        request.setCity(" Roma ");

        MyProfileResponse response = meService.updateMyProfile(request);

        assertThat(response.getFirstName()).isEqualTo(professional.getFirstName());
        assertThat(response.getLastName()).isEqualTo("Verdi");
        assertThat(response.getSpecialization()).isEqualTo(professional.getSpecialization());
        assertThat(response.getPhoneNumber()).isEqualTo("+39 333 1234567");
        assertThat(response.getBio()).isEqualTo("Personal trainer certificato.");
        assertThat(response.getWorkplaceName()).isEqualTo("Support Gym");
        assertThat(response.getCity()).isEqualTo("Roma");
        assertThat(response.getBirthDate()).isNull();
        assertThat(response.getPrimaryGoal()).isNull();

        UpdateMyProfileRequest invalidRequest = new UpdateMyProfileRequest();
        invalidRequest.setPrimaryGoal("Campo riservato al cliente.");

        assertThatThrownBy(() -> meService.updateMyProfile(invalidRequest))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("PROFILE_FIELDS_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Cliente deve aggiornare il proprio stato operativo")
    void shouldUpdateClientOperationalStatus() {
        ClientProfile client = createActiveClient();
        authenticateAs(client.getEmail(), "CLIENT");

        UpdateOperationalStatusRequest request = new UpdateOperationalStatusRequest();
        request.setOperationalStatus(" pausa ");

        MyProfileResponse response = meService.updateMyOperationalStatus(request);

        assertThat(response.getOperationalStatus()).isEqualTo("PAUSA");

        ClientProfile updatedClient = clientProfileRepository.findById(client.getId())
                .orElseThrow();
        assertThat(updatedClient.getOperationalStatus()).isEqualTo(ClientOperationalStatus.PAUSA);
    }

    @Test
    @DisplayName("Professionista deve aggiornare il proprio stato operativo")
    void shouldUpdateProfessionalOperationalStatus() {
        ProfessionalProfile professional = createActivePersonalTrainer();
        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        UpdateOperationalStatusRequest request = new UpdateOperationalStatusRequest();
        request.setOperationalStatus(" ferie ");

        MyProfileResponse response = meService.updateMyOperationalStatus(request);

        assertThat(response.getOperationalStatus()).isEqualTo("FERIE");

        ProfessionalProfile updatedProfessional = professionalProfileRepository.findById(professional.getId())
                .orElseThrow();
        assertThat(updatedProfessional.getOperationalStatus()).isEqualTo(ProfessionalOperationalStatus.FERIE);
    }

    @Test
    @DisplayName("Stato operativo non valido deve essere rifiutato")
    void shouldRejectInvalidOperationalStatus() {
        ClientProfile client = createActiveClient();
        authenticateAs(client.getEmail(), "CLIENT");

        UpdateOperationalStatusRequest request = new UpdateOperationalStatusRequest();
        request.setOperationalStatus("NON_VALIDO");

        assertThatThrownBy(() -> meService.updateMyOperationalStatus(request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("INVALID_OPERATIONAL_STATUS"));

        ClientProfile unchangedClient = clientProfileRepository.findById(client.getId())
                .orElseThrow();
        assertThat(unchangedClient.getOperationalStatus()).isEqualTo(ClientOperationalStatus.ATTIVO);
    }

    @Test
    @DisplayName("Professionista deve rimuovere gli URL profilo inviando valori vuoti")
    void shouldRemoveProfessionalProfileUrlsWhenBlankValuesAreProvided() {
        ProfessionalProfile professional = createActivePersonalTrainer();

        professional.setInstagramUrl("https://instagram.com/mario.rossi");
        professional.setWebsiteUrl("https://www.mariorossi.it");

        professionalProfileRepository.save(professional);

        authenticateAs(professional.getEmail(), "PROFESSIONAL");

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setInstagramUrl("");
        request.setWebsiteUrl("   ");

        Set<ConstraintViolation<UpdateMyProfileRequest>> violations
                = validator.validate(request);

        assertThat(violations).isEmpty();

        MyProfileResponse response = meService.updateMyProfile(request);

        assertThat(response.getInstagramUrl()).isNull();
        assertThat(response.getWebsiteUrl()).isNull();
    }

    @Test
    @DisplayName("Professionista non deve salvare URL profilo senza protocollo http o https")
    void shouldRejectProfessionalProfileUrlWithoutProtocol() {
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setInstagramUrl("instagram.com/mario.rossi");
        request.setWebsiteUrl("www.mariorossi.it");

        Set<ConstraintViolation<UpdateMyProfileRequest>> violations
                = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "L'URL Instagram deve iniziare con http:// o https://",
                        "L'URL del sito web deve iniziare con http:// o https://"
                );
    }

    private ProfessionalProfile createActivePersonalTrainer() {
        String email = "professional-" + UUID.randomUUID() + "@test.com";

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

    private ClientProfile createActiveClient() {
        String email = "client-" + UUID.randomUUID() + "@test.com";

        ClientProfile client = new ClientProfile(
                "Luigi",
                "Bianchi",
                email,
                "password123",
                LocalDate.now().minusYears(30),
                BigDecimal.valueOf(180),
                "Migliorare la forma fisica",
                Gender.MALE
        );

        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setActive(true);

        return clientProfileRepository.save(client);
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
