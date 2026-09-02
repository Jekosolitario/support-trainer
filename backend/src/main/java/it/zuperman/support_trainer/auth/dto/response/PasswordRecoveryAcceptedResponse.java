package it.zuperman.support_trainer.auth.dto.response;

public record PasswordRecoveryAcceptedResponse(String message) {

    public static final String NEUTRAL_MESSAGE =
            "Se esiste un account associato a questa email, riceverai le istruzioni per reimpostare la password.";

    public static PasswordRecoveryAcceptedResponse neutral() {
        return new PasswordRecoveryAcceptedResponse(NEUTRAL_MESSAGE);
    }
}
