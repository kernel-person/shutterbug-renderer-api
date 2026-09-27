package ke.ric.renderer.sample;

import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.RendererService;
import ke.ric.renderer.api.Scene;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** API-linked runtime loaded only after the API-free plugin shell proves the API is present. */
final class RendererSampleApiRuntime implements RendererSampleRuntime, Listener {
    private static final String ENTITY_NAME = "renderer-sample-canary";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final AtomicBoolean gateRunning = new AtomicBoolean();
    private RendererClient client;
    private RendererSampleLifecycle lifecycle;
    private RenderProgressPoller progressPoller;
    private RendererSampleWorkflow workflow;
    private Scene liveScene;
    private RenderSettings liveSettings;
    private String liveHash;
    private Entity canaryEntity;
    private volatile String gateStage = "idle";
    private volatile ColdRegionPreparer<Chunk> activePreparation;
    private volatile CompletableFuture<PreparedScene> activeColdScene;
    private final JavaPlugin plugin;

    private RendererSampleApiRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    static RendererSampleRuntime enable(JavaPlugin plugin) {
        RendererSampleApiRuntime runtime = new RendererSampleApiRuntime(plugin);
        try {
            runtime.enable();
            return runtime;
        } catch (RuntimeException | Error failure) {
            try {
                runtime.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void enable() {
        Objects.requireNonNull(plugin.getCommand("renderer-sample"), "renderer-sample command")
                .setExecutor(plugin);
        progressPoller = new RenderProgressPoller(task -> {
            var scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
            return scheduled::cancel;
        }, progress -> plugin.getLogger().fine("RENDERER_SAMPLE_PROGRESS phase="
                + progress.phase() + " basisPoints=" + progress.basisPoints()));
        lifecycle = new RendererSampleLifecycle(
                () -> Bukkit.getServicesManager().load(RendererService.class),
                service -> service.createClient(plugin),
                message -> plugin.getLogger().warning("RENDERER_SAMPLE_UNAVAILABLE " + message),
                capabilities -> plugin.getLogger().info("RENDERER_SAMPLE_AVAILABLE version="
                        + capabilities.rendererVersion() + " platform=" + capabilities.platform()
                        + " production=" + capabilities.packagedForProduction()));
        if (activateAvailableService()) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Close the small registration race between the first lookup and listener registration.
        if (activateAvailableService()) HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == RendererService.class
                && activateAvailableService()) {
            HandlerList.unregisterAll(this);
        }
    }

    private boolean activateAvailableService() {
        if (workflow != null) return true;
        client = lifecycle.activate();
        if (client == null) return false;
        Executor conversionExecutor = command ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
        workflow = new RendererSampleWorkflow(client, progressPoller, conversionExecutor);
        workflow.capabilities().messages().forEach(message ->
                plugin.getLogger().warning("RENDERER_SAMPLE_CAPABILITY " + message));
        if (!workflow.capabilities().coldCaptureAvailable()) {
            plugin.getLogger().warning(
                    "RENDERER_SAMPLE_UNAVAILABLE NORMAL cold workflow skipped; sample remains enabled");
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> runCanary("startup", null));
        return true;
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        cancelColdPreparation();
        if (progressPoller != null) progressPoller.close();
        progressPoller = null;
        if (lifecycle != null) lifecycle.close();
        lifecycle = null;
        client = null;
        workflow = null;
        liveSettings = null;
        if (canaryEntity != null && canaryEntity.isValid()) canaryEntity.remove();
        canaryEntity = null;
        gateRunning.set(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"renderer-sample".equalsIgnoreCase(command.getName()) || args.length != 1) {
            return false;
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("renderer sample lifecycle command left main thread");
        }
        try {
            return switch (args[0]) {
                case "disable-shutterbug" -> disableShutterBugAndRender();
                case "disable-provider" -> disableProvider();
                default -> false;
            };
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("RENDERER_SAMPLE_LIFECYCLE_FAILED type="
                    + failure.getClass().getSimpleName());
            return true;
        }
    }

    private boolean disableShutterBugAndRender() {
        Plugin shutterBug = requiredPlugin("ShutterBug");
        Bukkit.getPluginManager().disablePlugin(shutterBug);
        if (shutterBug.isEnabled() || client == null || !client.active() || liveScene == null) {
            throw new IllegalStateException("ShutterBug did not disable independently");
        }
        RenderTask lifecycleTask = client.submit(new RenderJob(1, liveScene, liveSettings));
        progressPoller.observe(lifecycleTask);
        CompletionStage<RenderResult> render = lifecycleTask.completion();
        render.toCompletableFuture().orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        lifecycleFailure(failure);
                        return;
                    }
                    LiveGateThreading.requireAsyncCompletion(Bukkit.isPrimaryThread());
                    if (!liveHash.equals(LiveGateEvidence.verify(
                            result, result, ENTITY_NAME, liveSettings.attachments()))) {
                        lifecycleFailure(new IllegalStateException(
                                "provider output changed while ShutterBug was disabled"));
                        return;
                    }
                    if (shutterBug.isEnabled() || !client.active()) {
                        lifecycleFailure(new IllegalStateException(
                                "ShutterBug did not remain independently disabled"));
                        return;
                    }
                    plugin.getLogger().info(
                            "RENDERER_SAMPLE_SHUTTERBUG_DISABLED_OK providerActive=true hash="
                                    + liveHash);
                });
        return true;
    }

    private boolean disableProvider() {
        Plugin provider = requiredPlugin("ShutterBugRenderer");
        Plugin shutterBug = requiredPlugin("ShutterBug");
        RendererClient previous = client;
        Bukkit.getPluginManager().disablePlugin(provider);
        if (provider.isEnabled() || !shutterBug.isEnabled()
                || previous == null || previous.active()) {
            throw new IllegalStateException("provider did not disable independently");
        }
        if (lifecycle != null) lifecycle.activate();
        client = null;
        workflow = null;
        plugin.getLogger().info(
                "RENDERER_SAMPLE_PROVIDER_DISABLED_OK shutterbugActive=true clientClosed=true");
        return true;
    }

    private void runCanary(String generation, Runnable success) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("renderer capture setup must run on the main thread");
        }
        if (!gateRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("renderer sample gate is already running");
        }
        long preparationStarted = System.nanoTime();
        gateStage = "prepare";
        try {
            if (client == null || workflow == null || !client.active()) {
                throw new IllegalStateException("renderer activation is unavailable");
            }
            prepareColdScene(preparationStarted).toCompletableFuture()
                    .orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .thenCompose(prepared -> {
                gateStage = "capture-normal";
                long captureStarted = System.nanoTime();
                return workflow.captureCold128(prepared.camera())
                        .thenApply(converted -> {
                            prepared.preparation().releaseTickets();
                            return new Captured(converted, prepared,
                                    System.nanoTime() - captureStarted);
                        });
            }).thenCompose(captured -> {
                RendererSampleWorkflow.ConversionEvidence converted = captured.converted();
                Scene scene = converted.scene();
                liveScene = scene;
                liveSettings = converted.settings();
                removeCanaryEntity();
                gateStage = "second-render";
                RenderTask second = client.submit(new RenderJob(1, scene, converted.settings()));
                progressPoller.observe(second);
                return second.completion().thenApply(secondResult ->
                        new Evidence(converted.frame(), secondResult, captured.captureNanos(),
                                captured.prepared().preparationNanos(), scene,
                                converted.settings(), converted.png().length,
                                converted.mapColors().length,
                                captured.prepared().coordinate(),
                                captured.prepared().coldEvidence()));
            }).thenCompose(evidence -> {
                gateStage = "cancellation";
                return workflow.proveCancellation(evidence.scene()).thenApply(ignored -> evidence);
            }).thenCompose(evidence -> {
                gateStage = "tiny-timeout";
                return workflow.proveTinyTimeout(evidence.scene()).thenApply(ignored -> evidence);
            }).thenCompose(evidence -> {
                if (!workflow.capabilities().manualExtremeAvailable()) {
                    return CompletableFuture.completedFuture(
                            new CompletedEvidence(evidence, null));
                }
                gateStage = "manual-extreme";
                return workflow.renderManualExtreme().thenApply(manual ->
                        new CompletedEvidence(evidence, manual));
            })
                    .toCompletableFuture().orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .whenComplete((completed, failure) -> {
                        gateRunning.set(false);
                        if (failure != null) {
                            liveFailure(generation, failure);
                            return;
                        }
                        try {
                            Evidence evidence = completed.live();
                            LiveGateThreading.requireAsyncCompletion(Bukkit.isPrimaryThread());
                            liveHash = LiveGateEvidence.verify(
                                    evidence.first(), evidence.second(), ENTITY_NAME,
                                    evidence.settings().attachments());
                            long firstMs = nanosToMillis(evidence.first().timings().totalNanos());
                            long secondMs = nanosToMillis(evidence.second().timings().totalNanos());
                            String manual = completed.manual() == null ? "skipped"
                                    : completed.manual().settings().profile().name();
                            plugin.getLogger().info("RENDERER_SAMPLE_LIVE_OK " + evidence.coldEvidence()
                                    + " generation=" + generation
                                    + " hash=" + liveHash
                                    + " coldGeneration=" + evidence.coordinate().generation()
                                    + " coldX=" + evidence.coordinate().blockX()
                                    + " coldZ=" + evidence.coordinate().blockZ()
                                    + " prepMs=" + nanosToMillis(evidence.preparationNanos())
                                    + " captureMs=" + nanosToMillis(evidence.captureNanos())
                                    + " firstTotalMs=" + firstMs
                                    + " secondTotalMs=" + secondMs
                                    + " completionThread=async profile="
                                    + evidence.settings().profile()
                                    + " pngBytes=" + evidence.pngBytes()
                                    + " mapColors=" + evidence.mapColors()
                                    + " manual=" + manual
                                    + " entity=" + ENTITY_NAME
                                    + " cancelled=true timeout=true");
                            if (success != null) success.run();
                        } catch (RuntimeException verificationFailure) {
                            liveFailure(generation, verificationFailure);
                        }
                    });
        } catch (RuntimeException failure) {
            gateRunning.set(false);
            removeCanaryEntity();
            liveFailure(generation, failure);
        }
    }

    private CompletionStage<PreparedScene> prepareColdScene(long startedNanos) {
        World world = Bukkit.getWorlds().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("server has no world"));
        CompletableFuture<PreparedScene> result = new CompletableFuture<>();
        activeColdScene = result;
        Executor asynchronous = command ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
        ColdGenerationStore store = new ColdGenerationStore(
                plugin.getDataFolder().toPath(), asynchronous);
        store.readNextCandidate().whenComplete((initialGeneration, readFailure) -> {
            if (result.isDone()) return;
            if (readFailure != null) {
                result.completeExceptionally(readFailure);
                return;
            }
            scheduleMain(result, () -> startColdRegion(world, store, initialGeneration,
                    startedNanos, result));
        });
        return result;
    }

    private void startColdRegion(World world, ColdGenerationStore store,
                                 long initialGeneration, long startedNanos,
                                 CompletableFuture<PreparedScene> result) {
        if (result.isDone()) return;
        ColdRegionPreparer<Chunk> preparation = new ColdRegionPreparer<>(
                chunkBoundary(world), initialGeneration);
        activePreparation = preparation;
        preparation.start().whenComplete((region, loadFailure) -> {
            if (loadFailure != null) {
                result.completeExceptionally(loadFailure);
                return;
            }
            store.persist(region.coordinate().generation()).whenComplete(
                    (ignored, persistFailure) -> {
                        if (result.isDone()) return;
                        if (persistFailure != null) {
                            result.completeExceptionally(persistFailure);
                            preparation.cancel();
                            return;
                        }
                        scheduleMain(result, () -> finishColdScene(
                                world, preparation, region, startedNanos, result));
                    });
        });
    }

    private ColdRegionPreparer.Boundary<Chunk> chunkBoundary(World world) {
        return new ColdRegionPreparer.Boundary<>() {
            @Override
            public boolean isPrimaryThread() {
                return Bukkit.isPrimaryThread();
            }

            @Override
            public boolean isChunkGenerated(int chunkX, int chunkZ) {
                return world.isChunkGenerated(chunkX, chunkZ);
            }

            @Override
            public CompletionStage<Chunk> loadChunkAsync(int chunkX, int chunkZ) {
                return PaperAsyncChunkLoader.load(world, chunkX, chunkZ);
            }

            @Override
            public void addTicket(Chunk chunk) {
                chunk.addPluginChunkTicket(plugin);
            }

            @Override
            public void removeTicket(Chunk chunk) {
                chunk.removePluginChunkTicket(plugin);
            }

            @Override
            public void nextTick(Runnable task) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        };
    }

    private void finishColdScene(World world, ColdRegionPreparer<Chunk> preparation,
                                 ColdRegionPreparer.Result<Chunk> region,
                                 long startedNanos,
                                 CompletableFuture<PreparedScene> result) {
        if (result.isDone()) return;
        ColdCoordinate coordinate = region.coordinate();
        try {
            double y = Math.min(world.getMaxHeight() - 10.0,
                    Math.max(world.getMinHeight() + 20.0,
                            world.getHighestBlockYAt(
                                    coordinate.blockX(), coordinate.blockZ()) + 20.0));
            Location entityLocation = new Location(world,
                    coordinate.blockX() + 0.5, y, coordinate.blockZ() + 0.5,
                    180.0f, 0.0f);
            ArmorStand stand = world.spawn(entityLocation, ArmorStand.class, value -> {
                value.setGravity(false);
                value.setInvulnerable(true);
                value.setSilent(true);
                value.setArms(true);
                value.setBasePlate(true);
                value.setMarker(false);
                value.setCustomName(ENTITY_NAME);
                value.setCustomNameVisible(false);
            });
            canaryEntity = stand;
            Location camera = new Location(world, entityLocation.getX(),
                    entityLocation.getY() + 1.0, coordinate.cameraBlockZ() + 0.5,
                    180.0f, 0.0f);
            result.complete(new PreparedScene(camera, coordinate, region.coldEvidence(),
                    System.nanoTime() - startedNanos, preparation));
        } catch (Throwable failure) {
            preparation.cancel();
            result.completeExceptionally(failure);
        }
    }

    private void scheduleMain(CompletableFuture<?> result, Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!result.isDone()) task.run();
            });
        } catch (RuntimeException schedulingFailure) {
            result.completeExceptionally(schedulingFailure);
        }
    }

    private void removeCanaryEntity() {
        Entity entity = canaryEntity;
        canaryEntity = null;
        if (entity == null) return;
        if (Bukkit.isPrimaryThread()) {
            if (entity.isValid()) entity.remove();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (entity.isValid()) entity.remove();
            });
        }
    }

    private Plugin requiredPlugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null) throw new IllegalStateException(name + " is unavailable");
        return plugin;
    }

    private void liveFailure(String generation, Throwable failure) {
        removeCanaryEntity();
        cancelColdPreparation();
        Throwable cause = unwrap(failure);
        plugin.getLogger().severe("RENDERER_SAMPLE_LIVE_FAILED generation=" + generation
                + " stage=" + gateStage + " type="
                + cause.getClass().getSimpleName()
                + " code=" + failureCode(failure));
    }

    private void lifecycleFailure(Throwable failure) {
        plugin.getLogger().severe("RENDERER_SAMPLE_LIFECYCLE_FAILED type="
                + unwrap(failure).getClass().getSimpleName());
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static String failureCode(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof RendererException rendererFailure
                ? rendererFailure.code().name() : "NONE";
    }

    private void cancelColdPreparation() {
        CompletableFuture<PreparedScene> coldScene = activeColdScene;
        activeColdScene = null;
        if (coldScene != null) {
            coldScene.completeExceptionally(
                    new IllegalStateException("renderer sample disabled during preparation"));
        }
        ColdRegionPreparer<Chunk> preparation = activePreparation;
        activePreparation = null;
        if (preparation != null) preparation.cancel();
    }

    private static long nanosToMillis(long nanos) {
        return nanos < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private record PreparedScene(Location camera, ColdCoordinate coordinate,
                                 String coldEvidence, long preparationNanos,
                                 ColdRegionPreparer<Chunk> preparation) {}
    private record Captured(RendererSampleWorkflow.ConversionEvidence converted,
                            PreparedScene prepared, long captureNanos) {}
    private record Evidence(RenderResult first, RenderResult second,
                            long captureNanos, long preparationNanos, Scene scene,
                            RenderSettings settings, int pngBytes, int mapColors,
                            ColdCoordinate coordinate, String coldEvidence) {}
    private record CompletedEvidence(Evidence live,
                                     RendererSampleWorkflow.ConversionEvidence manual) {}
}
