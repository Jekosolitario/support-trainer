package it.zuperman.support_trainer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class AuthControllerRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Non deve registrare due professionisti con la stessa email")
    void shouldRejectProfessionalRegistrationWithDuplicateEmail() throws Exception {
        String requestBody = """
                {
                  "firstName": "Marco",
                  "lastName": "Ferrari",
                  "email": "duplicate.professional@example.com",
                  "password": "Password123!",
                  "specialization": "PERSONAL_TRAINER"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("Non deve registrare un cliente con codice invito inesistente")
    void shouldRejectClientRegistrationWithMissingInviteCode() throws Exception {
        String requestBody = """
                {
                  "firstName": "Laura",
                  "lastName": "Conti",
                  "email": "laura.conti.invite.notfound@example.com",
                  "password": "Password123!",
                  "birthDate": "1995-01-01",
                  "heightCm": 170.00,
                  "primaryGoal": "Migliorare la forma fisica",
                  "gender": "FEMALE",
                  "inviteCode": "INVITE-NOT-EXISTING"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVITE_CODE_NOT_FOUND"));
    }
}
