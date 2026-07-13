package it.zuperman.support_trainer.common.time;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirectSystemTimeUsageTest {

    private static final Pattern DIRECT_TIME_ACCESS = Pattern.compile(
            "(?:LocalDateTime|LocalDate|Instant|ZonedDateTime|OffsetDateTime)\\.now\\s*\\("
                    + "|new\\s+Date\\s*\\("
                    + "|System\\.currentTimeMillis\\s*\\("
                    + "|Calendar\\.getInstance\\s*\\("
    );

    @Test
    void applicationCodeShouldUseOnlyApplicationTimeProvider() throws IOException {
        Path sourceRoot = findMainSourceRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relativePath = sourceRoot.relativize(source);
                if (relativePath.toString().replace('\\', '/').contains("/common/time/")) {
                    continue;
                }

                List<String> lines = Files.readAllLines(source);
                for (int index = 0; index < lines.size(); index++) {
                    if (DIRECT_TIME_ACCESS.matcher(lines.get(index)).find()) {
                        violations.add(relativePath + ":" + (index + 1) + " -> " + lines.get(index).trim());
                    }
                }
            }
        }

        assertThat(violations)
                .as("Accessi diretti all'orologio: usare ApplicationTimeProvider")
                .isEmpty();
    }

    private static Path findMainSourceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path directSourceRoot = current.resolve(Path.of("src", "main", "java"));
            if (Files.isDirectory(directSourceRoot)) {
                return directSourceRoot;
            }

            Path backendSourceRoot = current.resolve(Path.of("backend", "src", "main", "java"));
            if (Files.isDirectory(backendSourceRoot)) {
                return backendSourceRoot;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Impossibile individuare src/main/java per il controllo temporale");
    }
}
