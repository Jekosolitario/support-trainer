package it.zuperman.support_trainer.security.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins
) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origins must not be empty");
        }

        LinkedHashSet<String> normalizedOrigins = new LinkedHashSet<>();
        for (String origin : allowedOrigins) {
            normalizedOrigins.add(normalizeOrigin(origin));
        }
        allowedOrigins = List.copyOf(normalizedOrigins);
    }

    private static String normalizeOrigin(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank()) {
            throw new IllegalArgumentException("app.cors.allowed-origins must not contain blank values");
        }

        String origin = rawOrigin.trim();
        if (origin.contains("*")) {
            throw new IllegalArgumentException("app.cors.allowed-origins must not contain wildcards");
        }

        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException exception) {
            throw invalidOrigin(exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalidOrigin(null);
        }
        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawAuthority().endsWith(":")
                || uri.getPort() > 65535) {
            throw invalidOrigin(null);
        }
        if ((uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidOrigin(null);
        }

        try {
            return new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null
            ).toASCIIString();
        } catch (URISyntaxException exception) {
            throw invalidOrigin(exception);
        }
    }

    private static IllegalArgumentException invalidOrigin(Exception cause) {
        return new IllegalArgumentException(
                "app.cors.allowed-origins must contain exact HTTP or HTTPS origins without paths, queries or fragments",
                cause
        );
    }
}
