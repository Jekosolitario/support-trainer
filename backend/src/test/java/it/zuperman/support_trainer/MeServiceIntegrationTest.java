package it.zuperman.support_trainer;

import java.util.List;
import java.util.Set;
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

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.profile.dto.request.UpdateMyProfileRequest;
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
    private Validator validator;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
