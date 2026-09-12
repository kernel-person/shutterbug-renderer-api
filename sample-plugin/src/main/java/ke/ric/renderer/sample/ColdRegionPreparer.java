package ke.ric.renderer.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Main-thread state machine for proving and asynchronously loading one cold 3x3 region. */
final class ColdRegionPreparer<T> {
    interface Boundary<T> {
        boolean isPrimaryThread();

        boolean isChunkGenerated(int chunkX, int chunkZ);

        CompletionStage<T> loadChunkAsync(int chunkX, int chunkZ);

        void addTicket(T chunk);

        void removeTicket(T chunk);

        void nextTick(Runnable task);
    }

    record Result<T>(ColdCoordinate coordinate, List<T> chunks,
                     boolean previouslyGenerated) {
        Result {
            Objects.requireNonNull(coordinate, "coordinate");
            chunks = List.copyOf(chunks);
            if (previouslyGenerated || chunks.size() != 9) {
                throw new IllegalArgumentException("cold proof requires nine ungenerated chunks");
            }
        }

        String coldEvidence() {
            return "previouslyGenerated=false checkedChunks=9";
        }
    }

    private final Boundary<T> boundary;
    private final CompletableFuture<Result<T>> completion = new CompletableFuture<>();
    private final List<T> ticketed = new ArrayList<>(9);
    private long generation;
    private List<Position> selected = List.of();
    private int nextIndex;
    private boolean started;
    private boolean released;

    ColdRegionPreparer(Boundary<T> boundary, long initialGeneration) {
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.generation = initialGeneration;
    }

    CompletionStage<Result<T>> start() {
        requirePrimaryThread();
        if (started) throw new IllegalStateException("cold preparation already started");
        started = true;
        selectCandidate();
        return completion;
    }

    void cancel() {
        Runnable cancellation = () -> {
            completion.completeExceptionally(
                    new CancellationException("cold preparation cancelled"));
            releaseNow();
        };
        if (boundary.isPrimaryThread()) cancellation.run();
        else boundary.nextTick(cancellation);
    }

    void releaseTickets() {
        if (boundary.isPrimaryThread()) releaseNow();
        else boundary.nextTick(this::releaseNow);
    }

    private void selectCandidate() {
        requirePrimaryThread();
        if (completion.isDone()) return;
        try {
            ColdCoordinate coordinate = ColdCoordinate.forGeneration(generation);
            List<Position> candidate = positions(coordinate);
            boolean generated = false;
            for (Position position : candidate) {
                generated |= boundary.isChunkGenerated(position.x(), position.z());
            }
            if (generated) {
                generation = Math.addExact(generation, 1L);
                boundary.nextTick(this::selectCandidate);
                return;
            }
            selected = candidate;
            launchNext(coordinate);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void launchNext(ColdCoordinate coordinate) {
        requirePrimaryThread();
        if (completion.isDone()) return;
        Position position = selected.get(nextIndex);
        try {
            CompletionStage<T> loading = Objects.requireNonNull(
                    boundary.loadChunkAsync(position.x(), position.z()),
                    "Paper async chunk future");
            loading.whenComplete((chunk, failure) -> {
                if (completion.isDone()) return;
                boundary.nextTick(() -> acceptLoaded(coordinate, chunk, failure));
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void acceptLoaded(ColdCoordinate coordinate, T chunk, Throwable failure) {
        requirePrimaryThread();
        if (completion.isDone()) return;
        if (failure != null) {
            fail(failure);
            return;
        }
        try {
            T loaded = Objects.requireNonNull(chunk, "Paper async chunk result");
            ticketed.add(loaded);
            boundary.addTicket(loaded);
            nextIndex++;
            if (nextIndex < selected.size()) {
                launchNext(coordinate);
            } else {
                completion.complete(new Result<>(coordinate, ticketed, false));
            }
        } catch (Throwable ticketFailure) {
            fail(ticketFailure);
        }
    }

    private void fail(Throwable failure) {
        requirePrimaryThread();
        releaseNow();
        completion.completeExceptionally(failure);
    }

    private void releaseNow() {
        requirePrimaryThread();
        if (released) return;
        released = true;
        for (T chunk : List.copyOf(ticketed)) {
            boundary.removeTicket(chunk);
        }
        ticketed.clear();
    }

    private void requirePrimaryThread() {
        if (!boundary.isPrimaryThread()) {
            throw new IllegalStateException("cold preparation left the primary thread");
        }
    }

    private static List<Position> positions(ColdCoordinate coordinate) {
        List<Position> positions = new ArrayList<>(9);
        for (int x = coordinate.chunkX() - 1; x <= coordinate.chunkX() + 1; x++) {
            for (int z = coordinate.chunkZ() - 1; z <= coordinate.chunkZ() + 1; z++) {
                positions.add(new Position(x, z));
            }
        }
        return List.copyOf(positions);
    }

    private record Position(int x, int z) {}
}
