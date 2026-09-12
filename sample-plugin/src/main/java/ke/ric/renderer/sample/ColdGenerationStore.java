package ke.ric.renderer.sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** Persists the monotonic cold-candidate cursor exclusively on an asynchronous executor. */
final class ColdGenerationStore {
    private final Path directory;
    private final Path counter;
    private final Path staging;
    private final Executor executor;

    ColdGenerationStore(Path directory, Executor executor) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.counter = directory.resolve("live-gate-generation.txt");
        this.staging = directory.resolve(".live-gate-generation.tmp");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    CompletionStage<Long> readNextCandidate() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long previous = Files.isRegularFile(counter)
                        ? Long.parseLong(Files.readString(counter).trim()) : 0L;
                return Math.addExact(previous, 1L);
            } catch (IOException | NumberFormatException failure) {
                throw new IllegalStateException(
                        "cold scene generation state is unavailable", failure);
            }
        }, executor);
    }

    CompletionStage<Void> persist(long generation) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                Files.writeString(staging, Long.toString(generation) + "\n");
                try {
                    Files.move(staging, counter, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(staging, counter, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "cold scene generation state is unavailable", failure);
            }
        }, executor);
    }
}
