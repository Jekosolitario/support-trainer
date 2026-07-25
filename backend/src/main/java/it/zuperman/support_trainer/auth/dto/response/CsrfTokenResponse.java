package it.zuperman.support_trainer.auth.dto.response;

public record CsrfTokenResponse(String token, String headerName) {

    public CsrfTokenResponse {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("headerName must not be null or blank");
        }
    }
}
