package it.zuperman.support_trainer.email.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import it.zuperman.support_trainer.email.config.EmailProperties;

@Component
public class EmailVerificationLinkBuilder {

    private static final int MAX_TOKEN_LENGTH = 500;

    private final EmailProperties emailProperties;

    public EmailVerificationLinkBuilder(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    public String build(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("token must not exceed 500 characters");
        }

        return UriComponentsBuilder.fromUri(emailProperties.verificationPageUrl())
                .fragment("token={token}")
                .buildAndExpand(token)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }
}
