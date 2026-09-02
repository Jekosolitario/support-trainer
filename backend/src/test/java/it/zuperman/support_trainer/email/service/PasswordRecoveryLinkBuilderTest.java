package it.zuperman.support_trainer.email.service;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import it.zuperman.support_trainer.email.config.EmailMode;
import it.zuperman.support_trainer.email.config.EmailProperties;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryLinkBuilderTest {

    @Test
    void shouldBuildFragmentForSimplePageUrl() {
        String link = builder("https://frontend.example/reset-password").build("recovery-token");

        assertThat(link).isEqualTo("https://frontend.example/reset-password#token=recovery-token");
    }

    @Test
    void shouldPreserveBasePathWithoutDoubleSlash() {
        String link = builder("https://frontend.example/app/reset-password").build("token");

        assertThat(link).isEqualTo("https://frontend.example/app/reset-password#token=token");
        assertThat(link.substring("https://".length())).doesNotContain("//");
    }

    @Test
    void shouldEncodeAndDecodeSpecialTokenCharacters() {
        String token = "a b+#%/=?ü";
        String link = builder("https://frontend.example/reset-password").build(token);
        URI uri = URI.create(link);

        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getRawFragment()).startsWith("token=").contains("%20", "%23", "%25");
        assertThat(uri.getFragment()).isEqualTo("token=" + token);
    }

    @Test
    void shouldNeverCreateQueryString() {
        URI link = URI.create(builder("http://localhost:5173/reset-password").build("token"));

        assertThat(link.getQuery()).isNull();
        assertThat(link.getFragment()).isEqualTo("token=token");
    }

    @Test
    void shouldNotExposeOrLogToken(CapturedOutput output) {
        String token = "sensitive-recovery-token";
        PasswordRecoveryLinkBuilder linkBuilder = builder("https://frontend.example/reset-password");
        String link = linkBuilder.build(token);

        assertThat(linkBuilder.toString()).doesNotContain(token, link);
        assertThat(output.getAll()).doesNotContain(token, link);
    }

    private PasswordRecoveryLinkBuilder builder(String pageUrl) {
        return new PasswordRecoveryLinkBuilder(
                new EmailProperties(
                        EmailMode.DISABLED,
                        URI.create("https://frontend.example/verify-email"),
                        URI.create(pageUrl),
                        null,
                        null
                )
        );
    }
}
