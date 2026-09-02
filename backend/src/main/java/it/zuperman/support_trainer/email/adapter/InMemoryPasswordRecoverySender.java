package it.zuperman.support_trainer.email.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.port.PasswordRecoverySender;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "IN_MEMORY")
public class InMemoryPasswordRecoverySender implements PasswordRecoverySender {

    private final ConcurrentLinkedQueue<PasswordRecoveryMessage> messages = new ConcurrentLinkedQueue<>();

    @Override
    public void send(PasswordRecoveryMessage message) {
        messages.add(Objects.requireNonNull(message, "message must not be null"));
    }

    public List<PasswordRecoveryMessage> messages() {
        return List.copyOf(messages);
    }

    public Optional<PasswordRecoveryMessage> findByCorrelationId(UUID correlationId) {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        return messages.stream()
                .filter(message -> message.correlationId().equals(correlationId))
                .findFirst();
    }

    /**
     * Clears the local inbox. Intended exclusively for tests and local debugging.
     */
    public void clearForTesting() {
        messages.clear();
    }
}
