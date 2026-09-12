package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColdRegionPreparerTest {
    @Test
    void generatedCandidateIsSkippedBeforeAnyChunkLoad() {
        FakeBoundary boundary = new FakeBoundary();
        ColdCoordinate first = ColdCoordinate.forGeneration(1);
        boundary.generated.add(new Position(first.chunkX() - 1, first.chunkZ() + 1));
        ColdRegionPreparer<FakeChunk> preparer = new ColdRegionPreparer<>(boundary, 1);

        CompletableFuture<ColdRegionPreparer.Result<FakeChunk>> completion =
                preparer.start().toCompletableFuture();

        assertEquals(0, boundary.launches.size());
        assertFalse(completion.isDone());
        boundary.advanceTick();
        assertEquals(List.of(new Position(1007, -1001)), boundary.launches);
        assertEquals(18, boundary.generatedChecks.size(),
                "both complete 3x3 candidates must be checked before loading");
    }

    @Test
    void launchesAtMostOneAsyncLoadPerTickAndNeverWaitsForCompletion() {
        FakeBoundary boundary = new FakeBoundary();
        ColdRegionPreparer<FakeChunk> preparer = new ColdRegionPreparer<>(boundary, 1);

        CompletableFuture<ColdRegionPreparer.Result<FakeChunk>> completion =
                preparer.start().toCompletableFuture();

        assertEquals(1, boundary.launches.size());
        assertFalse(completion.isDone(), "start must not wait for the incomplete Paper future");
        boundary.completeCurrentOffThread();
        assertEquals(1, boundary.launches.size(),
                "completion must not launch another load in the same tick");
        assertEquals(0, boundary.ticketed.size(),
                "completion must be marshalled to the primary thread before ticketing");

        boundary.advanceTick();
        assertEquals(2, boundary.launches.size());
        assertEquals(1, boundary.ticketed.size());
        assertEquals(List.of(0, 1), boundary.launchTicks);
    }

    @Test
    void exactThreeByThreeTicketsAreReleasedAfterSuccess() {
        FakeBoundary boundary = new FakeBoundary();
        ColdRegionPreparer<FakeChunk> preparer = new ColdRegionPreparer<>(boundary, 1);
        CompletableFuture<ColdRegionPreparer.Result<FakeChunk>> completion =
                preparer.start().toCompletableFuture();

        boundary.completeAllLoads();
        ColdRegionPreparer.Result<FakeChunk> result = completion.join();

        assertFalse(result.previouslyGenerated());
        assertEquals("previouslyGenerated=false checkedChunks=9", result.coldEvidence());
        assertEquals(Set.of(
                new Position(1003, -1001), new Position(1003, -1000),
                new Position(1003, -999), new Position(1004, -1001),
                new Position(1004, -1000), new Position(1004, -999),
                new Position(1005, -1001), new Position(1005, -1000),
                new Position(1005, -999)), new HashSet<>(boundary.ticketedPositions()));

        preparer.releaseTickets();

        assertEquals(boundary.ticketed, boundary.released);
        assertEquals(9, boundary.released.size());
    }

    @Test
    void loadFailureReleasesEveryTicketAlreadyAddedAndNoOthers() {
        FakeBoundary boundary = new FakeBoundary();
        ColdRegionPreparer<FakeChunk> preparer = new ColdRegionPreparer<>(boundary, 1);
        CompletableFuture<ColdRegionPreparer.Result<FakeChunk>> completion =
                preparer.start().toCompletableFuture();
        boundary.completeCurrentOffThread();
        boundary.advanceTick();

        boundary.failCurrentOffThread();
        boundary.advanceTick();

        assertThrows(CompletionException.class, completion::join);
        assertEquals(1, boundary.ticketed.size());
        assertEquals(boundary.ticketed, boundary.released);
    }

    @Test
    void disableCancellationReleasesTicketsOnPrimaryThread() {
        FakeBoundary boundary = new FakeBoundary();
        ColdRegionPreparer<FakeChunk> preparer = new ColdRegionPreparer<>(boundary, 1);
        CompletableFuture<ColdRegionPreparer.Result<FakeChunk>> completion =
                preparer.start().toCompletableFuture();
        boundary.completeCurrentOffThread();
        boundary.advanceTick();

        preparer.cancel();

        assertTrue(completion.isCompletedExceptionally());
        assertEquals(boundary.ticketed, boundary.released);
        assertEquals(1, boundary.released.size());
    }

    private record Position(int x, int z) {}
    private record FakeChunk(Position position) {}

    private static final class FakeBoundary
            implements ColdRegionPreparer.Boundary<FakeChunk> {
        private final Set<Position> generated = new HashSet<>();
        private final List<Position> generatedChecks = new ArrayList<>();
        private final List<Position> launches = new ArrayList<>();
        private final List<Integer> launchTicks = new ArrayList<>();
        private final List<FakeChunk> ticketed = new ArrayList<>();
        private final List<FakeChunk> released = new ArrayList<>();
        private final ArrayDeque<Runnable> nextTick = new ArrayDeque<>();
        private CompletableFuture<FakeChunk> current;
        private int tick;
        private boolean primary = true;

        @Override
        public boolean isPrimaryThread() {
            return primary;
        }

        @Override
        public boolean isChunkGenerated(int chunkX, int chunkZ) {
            requirePrimary();
            Position position = new Position(chunkX, chunkZ);
            generatedChecks.add(position);
            return generated.contains(position);
        }

        @Override
        public CompletableFuture<FakeChunk> loadChunkAsync(int chunkX, int chunkZ) {
            requirePrimary();
            Position position = new Position(chunkX, chunkZ);
            launches.add(position);
            launchTicks.add(tick);
            current = new CompletableFuture<>();
            return current;
        }

        @Override
        public void addTicket(FakeChunk chunk) {
            requirePrimary();
            ticketed.add(chunk);
        }

        @Override
        public void removeTicket(FakeChunk chunk) {
            requirePrimary();
            released.add(chunk);
        }

        @Override
        public void nextTick(Runnable task) {
            nextTick.add(task);
        }

        void completeCurrentOffThread() {
            primary = false;
            Position position = launches.get(launches.size() - 1);
            current.complete(new FakeChunk(position));
            primary = true;
        }

        void failCurrentOffThread() {
            primary = false;
            current.completeExceptionally(new IllegalStateException("async generation failed"));
            primary = true;
        }

        void advanceTick() {
            tick++;
            List<Runnable> tasks = List.copyOf(nextTick);
            nextTick.clear();
            tasks.forEach(Runnable::run);
        }

        void completeAllLoads() {
            while (!currentCompletionDone()) {
                completeCurrentOffThread();
                advanceTick();
            }
        }

        private boolean currentCompletionDone() {
            return launches.size() == 9 && current != null && current.isDone()
                    && nextTick.isEmpty();
        }

        List<Position> ticketedPositions() {
            return ticketed.stream().map(FakeChunk::position).toList();
        }

        private void requirePrimary() {
            if (!primary) throw new AssertionError("ticket/probe/load boundary left primary thread");
        }
    }
}
