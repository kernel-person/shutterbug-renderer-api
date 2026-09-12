package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAsyncChunkLoaderTest {
    @Test
    void invokesOnlyThePublicGeneratingAsyncPaperMethodWithoutWaiting() {
        FakePaperWorld world = new FakePaperWorld();

        CompletionStage<FakeChunk> result = PaperAsyncChunkLoader.invoke(
                world, FakeChunk.class, 17, -23);

        assertEquals(1, world.invocations);
        assertEquals(17, world.chunkX);
        assertEquals(-23, world.chunkZ);
        assertTrue(world.generate);
        assertFalse(result.toCompletableFuture().isDone());
        FakeChunk chunk = new FakeChunk();
        world.result.complete(chunk);
        assertSame(chunk, result.toCompletableFuture().getNow(null));
    }

    @Test
    void missingPaperAsyncMethodFailsClosed() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PaperAsyncChunkLoader.invoke(new Object(), FakeChunk.class, 0, 0));

        assertTrue(failure.getMessage().contains("getChunkAtAsync"));
    }

    @Test
    void wrongAsyncResultTypeFailsClosedOnCompletion() {
        WrongPaperWorld world = new WrongPaperWorld();
        CompletionStage<FakeChunk> result = PaperAsyncChunkLoader.invoke(
                world, FakeChunk.class, 1, 2);

        world.result.complete("not a chunk");

        assertThrows(Exception.class, () -> result.toCompletableFuture().getNow(null));
    }

    public static final class FakePaperWorld {
        final CompletableFuture<FakeChunk> result = new CompletableFuture<>();
        int invocations;
        int chunkX;
        int chunkZ;
        boolean generate;

        public CompletableFuture<FakeChunk> getChunkAtAsync(
                int chunkX, int chunkZ, boolean generate) {
            invocations++;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.generate = generate;
            return result;
        }
    }

    public static final class WrongPaperWorld {
        final CompletableFuture<String> result = new CompletableFuture<>();

        public CompletableFuture<String> getChunkAtAsync(int x, int z, boolean generate) {
            return result;
        }
    }

    private static final class FakeChunk {}
}
