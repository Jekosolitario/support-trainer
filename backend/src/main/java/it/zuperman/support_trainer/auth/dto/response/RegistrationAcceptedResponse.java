package it.zuperman.support_trainer.auth.dto.response;

/**
 * Neutral acknowledgement for a public registration request.
 *
 * <p>The response intentionally does not disclose whether an account was
 * created or already existed.</p>
 */
public record RegistrationAcceptedResponse(String message) {

    public static final String NEUTRAL_MESSAGE =
            "Se la registrazione può essere completata, riceverai le istruzioni per verificare l'indirizzo email";

    public static RegistrationAcceptedResponse neutral() {
        return new RegistrationAcceptedResponse(NEUTRAL_MESSAGE);
    }
}
