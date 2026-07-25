package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MySqlTestLifecycleSupportTest {

    @Test
    @DisplayName("close fallisce: DROP viene tentato e la failure close non è persa")
    void closeFailureStillAttemptsDropAndIsRethrown() {
        AtomicBoolean dropAttempted = new AtomicBoolean(false);
        RuntimeException closeFailure = new RuntimeException("close failed");

        Throwable result = MySqlTestLifecycleSupport.runCleanup(
                null,
                () -> {
                    throw closeFailure;
                },
                () -> dropAttempted.set(true)
        );

        assertThat(dropAttempted).isTrue();
        assertThat(result).isSameAs(closeFailure);

        Throwable thrown = catchThrowable(() -> MySqlTestLifecycleSupport.rethrowIfPresent(result));
        assertThat(thrown).isSameAs(closeFailure);
    }

    @Test
    @DisplayName("failure primaria + DROP fallisce: primaria rilanciata, DROP suppressed")
    void primaryFailurePreservedWhenDropFails() {
        IllegalStateException primary = new IllegalStateException("test body failed");
        RuntimeException dropFailure = new RuntimeException("drop failed");
        AtomicBoolean dropAttempted = new AtomicBoolean(false);

        Throwable result = MySqlTestLifecycleSupport.runCleanup(
                primary,
                () -> {
                },
                () -> {
                    dropAttempted.set(true);
                    throw dropFailure;
                }
        );

        assertThat(dropAttempted).isTrue();
        assertThat(result).isSameAs(primary);
        assertThat(result.getSuppressed()).containsExactly(dropFailure);

        Throwable thrown = catchThrowable(() -> MySqlTestLifecycleSupport.rethrowIfPresent(result));
        assertThat(thrown).isSameAs(primary);
        assertThat(thrown.getSuppressed()).containsExactly(dropFailure);
    }

    @Test
    @DisplayName("close e DROP falliscono: close primaria, DROP suppressed")
    void closeIsPrimaryWhenBothCloseAndDropFail() {
        RuntimeException closeFailure = new RuntimeException("close failed");
        RuntimeException dropFailure = new RuntimeException("drop failed");
        AtomicBoolean dropAttempted = new AtomicBoolean(false);

        Throwable result = MySqlTestLifecycleSupport.runCleanup(
                null,
                () -> {
                    throw closeFailure;
                },
                () -> {
                    dropAttempted.set(true);
                    throw dropFailure;
                }
        );

        assertThat(dropAttempted).isTrue();
        assertThat(result).isSameAs(closeFailure);
        assertThat(result.getSuppressed()).containsExactly(dropFailure);

        Throwable thrown = catchThrowable(() -> MySqlTestLifecycleSupport.rethrowIfPresent(result));
        assertThat(thrown).isSameAs(closeFailure);
        assertThat(thrown.getSuppressed()).containsExactly(dropFailure);
    }
}
