package ke.ric.renderer.sample;

import ke.ric.renderer.api.RenderProgress;
import ke.ric.renderer.api.RenderTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Polls public render progress on a bounded scheduler without waiting for task completion. */
final class RenderProgressPoller implements AutoCloseable {
    interface Scheduler {
        Cancellable repeat(Runnable task);
    }

    interface Cancellable {
        void cancel();
    }

    private final Scheduler scheduler;
    private final Consumer<RenderProgress> progressConsumer;
    private final Set<Observation> observations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    RenderProgressPoller(Scheduler scheduler, Consumer<RenderProgress> progressConsumer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.progressConsumer = Objects.requireNonNull(progressConsumer, "progressConsumer");
    }

    void observe(RenderTask task) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) throw new IllegalStateException("render progress poller is closed");
        Observation observation = new Observation(task);
        observation.polling = Objects.requireNonNull(
                scheduler.repeat(observation::poll), "polling task");
        observations.add(observation);
        task.completion().whenComplete((ignored, failure) -> observation.finish());
        if (closed.get()) observation.cancelOwnedTask();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Observation observation : Set.copyOf(observations)) {
            observation.cancelOwnedTask();
        }
    }

    private final class Observation {
        private final RenderTask task;
        private final AtomicBoolean finished = new AtomicBoolean();
        private Cancellable polling;
        private RenderProgress previous;

        private Observation(RenderTask task) {
            this.task = task;
        }

        private void poll() {
            if (finished.get()) return;
            RenderProgress current = Objects.requireNonNull(task.progress(), "render progress");
            if (previous != null && regresses(previous, current)) {
                cancelOwnedTask();
                throw new IllegalStateException("renderer progress regressed from "
                        + previous + " to " + current);
            }
            previous = current;
            progressConsumer.accept(current);
        }

        private void cancelOwnedTask() {
            if (!finished.get()) task.cancel();
            finish();
        }

        private void finish() {
            if (!finished.compareAndSet(false, true)) return;
            polling.cancel();
            observations.remove(this);
        }
    }

    private static boolean regresses(RenderProgress previous, RenderProgress current) {
        return current.basisPoints() < previous.basisPoints()
                || (current.basisPoints() == previous.basisPoints()
                && current.phase().ordinal() < previous.phase().ordinal());
    }
}
