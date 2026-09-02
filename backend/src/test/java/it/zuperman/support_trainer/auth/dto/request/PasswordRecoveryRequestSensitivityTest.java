package it.zuperman.support_trainer.auth.dto.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryRequestSensitivityTest {

    @Test
    void requestToStringMustNotExposeEmail() {
        PasswordRecoveryRequest request = new PasswordRecoveryRequest("user@example.com");
        assertThat(request.toString()).doesNotContain("user@example.com");
    }

    @Test
    void confirmToStringMustNotExposeTokenOrPassword() {
        PasswordRecoveryConfirmRequest request = new PasswordRecoveryConfirmRequest(
                "raw-recovery-token",
                "NewPass123!"
        );
        assertThat(request.toString())
                .doesNotContain("raw-recovery-token", "NewPass123!");
    }
}
