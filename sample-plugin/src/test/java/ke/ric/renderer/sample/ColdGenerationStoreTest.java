package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ColdGenerationStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void counterReadAndAtomicWriteRunOnlyThroughTheAsyncExecutor() throws Exception {
        QueueExecutor executor = new QueueExecutor();
        ColdGenerationStore store = new ColdGenerationStore(temporaryDirectory, executor);

        CompletionStage<Long> first = store.readNextCandidate();
        assertFalse(first.toCompletableFuture().isDone());
        assertFalse(Files.exists(temporaryDirectory.resolve("live-gate-generation.txt")));
        executor.runNext();
        assertEquals(1L, first.toCompletableFuture().getNow(-1L));

        CompletionStage<Void> persisted = store.persist(7L);
        assertFalse(persisted.toCompletableFuture().isDone());
        executor.runNext();
        assertEquals("7\n", Files.readString(
                temporaryDirectory.resolve("live-gate-generation.txt")));

        CompletionStage<Long> next = store.readNextCandidate();
        executor.runNext();
        assertEquals(8L, next.toCompletableFuture().getNow(-1L));
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
