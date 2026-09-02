package it.zuperman.support_trainer.email.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

@Validated
@ConfigurationProperties("app.email")
public record EmailProperties(
        @DefaultValue("DISABLED") EmailMode mode,
        URI verificationPageUrl,
        @DefaultValue("http://localhost:5173/reset-password") URI passwordRecoveryPageUrl,
        Sender sender,
        Smtp smtp
) {

    public EmailProperties {
        if (mode == null) {
            throw new IllegalArgumentException("app.email.mode must not be null");
        }
        validatePageUrl(mode, verificationPageUrl, "app.email.verification-page-url");
        validatePageUrl(mode, passwordRecoveryPageUrl, "app.email.password-recovery-page-url");
        if (mode == EmailMode.SMTP) {
            validateSender(sender);
            validateSmtp(smtp);
        }
    }

    private static void validatePageUrl(EmailMode mode, URI uri, String propertyName) {
        if (uri == null) {
            throw invalidPageUrl(propertyName);
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
            throw invalidPageUrl(propertyName);
        }

        if (mode != EmailMode.DISABLED
                && scheme.equalsIgnoreCase("http")
                && !isLoopbackHost(uri.getHost())) {
            throw new IllegalArgumentException(
                    propertyName + " must use HTTPS outside the local loopback"
            );
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static void validateSender(Sender sender) {
        if (sender == null || sender.address() == null) {
            throw new IllegalArgumentException("app.email.sender.address must be a valid email address");
        }
        if (sender.name() == null) {
            throw new IllegalArgumentException("app.email.sender.name must not be blank");
        }
    }

    private static void validateSmtp(Smtp smtp) {
        if (smtp == null) {
            throw new IllegalArgumentException("app.email.smtp must be configured when mode is SMTP");
        }
        if (smtp.host() == null) {
            throw new IllegalArgumentException("app.email.smtp.host must not be blank");
        }
        if (smtp.port() == null || smtp.port() < 1 || smtp.port() > 65535) {
            throw new IllegalArgumentException("app.email.smtp.port must be between 1 and 65535");
        }
        validatePositiveTimeout(smtp.connectTimeout(), "app.email.smtp.connect-timeout");
        validatePositiveTimeout(smtp.readTimeout(), "app.email.smtp.read-timeout");
        validatePositiveTimeout(smtp.writeTimeout(), "app.email.smtp.write-timeout");

        if (smtp.auth() && smtp.username() == null) {
            throw new IllegalArgumentException("app.email.smtp.username must not be blank when auth is enabled");
        }
        if (smtp.auth() && smtp.password() == null) {
            throw new IllegalArgumentException("app.email.smtp.password must not be blank when auth is enabled");
        }
        if (!smtp.auth() && (smtp.username() != null || smtp.password() != null)) {
            throw new IllegalArgumentException(
                    "app.email.smtp.username and app.email.smtp.password require auth to be enabled"
            );
        }
    }

    private static void validatePositiveTimeout(Duration timeout, String propertyName) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }

    private static IllegalArgumentException invalidPageUrl(String propertyName) {
        return new IllegalArgumentException(
                propertyName + " must be an absolute HTTP or HTTPS page URL "
                + "without query, fragment, trailing slash or ambiguous path"
        );
    }

    @Override
    public String toString() {
        return "EmailProperties[mode=" + mode
                + ", senderConfigured=" + (sender != null)
                + ", smtpConfigured=" + (smtp != null) + "]";
    }

    public record Sender(String address, String name, String replyTo) {

        public Sender {
            address = normalizeOptional(address);
            name = normalizeOptional(name);
            replyTo = normalizeOptional(replyTo);
            validateOptionalAddress(address, "app.email.sender.address");
            validateOptionalAddress(replyTo, "app.email.sender.reply-to");
            validateLength(name, 150, "app.email.sender.name");
        }

        @Override
        public String toString() {
            return "EmailProperties.Sender[addressConfigured=" + (address != null)
                    + ", nameConfigured=" + (name != null)
                    + ", replyToConfigured=" + (replyTo != null) + "]";
        }
    }

    public record Smtp(
            String host,
            Integer port,
            String username,
            String password,
            @DefaultValue("false") boolean auth,
            @DefaultValue("false") boolean startTls,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout
    ) {

        public Smtp {
            host = normalizeOptional(host);
            username = normalizeOptional(username);
            password = normalizeOptional(password);
            validateLength(host, 253, "app.email.smtp.host");
            validateLength(username, 320, "app.email.smtp.username");
            validateLength(password, 1024, "app.email.smtp.password");
        }

        @Override
        public String toString() {
            return "EmailProperties.Smtp[configured=" + (host != null)
                    + ", auth=" + auth
                    + ", startTls=" + startTls + "]";
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validateLength(String value, int maximum, String propertyName) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(propertyName + " must not exceed " + maximum + " characters");
        }
    }

    private static void validateOptionalAddress(String value, String propertyName) {
        if (value == null) {
            return;
        }
        try {
            InternetAddress internetAddress = new InternetAddress(value, true);
            internetAddress.validate();
            if (!value.equals(internetAddress.getAddress()) || value.length() > 254) {
                throw new IllegalArgumentException(propertyName + " must be a valid email address");
            }
        } catch (AddressException exception) {
            throw new IllegalArgumentException(propertyName + " must be a valid email address");
        }
    }
}
