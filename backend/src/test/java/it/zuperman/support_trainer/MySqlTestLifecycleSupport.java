package it.zuperman.support_trainer;

/**
 * Test-only helper to preserve a primary lifecycle failure while still attempting cleanup.
 */
final class MySqlTestLifecycleSupport {

    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Throwable;
    }

    private MySqlTestLifecycleSupport() {
    }

    static Throwable attach(Throwable primaryFailure, Throwable secondaryFailure) {
        if (primaryFailure == null) {
            return secondaryFailure;
        }
        primaryFailure.addSuppressed(secondaryFailure);
        return primaryFailure;
    }

    /**
     * Runs close (optional) then drop, preserving {@code primaryFailure} when present.
     */
    static Throwable runCleanup(
            Throwable primaryFailure,
            ThrowingAction closeAction,
            ThrowingAction dropAction
    ) {
        Throwable failure = primaryFailure;
        if (closeAction != null) {
            try {
                closeAction.run();
            } catch (Throwable closeFailure) {
                failure = attach(failure, closeFailure);
            }
        }
        try {
            dropAction.run();
        } catch (Throwable dropFailure) {
            failure = attach(failure, dropFailure);
        }
        return failure;
    }

    static void rethrowIfPresent(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw new Exception(failure);
    }
}
