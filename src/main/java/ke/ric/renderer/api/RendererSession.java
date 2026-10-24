package ke.ric.renderer.api;

import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Optional plugin-owned capture/render lifecycle. Construct, request, discover clients and close
 * on the server thread. Cancellation of a returned future is safe from any thread. There are no
 * retries, gameplay cooldowns or persistence; low-level multi-pass workflows can use {@link #client()}.
 * This helper is supplied with the development API preview and should be tested with its matching
 * development provider; its presence does not establish support in an older production installation.
 */
public final class RendererSession implements AutoCloseable {
    private final Plugin owner;
    private final Server server;
    private final Set<Operation> operations = ConcurrentHashMap.newKeySet();
    private final ScheduledThreadPoolExecutor deadlines;
    private final Listener events = new Events();
    private volatile boolean closed;
    private RendererService service;
    private RendererClient client;

    /** Registers lifecycle listeners for this enabled owner on the server thread; close during owner disable. */
    public RendererSession(Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        server = owner.getServer();
        requireMainThread();
        if (!owner.isEnabled()) throw unavailable(RenderFailureCode.CLIENT_CLOSED);
        deadlines = new ScheduledThreadPoolExecutor(1, command -> {
            Thread thread = new Thread(command, "renderer-session-deadline");
            thread.setDaemon(true);
            return thread;
        });
        deadlines.setRemoveOnCancelPolicy(true);
        server.getPluginManager().registerEvents(events, owner);
    }

    /** Discovers/reacquires the current service's client; service absence is a typed failure. */
    public RendererClient client() {
        requireMainThread();
        requireOpen();
        RendererService current = server.getServicesManager().load(RendererService.class);
        if (service != current || (client != null && !client.active())) invalidate();
        // Invalidation completes consumer futures inline; their callbacks may close this session.
        requireOpen();
        if (current == null) throw unavailable(RenderFailureCode.PROVIDER_DISABLED);
        if (client == null) {
            RendererClient acquired = current.createClient(owner);
            service = current;
            client = acquired;
        }
        return client;
    }

    /**
     * Captures and renders under one wall-clock deadline from the request's settings. Successful
     * results are published on the server thread after a final lifecycle check. Synchronous provider
     * and scheduling failures settle the returned future too; cancellation releases owned work.
     */
    public CompletableFuture<RenderResult> captureAndRender(CaptureRequest request) {
        return captureAndRender(request, 70, List.of());
    }

    /** Captures with an explicit field of view in degrees under the same lifecycle and deadline. */
    public CompletableFuture<RenderResult> captureAndRender(CaptureRequest request, double fovDegrees) {
        return captureAndRender(request, fovDegrees, List.of());
    }

    /** Captures, appends complete exact entities, then renders under the same deadline and lifecycle.
     * The entity list is copied before capture; empty lists also work with older providers. */
    public CompletableFuture<RenderResult> captureAndRender(CaptureRequest request, List<SceneEntity> entities) {
        return captureAndRender(request, 70, entities);
    }

    /** Captures a custom field of view, appends exact entities and renders with one deadline. */
    public CompletableFuture<RenderResult> captureAndRender(CaptureRequest request, double fovDegrees,
                                                          List<SceneEntity> entities) {
        requireMainThread();
        List<SceneEntity> copied = List.copyOf(entities);
        Objects.requireNonNull(request, "request");
        Operation operation = new Operation(request);
        operation.result.whenComplete((frame, failure) -> operation.release(failure != null));
        try {
            operation.client = client();
            operation.service = service;
            operations.add(operation);
            operation.timer = deadlines.schedule(() -> operation.fail(unavailable(RenderFailureCode.TIMEOUT)),
                    operation.remaining(), TimeUnit.NANOSECONDS);
            operation.cancelIfFinished();
            if (!operation.result.isDone()) {
                CompletionStage<Scene> capture = operation.client.capture(request, fovDegrees);
                operation.capture = capture.toCompletableFuture();
                operation.cancelIfFinished();
                capture.whenComplete((scene, failure) -> dispatch(operation, () -> {
                    if (failure != null) { operation.fail(failure); return; }
                    Scene composed = copied.isEmpty() ? scene : operation.client.appendEntities(scene, copied);
                    if (operation.result.isDone()) return;
                    RenderSettings settings = request.settings().withTimeout(Duration.ofNanos(operation.remaining()));
                    operation.task = operation.client.submit(new RenderJob(1, composed, settings));
                    operation.cancelIfFinished();
                    if (!operation.result.isDone()) operation.task.completion().whenComplete((frame, renderFailure) ->
                            dispatch(operation, () -> {
                                if (renderFailure == null) operation.result.complete(
                                        frame.withWorldCapturedNanos(composed.worldCapturedNanos()));
                                else operation.fail(renderFailure);
                            }));
                }));
            }
        } catch (Throwable failure) {
            operation.fail(failure);
        }
        return operation.result;
    }

    /**
     * Explicit Bukkit dispatcher, usable from any thread. Scheduling rejection is propagated to
     * the caller, not swallowed. Accepted commands are not revoked by provider replacement; callers
     * must check their destination's liveness. Bukkit provides no callback guarantee after disable.
     */
    public Executor mainThreadExecutor() {
        return command -> {
            Objects.requireNonNull(command, "command");
            if (closed) throw new RejectedExecutionException("renderer session is closed");
            if (server.isPrimaryThread()) command.run();
            else server.getScheduler().runTask(owner, command);
        };
    }

    /** Settles all owned work, closes the client and detaches listeners/timers. Idempotent. */
    @Override public void close() {
        requireMainThread();
        if (closed) return;
        closed = true;
        for (Operation operation : operations) operation.fail(unavailable(RenderFailureCode.CLIENT_CLOSED));
        deadlines.shutdownNow();
        HandlerList.unregisterAll(events);
        invalidate();
    }

    private final class Events implements Listener {
        @EventHandler private void onDisable(PluginDisableEvent event) {
            if (event.getPlugin() == owner) close();
        }

        @EventHandler private void onUnregister(ServiceUnregisterEvent event) {
            if (event.getProvider().getProvider() == service) invalidate();
        }
    }

    private void invalidate() {
        RendererClient old = client;
        Set<Operation> invalidated = Set.copyOf(operations);
        operations.clear();
        service = null;
        client = null;
        // Completion callbacks may bind a new client and add work; only fail the detached old set.
        for (Operation operation : invalidated) operation.fail(unavailable(RenderFailureCode.PROVIDER_DISABLED));
        if (old != null) old.close();
    }

    private void dispatch(Operation operation, Runnable command) {
        if (operation.result.isDone()) return;
        try {
            mainThreadExecutor().execute(() -> {
                if (operation.result.isDone()) return;
                try {
                    requireOpen();
                    if (operation.service != server.getServicesManager().load(RendererService.class)
                            || operation.client != client || !operation.client.active()) {
                        throw unavailable(RenderFailureCode.PROVIDER_DISABLED);
                    }
                    operation.remaining();
                    command.run();
                } catch (Throwable failure) { operation.fail(failure); }
            });
        } catch (Throwable failure) { operation.fail(failure); }
    }

    private void requireMainThread() {
        if (!server.isPrimaryThread()) throw new IllegalStateException("operation requires the server thread");
    }

    private void requireOpen() {
        if (closed || !owner.isEnabled()) throw unavailable(RenderFailureCode.CLIENT_CLOSED);
    }

    private static RendererException unavailable(RenderFailureCode code) {
        return new RendererException(code, "renderer session: " + code);
    }

    private final class Operation {
        final CompletableFuture<RenderResult> result = new CompletableFuture<>();
        final long started = System.nanoTime();
        final long timeout;
        RendererClient client;
        RendererService service;
        volatile CompletableFuture<Scene> capture;
        volatile RenderTask task;
        volatile ScheduledFuture<?> timer;

        Operation(CaptureRequest request) { timeout = request.settings().timeout().toNanos(); }

        long remaining() {
            long remaining = timeout - (System.nanoTime() - started);
            if (remaining <= 0) throw unavailable(RenderFailureCode.TIMEOUT);
            return remaining;
        }

        void fail(Throwable failure) { result.completeExceptionally(failure); }

        void cancelIfFinished() { if (result.isDone()) release(true); }

        void release(boolean cancel) {
            operations.remove(this);
            ScheduledFuture<?> deadline = timer;
            if (deadline != null) deadline.cancel(false);
            if (cancel) {
                CompletableFuture<Scene> captured = capture;
                if (captured != null) captured.cancel(true);
                RenderTask rendering = task;
                if (rendering != null) rendering.cancel();
            }
        }
    }
}
