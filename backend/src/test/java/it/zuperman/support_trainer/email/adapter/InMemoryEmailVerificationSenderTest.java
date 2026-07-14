package it.zuperman.support_trainer.email.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryEmailVerificationSenderTest {

    private final InMemoryEmailVerificationSender sender = new InMemoryEmailVerificationSender();

    @Test
    void shouldStoreImmutableSnapshotsAndFindByCorrelationId() {
        EmailVerificationMessage message = message(1);
        sender.send(message);

        List<EmailVerificationMessage> snapshot = sender.messages();
        assertThat(snapshot).containsExactly(message);
        assertThat(sender.findByCorrelationId(message.correlationId())).contains(message);
        assertThatThrownBy(() -> snapshot.add(message)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldClearStateForTesting() {
        sender.send(message(1));
        sender.clearForTesting();

        assertThat(sender.messages()).isEmpty();
    }

    @Test
    void shouldSupportBasicConcurrentWrites() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                int messageIndex = index;
                tasks.add(() -> {
                    sender.send(message(messageIndex));
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(sender.messages()).hasSize(40);
    }

    private EmailVerificationMessage message(int index) {
        return new EmailVerificationMessage(
                "recipient-%d@example.com".formatted(index),
                "https://frontend.example/verify-email#token=token-%d".formatted(index),
                Instant.parse("2026-07-15T12:00:00Z"),
                EmailVerificationReason.REGISTRATION,
                UUID.randomUUID()
        );
    }
}
