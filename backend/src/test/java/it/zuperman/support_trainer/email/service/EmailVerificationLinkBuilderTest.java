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
class EmailVerificationLinkBuilderTest {

    @Test
    void shouldBuildFragmentForSimplePageUrl() {
        String link = builder("https://frontend.example/verify-email").build("verification-token");

        assertThat(link).isEqualTo("https://frontend.example/verify-email#token=verification-token");
    }

    @Test
    void shouldPreserveBasePathWithoutDoubleSlash() {
        String link = builder("https://frontend.example/app/verify-email").build("token");

        assertThat(link).isEqualTo("https://frontend.example/app/verify-email#token=token");
        assertThat(link.substring("https://".length())).doesNotContain("//");
    }

    @Test
    void shouldEncodeAndDecodeSpecialTokenCharacters() {
        String token = "a b+#%/=?ü";
        String link = builder("https://frontend.example/verify-email").build(token);
        URI uri = URI.create(link);

        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getRawFragment()).startsWith("token=").contains("%20", "%23", "%25");
        assertThat(uri.getFragment()).isEqualTo("token=" + token);
    }

    @Test
    void shouldSupportFiveHundredCharacterToken() {
        String token = "a".repeat(500);
        String link = builder("https://frontend.example/verify-email").build(token);

        assertThat(URI.create(link).getFragment()).isEqualTo("token=" + token);
    }

    @Test
    void shouldNeverCreateQueryString() {
        URI link = URI.create(builder("http://localhost:5173/verify-email").build("token"));

        assertThat(link.getQuery()).isNull();
        assertThat(link.getFragment()).isEqualTo("token=token");
    }

    @Test
    void shouldNotExposeOrLogToken(CapturedOutput output) {
        String token = "sensitive-verification-token";
        EmailVerificationLinkBuilder builder = builder("https://frontend.example/verify-email");
        String link = builder.build(token);

        assertThat(builder.toString()).doesNotContain(token, link);
        assertThat(output.getAll()).doesNotContain(token, link);
    }

    private EmailVerificationLinkBuilder builder(String pageUrl) {
        return new EmailVerificationLinkBuilder(
                new EmailProperties(EmailMode.DISABLED, URI.create(pageUrl), null, null)
        );
    }
}
