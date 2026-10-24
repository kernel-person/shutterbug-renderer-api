package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Plugin-owned entry point for immutable snapshots and live camera streams.
 * Create one facade on the server thread, normally in {@code onEnable()}, and retain it for your
 * plugin's lifetime. Closing it cancels owned work and removes its temporary stream outputs;
 * owner disable also closes it automatically. Saved photographs remain in provider-owned storage.
 *
 * <p>Use {@link #snapshot(Entity)} for a one-off camera and {@link #stream(Entity)} for a tracked
 * feed. Living entities use their eye position; the source entity is excluded from its own capture.
 * The advanced {@link #session()} and {@link #client()} routes remain available for scene
 * composition, assets, attachments and custom jobs.</p>
 *
 * <pre>{@code
 * Renderer renderer = Renderer.forPlugin(plugin);
 * renderer.snapshot(player).fov(75).capture(frame -> {
 *     // Bukkit operations are safe here: consumer callbacks run on the server thread.
 *     plugin.getLogger().info("Captured " + frame.image().getWidth() + " pixels across");
 * });
 * }</pre>
 *
 * <p>Consumer overloads dispatch to the server thread and recheck lifecycle before delivery.
 * Ordinary {@link java.util.concurrent.CompletableFuture} continuations keep Java's normal threading
 * semantics. Never block the server thread waiting for a capture, render, conversion or save.
 * The fluent and creative controls require a matching development provider; older providers reject
 * unsupported optional capabilities rather than silently ignoring them.</p>
 */
public final class Renderer implements AutoCloseable {
    private final Plugin owner;
    private final Server server;
    private final RendererSession session;
    private final ThreadPoolExecutor conversions;
    private final Listener events = new Events();
    private final Object lifecycle = new Object();
    private final Map<CompletableFuture<?>, RendererService> pending = new HashMap<>();
    private final Map<CompletableFuture<?>, Set<World>> worlds = new HashMap<>();
    private final Set<World> unloadedWorlds = new HashSet<>();
    private final List<Runnable> closeHooks = new ArrayList<>();
    private volatile RendererService generation;
    private volatile boolean closed;

    private Renderer(Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        server = owner.getServer();
        session = new RendererSession(owner);
        conversions = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), command -> {
                    Thread thread = new Thread(command, "renderer-image-conversion");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        try { server.getPluginManager().registerEvents(events, owner); }
        catch (Throwable failure) { conversions.shutdownNow(); session.close(); throw failure; }
    }

    /**
     * Creates a facade for an enabled plugin. Service discovery happens when work first needs it.
     * @param owner consuming plugin, not the renderer provider
     * @return a new facade whose lifecycle belongs to the owner
     * @throws IllegalStateException if called outside the server thread
     * @throws RendererException if the owner is disabled
     * @throws NullPointerException if owner is null
     */
    public static Renderer forPlugin(Plugin owner) { return new Renderer(owner); }

    /**
     * Returns the advanced session owned by this facade. Do not close it while continuing to use the facade.
     * @return the session for explicit capture/render workflows
     * @throws RendererException if this facade is closed
     */
    public RendererSession session() { requireOpen(); return session; }

    /**
     * Discovers the current low-level client on the server thread. Do not retain the returned client
     * across provider replacement; call this method again when starting new work.
     * @return an active client for scene composition, assets, attachments and custom jobs
     * @throws IllegalStateException if called outside the server thread
     * @throws RendererException if the owner or provider is unavailable
     */
    public RendererClient client() {
        requireOpen();
        RendererClient client = session.client();
        generation = service();
        return client;
    }

    /**
     * Starts a snapshot from a defensive copy of a Bukkit location, including yaw and pitch.
     * @param camera pose in a loaded world; later changes to this Location are ignored
     * @return immutable settings with Classic 128×128, 70° vertical FOV, zero roll and radius 16
     * @throws NullPointerException if camera is null
     */
    public SnapshotBuilder snapshot(Location camera) { requireOpen(); return new SnapshotBuilder(this, camera); }

    /**
     * Starts a snapshot whose pose is read on the server thread when capture begins. A living entity
     * uses its eye pose; another entity uses its location. The source UUID is excluded automatically.
     * @param source live camera entity; a dead, invalid or offline source makes capture fail
     * @return immutable default snapshot settings
     * @throws NullPointerException if source is null
     */
    public SnapshotBuilder snapshot(Entity source) { requireOpen(); return new SnapshotBuilder(this, source); }

    /**
     * Starts a live feed from a defensive copy of a fixed camera pose.
     * @param camera pose in a loaded world; later Location mutations are ignored
     * @return immutable stream settings, targeting 20 FPS with bounded world-evidence reuse
     */
    public StreamBuilder stream(Location camera) { requireOpen(); return new StreamBuilder(this, camera); }

    /**
     * Starts a feed that follows a live entity. Living entities use their eye pose; the camera entity
     * is excluded. Death, invalidation or player logout ends the feed.
     * @param source entity whose pose is sampled on the server thread
     * @return immutable stream settings, targeting 20 FPS with bounded world-evidence reuse
     */
    public StreamBuilder stream(Entity source) { requireOpen(); return new StreamBuilder(this, source); }

    /**
     * Starts a feed that samples a location supplier on the server thread when useful work can begin.
     * Returning null ends the feed normally; throwing an exception fails it. Keep the supplier quick.
     * @param source supplier of the next camera pose in a loaded world
     * @return immutable stream settings, targeting 20 FPS with bounded world-evidence reuse
     */
    public StreamBuilder stream(Supplier<Location> source) { requireOpen(); return new StreamBuilder(this, source); }

    /**
     * Stops owned work and streams and releases listeners, the client and conversion workers.
     * This method is idempotent. It removes only owned temporary output; durable photos remain saved.
     * @throws IllegalStateException if called outside the server thread
     */
    @Override public void close() {
        if (!server.isPrimaryThread()) throw new IllegalStateException("close requires the server thread");
        List<Runnable> hooks;
        Set<CompletableFuture<?>> operations;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            hooks = List.copyOf(closeHooks);
            closeHooks.clear();
            operations = Set.copyOf(pending.keySet());
            pending.clear();
            worlds.clear();
            unloadedWorlds.clear();
        }
        for (Runnable hook : hooks) {
            try { hook.run(); }
            catch (Throwable failure) {
                if (owner.getLogger() != null) owner.getLogger().warning("Renderer cleanup failed: " + failure.getClass().getSimpleName());
            }
        }
        fail(operations, RenderFailureCode.CLIENT_CLOSED);
        conversions.shutdownNow();
        HandlerList.unregisterAll(events);
        session.close();
    }

    Plugin owner() { return owner; }
    Executor mainThreadExecutor() { requireOpen(); return session.mainThreadExecutor(); }
    Executor conversionExecutor() { return conversions; }
    RendererService service() {
        if (!server.isPrimaryThread()) throw new IllegalStateException("service discovery requires the server thread");
        return server.getServicesManager().load(RendererService.class);
    }
    RendererService generation() { return generation; }

    void requireOpen() {
        if (closed || (server.isPrimaryThread() && !owner.isEnabled())) throw unavailable(RenderFailureCode.CLIENT_CLOSED);
    }

    <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        boolean rejected;
        synchronized (lifecycle) {
            rejected = closed;
            if (!rejected) pending.put(future, generation);
        }
        future.whenComplete((value, failure) -> { synchronized (lifecycle) { pending.remove(future); worlds.remove(future); } });
        if (rejected) future.completeExceptionally(unavailable(RenderFailureCode.CLIENT_CLOSED));
        return future;
    }

    void bind(CompletableFuture<?> future, RendererService expected) {
        synchronized (lifecycle) { if (pending.containsKey(future)) pending.put(future, expected); }
    }

    void bindWorlds(CompletableFuture<?> future, java.util.Collection<World> affected) {
        if (!server.isPrimaryThread()) throw new IllegalStateException("world binding requires the server thread");
        Set<World> copied = Set.copyOf(affected);
        boolean unavailable;
        synchronized (lifecycle) {
            unavailable = copied.stream().anyMatch(unloadedWorlds::contains);
            if (!unavailable && pending.containsKey(future)) worlds.put(future, copied);
        }
        if (unavailable) future.completeExceptionally(worldUnavailable());
    }

    // Progress may outlive a successful public future by one tick, after its tracking entry is gone.
    boolean worldAvailable(World world) {
        synchronized (lifecycle) { return !closed && !unloadedWorlds.contains(world); }
    }

    private static RendererException worldUnavailable() {
        return new RendererException(RenderFailureCode.INVALID_REQUEST,
                "camera or output world unloaded; start a new operation after the world is loaded");
    }

    void onClose(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        synchronized (lifecycle) { requireOpen(); closeHooks.add(hook); }
    }

    void requireGeneration(RendererService expected) {
        requireOpen();
        if (expected == null || expected != service()) throw unavailable(RenderFailureCode.PROVIDER_DISABLED);
        RendererClient client = session.client();
        if (!client.active()) throw unavailable(RenderFailureCode.PROVIDER_DISABLED);
    }

    void checkWorkerGeneration(RendererService expected) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (closed) throw unavailable(RenderFailureCode.CLIENT_CLOSED);
        if (expected == null || expected != generation) throw unavailable(RenderFailureCode.PROVIDER_DISABLED);
    }

    <T> void dispatch(CompletableFuture<T> output, Runnable action) {
        if (output.isDone()) return;
        try {
            mainThreadExecutor().execute(() -> {
                if (output.isDone()) return;
                try { requireOpen(); action.run(); }
                catch (Throwable failure) { output.completeExceptionally(failure); }
            });
        } catch (Throwable failure) { output.completeExceptionally(failure); }
    }

    <T> CompletableFuture<T> convert(RendererService expected, Callable<T> action) {
        CompletableFuture<T> output = track(new CompletableFuture<>());
        bind(output, expected);
        if (output.isDone()) return output;
        FutureTask<Void> task = new FutureTask<>(() -> {
            if (output.isDone()) return null;
            try {
                checkWorkerGeneration(expected);
                T value = action.call();
                checkWorkerGeneration(expected);
                output.complete(value);
            } catch (Throwable failure) { output.completeExceptionally(failure); }
            return null;
        });
        output.whenComplete((value, failure) -> {
            if (failure != null) { task.cancel(true); conversions.remove(task); }
        });
        try { if (!output.isDone()) conversions.execute(task); }
        catch (RejectedExecutionException failure) {
            output.completeExceptionally(closed ? unavailable(RenderFailureCode.CLIENT_CLOSED)
                    : new RendererException(RenderFailureCode.QUEUE_FULL, "renderer conversion queue is full", failure));
        }
        return output;
    }

    <T> CompletableFuture<T> consumer(CompletableFuture<T> source, Consumer<? super T> success,
                                       Consumer<? super Throwable> error) {
        Objects.requireNonNull(success, "success");
        // Track a lifecycle signal separately: invalidation must become an observable callback,
        // not settle the consumer future before its main-thread delivery can run.
        CompletableFuture<T> output = new CompletableFuture<>();
        CompletableFuture<Void> lifetime = track(new CompletableFuture<>());
        java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
        follow(output, source);
        output.whenComplete((value, failure) -> lifetime.complete(null));
        class Delivery {
            void schedule(T value, Throwable failure, RendererService expected) {
                dispatch(output, () -> {
                    Throwable actual = failure == null ? null : unwrap(failure);
                    if (lifetime.isCompletedExceptionally()) {
                        try { lifetime.join(); }
                        catch (Throwable lost) { actual = unwrap(lost); }
                    }
                    if (actual == null) {
                        try { requireGeneration(expected); }
                        catch (Throwable stale) { actual = unwrap(stale); }
                    }
                    if (!delivered.compareAndSet(false, true)) return;
                    if (actual != null) {
                        if (error != null) error.accept(actual);
                        output.completeExceptionally(actual);
                    } else {
                        success.accept(value);
                        requireOpen();
                        output.complete(value);
                    }
                });
            }
        }
        Delivery delivery = new Delivery();
        lifetime.whenComplete((ignored, failure) -> {
            if (failure != null) delivery.schedule(null, failure, null);
        });
        source.whenComplete((value, failure) -> {
            // Capture identity now, rather than during queued delivery, so a replacement
            // provider cannot turn a completed old-provider result into a fresh success.
            RendererService expected = generation;
            bind(lifetime, expected);
            delivery.schedule(value, failure, expected);
        });
        return output;
    }

    static void follow(CompletableFuture<?> output, CompletableFuture<?> source) {
        output.whenComplete((value, failure) -> { if (failure != null) source.cancel(true); });
    }

    static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }

    private void invalidate(RendererService invalidated) {
        Set<CompletableFuture<?>> operations;
        synchronized (lifecycle) {
            operations = new HashSet<>();
            pending.forEach((future, expected) -> { if (expected == null || expected == invalidated) operations.add(future); });
            operations.forEach(pending::remove);
            if (generation == invalidated) generation = null;
        }
        fail(operations, RenderFailureCode.PROVIDER_DISABLED);
    }

    private static void fail(Set<CompletableFuture<?>> operations, RenderFailureCode code) {
        for (CompletableFuture<?> operation : operations) operation.completeExceptionally(unavailable(code));
    }

    static RendererException unavailable(RenderFailureCode code) { return new RendererException(code, "renderer: " + code); }

    private final class Events implements Listener {
        @EventHandler private void onDisable(PluginDisableEvent event) { if (event.getPlugin() == owner) close(); }
        @EventHandler private void onUnregister(ServiceUnregisterEvent event) {
            if (event.getProvider().getProvider() instanceof RendererService invalidated) invalidate(invalidated);
        }
        @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
        private void onWorldUnload(WorldUnloadEvent event) {
            if (event.isCancelled()) return;
            List<CompletableFuture<?>> affected;
            synchronized (lifecycle) {
                unloadedWorlds.add(event.getWorld());
                affected = worlds.entrySet().stream().filter(entry -> entry.getValue().contains(event.getWorld()))
                        .map(Map.Entry::getKey).toList();
            }
            for (CompletableFuture<?> future : affected) future.completeExceptionally(worldUnavailable());
        }
    }
}
