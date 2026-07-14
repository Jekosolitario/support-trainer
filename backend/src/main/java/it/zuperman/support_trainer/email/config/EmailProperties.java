package it.zuperman.support_trainer.email.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.email")
public record EmailProperties(
        @DefaultValue("DISABLED") EmailMode mode,
        URI verificationPageUrl
) {

    public EmailProperties {
        if (mode == null) {
            throw new IllegalArgumentException("app.email.mode must not be null");
        }
        validateVerificationPageUrl(verificationPageUrl);
    }

    private static void validateVerificationPageUrl(URI uri) {
        if (uri == null) {
            throw invalidVerificationPageUrl();
        }

        String scheme = uri.getScheme();
        String rawPath = uri.getRawPath();
        if (!uri.isAbsolute()
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || rawPath == null
                || rawPath.isBlank()
                || rawPath.equals("/")
                || rawPath.endsWith("/")
                || rawPath.contains("//")
                || !uri.normalize().equals(uri)) {
            throw invalidVerificationPageUrl();
        }
    }

    private static IllegalArgumentException invalidVerificationPageUrl() {
        return new IllegalArgumentException(
                "app.email.verification-page-url must be an absolute HTTP or HTTPS page URL "
                + "without query, fragment, trailing slash or ambiguous path"
        );
    }
}
