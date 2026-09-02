package it.zuperman.support_trainer.email.listener;

import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.zuperman.support_trainer.email.config.PasswordRecoveryDeliveryExecutorConfiguration;
import it.zuperman.support_trainer.email.event.PasswordRecoveryRequestedEvent;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.port.PasswordRecoverySender;
import it.zuperman.support_trainer.email.service.PasswordRecoveryLinkBuilder;

@Component
public class PasswordRecoveryEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryEventListener.class);

    private final PasswordRecoveryLinkBuilder linkBuilder;
    private final PasswordRecoverySender sender;
    private final TaskExecutor passwordRecoveryDeliveryExecutor;

    public PasswordRecoveryEventListener(
            PasswordRecoveryLinkBuilder linkBuilder,
            PasswordRecoverySender sender,
            @Qualifier(PasswordRecoveryDeliveryExecutorConfiguration.EXECUTOR_BEAN_NAME)
            TaskExecutor passwordRecoveryDeliveryExecutor
    ) {
        this.linkBuilder = linkBuilder;
        this.sender = sender;
        this.passwordRecoveryDeliveryExecutor = passwordRecoveryDeliveryExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onPasswordRecoveryRequested(PasswordRecoveryRequestedEvent event) {
        try {
            passwordRecoveryDeliveryExecutor.execute(() -> deliver(event));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn(
                    "Password recovery delivery enqueue rejected correlationId={} errorType={}",
                    event.correlationId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void deliver(PasswordRecoveryRequestedEvent event) {
        try {
            String recoveryUrl = linkBuilder.build(event.token());
            sender.send(new PasswordRecoveryMessage(
                    event.recipient(),
                    recoveryUrl,
                    event.expiresAt(),
                    event.correlationId()
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Password recovery delivery failed correlationId={} errorType={}",
                    event.correlationId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
