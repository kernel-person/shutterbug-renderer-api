package ke.ric.renderer.sample;

import ke.ric.renderer.api.RenderProgress;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderStatus;
import ke.ric.renderer.api.RenderTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderProgressPollerTest {
    @Test
    void observesActualProgressMonotonicallyAndStopsWhenCompletionFinishes() {
        FakeScheduler scheduler = new FakeScheduler();
        List<RenderProgress> observed = new ArrayList<>();
        RenderProgressPoller poller = new RenderProgressPoller(scheduler, observed::add);
        FakeTask task = new FakeTask();

        poller.observe(task);
        assertFalse(task.completion.isDone(), "observation must not wait for completion");

        task.progress = new RenderProgress(RenderProgress.Phase.QUEUED, 0);
        scheduler.tick();
        task.progress = new RenderProgress(RenderProgress.Phase.PREPARING, 200);
        scheduler.tick();
        task.progress = new RenderProgress(RenderProgress.Phase.RENDERING, 200);
        scheduler.tick();
        task.progress = new RenderProgress(RenderProgress.Phase.RENDERING, 6_000);
        scheduler.tick();

        assertEquals(List.of(
                new RenderProgress(RenderProgress.Phase.QUEUED, 0),
                new RenderProgress(RenderProgress.Phase.PREPARING, 200),
                new RenderProgress(RenderProgress.Phase.RENDERING, 200),
                new RenderProgress(RenderProgress.Phase.RENDERING, 6_000)), observed);
        assertEquals(4, task.progressCalls);

        task.completion.complete(null);
        assertTrue(scheduler.handle.cancelled);
        int callsAtCompletion = task.progressCalls;
        scheduler.tick();
        assertEquals(callsAtCompletion, task.progressCalls);
    }

    @Test
    void regressionIsRejectedAndCloseCancelsOwnedWorkIdempotently() {
        FakeScheduler scheduler = new FakeScheduler();
        RenderProgressPoller poller = new RenderProgressPoller(scheduler, ignored -> {});
        FakeTask task = new FakeTask();
        poller.observe(task);

        task.progress = new RenderProgress(RenderProgress.Phase.RENDERING, 5_000);
        scheduler.tick();
        task.progress = new RenderProgress(RenderProgress.Phase.DECODING, 4_999);
        assertThrows(IllegalStateException.class, scheduler::tick);

        poller.close();
        poller.close();
        assertEquals(1, task.cancelCalls);
        assertTrue(scheduler.handle.cancelled);
    }

    private static final class FakeScheduler implements RenderProgressPoller.Scheduler {
        private Runnable poll;
        private FakeHandle handle;

        @Override
        public RenderProgressPoller.Cancellable repeat(Runnable task) {
            poll = task;
            handle = new FakeHandle();
            return handle;
        }

        void tick() {
            if (!handle.cancelled) poll.run();
        }
    }

    private static final class FakeHandle implements RenderProgressPoller.Cancellable {
        boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class FakeTask implements RenderTask {
        private final CompletableFuture<RenderResult> completion = new CompletableFuture<>();
        private RenderProgress progress = new RenderProgress(RenderProgress.Phase.QUEUED, 0);
        private int progressCalls;
        private int cancelCalls;

        @Override public UUID id() { return new UUID(0, 1); }
        @Override public RenderStatus status() { return RenderStatus.QUEUED; }
        @Override public RenderProgress progress() { progressCalls++; return progress; }
        @Override public boolean cancel() { cancelCalls++; return completion.completeExceptionally(
                new IllegalStateException("cancelled")); }
        @Override public CompletionStage<RenderResult> completion() { return completion; }
    }
}
