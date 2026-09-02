package it.zuperman.support_trainer.email.support;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;

public final class PasswordRecoveryInbox {

    private PasswordRecoveryInbox() {
    }

    public static List<PasswordRecoveryMessage> awaitSize(InMemoryPasswordRecoverySender sender, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<PasswordRecoveryMessage> messages = sender.messages();
        while (messages.size() < expected) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                        "Expected at least " + expected + " password recovery messages, had " + messages.size()
                );
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            messages = sender.messages();
        }
        return messages;
    }
}
