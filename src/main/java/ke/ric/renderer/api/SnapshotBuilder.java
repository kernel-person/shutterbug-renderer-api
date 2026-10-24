package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Immutable settings for one snapshot. Each modifier returns a new builder; retain or chain its
 * return value. A builder may be reused to request multiple captures. Entity state is sampled on
 * the server thread when each capture begins, not when the builder is configured.
 *
 * <p>Defaults are Classic 128×128, 70° vertical FOV, zero roll, a 16-block radius and at most two
 * captured chunks per tick. Source entities are excluded; living cameras use their eye pose.
 * Required chunks must already be loaded. Nothing in this API silently loads missing scenery.</p>
 *
 * <p>{@link #capture()} yields pixels and evidence without allocating maps. {@link #captureMap()}
 * requests a durable, automatically saved photo item. Callback overloads run on the server thread;
 * ordinary future continuations have normal Java threading semantics. Cancellation propagates to
 * owned capture/render/output work. Never block the server thread waiting for a future.</p>
 *
 * <pre>{@code
 * renderer.snapshot(player)
 *         .fov(75).roll(5)
 *         .environment(RenderEnvironment.captured().timeOfDay(6000))
 *         .excluding(nearbyEntities)
 *         .capture(frame -> plugin.getLogger().info("Camera: " + frame.effectiveCamera()));
 * }</pre>
 */
public final class SnapshotBuilder {
    private final FrameOptions options;
    private final Renderer renderer;
    private final Location camera;
    private final Entity source;
    private final RenderSettings settings;
    private final double fov, radius;
    private final int chunksPerTick;
    private final Set<UUID> excludedIds;
    private final List<Entity> excludedEntities;

    SnapshotBuilder(Renderer renderer, Location camera) {
        this(renderer, Objects.requireNonNull(camera, "camera"), null, RenderSettings.defaults(), 70, 16, 2, Set.of(), List.of(), FrameOptions.defaults());
    }

    SnapshotBuilder(Renderer renderer, Entity source) {
        this(renderer, null, Objects.requireNonNull(source, "source"), RenderSettings.defaults(), 70, 16, 2, Set.of(), List.of(), FrameOptions.defaults());
    }

    private SnapshotBuilder(Renderer renderer, Location camera, Entity source, RenderSettings settings,
                            double fov, double radius, int chunksPerTick, Set<UUID> excludedIds, List<Entity> excludedEntities, FrameOptions options) {
        this.options=options;
        this.renderer = renderer;
        this.camera = camera == null ? null : camera.clone();
        this.source = source;
        this.settings = Objects.requireNonNull(settings, "settings");
        if (!Double.isFinite(fov) || fov <= 0 || fov >= 180) throw new IllegalArgumentException("vertical FOV must be in (0, 180) degrees");
        if (!Double.isFinite(radius) || radius <= 0 || chunksPerTick <= 0) throw new IllegalArgumentException("radius and chunk budget must be positive");
        this.fov = fov; this.radius = radius; this.chunksPerTick = chunksPerTick;
        this.excludedIds = Set.copyOf(excludedIds);
        this.excludedEntities = List.copyOf(excludedEntities);
    }

    /**
     * Sets explicit render dimensions. The immutable original builder is unchanged.
     * @param width positive output width in pixels
     * @param height positive output height in pixels
     * @throws IllegalArgumentException for invalid dimensions
     * @return a new builder with this setting
     */
    public SnapshotBuilder resolution(int width, int height) { return settings(settings.withResolution(width, height)); }
    /**
     * Sets vertical field of view, changing the camera framing rather than image brightness.
     * The default is 70°. Older providers explicitly reject unsupported custom FOV.
     * @param degrees finite vertical angle strictly between 0 and 180
     * @throws IllegalArgumentException for an invalid angle
     * @return a new builder with this setting
     */
    public SnapshotBuilder fov(double degrees) { return copy(settings, degrees, radius, chunksPerTick, excludedIds, excludedEntities); }
    /**
     * Selects rendering quality. Check the active provider's advertised profiles before choosing
     * a profile; explicit requested lights and shadows are not silently disabled by quality.
     * @param profile supported quality profile; default CLASSIC
     * @return a new builder with this setting
     */
    public SnapshotBuilder profile(RenderSettings.Profile profile) { return settings(settings.withProfile(profile)); }
    /**
     * Sets how far loaded-world evidence is captured around the source camera. Larger radii increase
     * capture and render work; required chunks must already be loaded.
     * @param blocks finite positive radius in blocks; default 16
     * @throws IllegalArgumentException for a nonpositive or nonfinite radius
     * @return a new builder with this setting
     */
    public SnapshotBuilder radius(double blocks) { return copy(settings, fov, blocks, chunksPerTick, excludedIds, excludedEntities); }
    /**
     * Sets the maximum number of chunks read per server tick during this snapshot. Required chunks
     * must already be loaded; this budget controls work, not chunk loading.
     * @param chunks positive per-tick budget; default 2
     * @return a new builder with this budget
     * @throws IllegalArgumentException if chunks is not positive
     */
    public SnapshotBuilder chunksPerTick(int chunks) { return copy(settings, fov, radius, chunks, excludedIds, excludedEntities); }
    /**
     * Replaces rendering settings, including resolution, profile, attachments, memory and timeout.
     * Camera FOV, roll, capture radius and recipe controls remain as configured.
     * @param settings complete immutable render settings
     * @return a new builder with this setting
     */
    public SnapshotBuilder settings(RenderSettings settings) { return copy(settings, fov, radius, chunksPerTick, excludedIds, excludedEntities); }
    /**
     * Adds UUID exclusions before visual capture, cumulatively across every layer.
     * @param ids UUIDs to omit; no null or duplicate elements
     * @return a new builder with the combined exclusions
     */
    public SnapshotBuilder excluding(UUID... ids) { return excluding(Set.of(ids)); }
    /**
     * Adds a defensive copy of UUID exclusions before visual capture. Exclusions are cumulative and
     * apply to every layer, including Xray and lighting; the camera entity is already excluded.
     * @param ids UUIDs to omit; no null elements
     * @return a new builder with this setting
     */
    public SnapshotBuilder excluding(Set<UUID> ids) {
        Set<UUID> combined = new HashSet<>(excludedIds); combined.addAll(Set.copyOf(ids));
        return copy(settings, fov, radius, chunksPerTick, combined, excludedEntities);
    }
    /**
     * Adds entities to omit before visual capture. References are copied now; UUIDs are read on the
     * server thread when preparing capture. The camera entity is already excluded.
     * @param entities entities to omit; no null elements
     * @return a new builder with this setting
     */
    public SnapshotBuilder excluding(Entity... entities) {
        List<Entity> combined = new ArrayList<>(excludedEntities); combined.addAll(List.of(entities));
        return copy(settings, fov, radius, chunksPerTick, excludedIds, combined);
    }

    /**
     * Captures and renders pixels and native evidence without allocating maps. Safe to request from
     * any thread: Bukkit reads are dispatched to the server thread and rendering runs asynchronously.
     * Cancellation propagates to owned work; never block the server thread waiting for completion.
     * @return a future for the immutable frame, or an exceptional completion on capture/render failure
     */
    public CompletableFuture<RenderedFrame> capture() { return captureFor(null); }

    private CompletableFuture<RenderedFrame> captureFor(CompletableFuture<?> lifetime) {
        CompletableFuture<RenderedFrame> output = renderer.track(new CompletableFuture<>());
        renderer.dispatch(output, () -> {
            Location pose;
            Set<UUID> exclusions = new HashSet<>(excludedIds);
            if (source == null) pose = camera.clone();
            else {
                if (!source.isValid() || source.isDead() || (source instanceof Player player && !player.isOnline()))
                    throw new RendererException(RenderFailureCode.INVALID_REQUEST, "snapshot source is no longer available");
                pose = source instanceof LivingEntity living ? living.getEyeLocation().clone() : source.getLocation().clone();
                exclusions.add(source.getUniqueId());
            }
            for (Entity entity : excludedEntities) exclusions.add(entity.getUniqueId());
            World world = Objects.requireNonNull(pose.getWorld(), "snapshot camera world");
            Set<UUID> hidden=options.hidden(world);exclusions.addAll(hidden);exclusions.addAll(options.entityIds());
            RenderEnvironment environment=Objects.requireNonNull(options.environment().get(),"environment supplier returned null");
            renderer.bindWorlds(output, List.of(world));
            if (output.isDone()) return;
            CaptureRequest request = new CaptureRequest(pose, radius, settings, chunksPerTick, exclusions);
            renderer.client();
            RendererService generation = renderer.generation();
            renderer.bind(output, generation);
            long captured = System.nanoTime();
            RecipePipeline pipeline=new RecipePipeline(renderer,settings,options,environment,pose,Set.of());
            pipeline.finishWith(lifetime==null?output:lifetime);
            CompletableFuture<RecipePipeline.Pixels> operation = pipeline.capture(request, fov);
            Renderer.follow(output, operation);
            operation.whenComplete((result, failure) -> {
                if (failure != null) { output.completeExceptionally(Renderer.unwrap(failure)); return; }
                renderer.dispatch(output, () -> {
                    renderer.requireGeneration(generation);
                    if(!RecipePipeline.visible(options,world,result.passes(),hidden))throw new RendererException(RenderFailureCode.STALE_SCENE,"visibility changed before snapshot publication");
                    long worldCaptured = result.passes().getFirst().result().worldCapturedNanos();
                    output.complete(new RenderedFrame(renderer, result, world, captured, worldCaptured == 0 ? captured : worldCaptured));
                });
            });
        });
        return output;
    }

    /**
     * Requests a frame and delivers the success callback on the server thread after a final
     * lifecycle check. Callback exceptions fail the returned future. No map is allocated.
     * @param success nonnull consumer of the rendered frame
     * @return a cancellable future settled after callback delivery; ordinary continuations have no thread guarantee
     */
    public CompletableFuture<RenderedFrame> capture(Consumer<RenderedFrame> success) { return capture(success, null); }
    /**
     * Requests a frame and delivers the success callback on the server thread after a final
     * lifecycle check. Callback exceptions fail the returned future. No map is allocated.
     * @param success nonnull consumer of the rendered frame
     * @param error optional server-thread failure observer, or null; failures still complete the future exceptionally
     * @return a cancellable future settled after callback delivery; ordinary continuations have no thread guarantee
     */
    public CompletableFuture<RenderedFrame> capture(Consumer<RenderedFrame> success, Consumer<Throwable> error) {
        Objects.requireNonNull(success, "success");
        return renderer.consumer(capture(), success, error);
    }

    /**
     * Captures, fits the result into one 128×128 map preserving aspect ratio with black letterboxing,
     * and saves a provider-owned photo. Conversion and durable storage run off-thread; map allocation
     * runs on the server thread. Completion means persistence succeeded, not merely that pixels exist.
     * Your plugin owns item delivery, inventory-full policy and organisation.
     * @return a cancellable future for the tagged photo item; fails if capture, conversion or saving fails
     */
    public CompletableFuture<ItemStack> captureMap() {
        CompletableFuture<ItemStack> output = renderer.track(new CompletableFuture<>());
        CompletableFuture<RenderedFrame> capture = captureFor(output);
        Renderer.follow(output, capture);
        capture.whenComplete((frame, failure) -> {
            if (output.isDone()) return;
            if (failure != null) { output.completeExceptionally(Renderer.unwrap(failure)); return; }
            CompletableFuture<ItemStack> map = frame.toMapItem(options.maps());
            Renderer.follow(output, map);
            map.whenComplete((item, saveFailure) -> {
                if (saveFailure == null) output.complete(item);
                else output.completeExceptionally(Renderer.unwrap(saveFailure));
            });
        });
        return output;
    }

    /**
     * Requests a saved photo and delivers the success callback on the server thread after a final
     * lifecycle check. Callback exceptions fail the returned future. The photo is saved before delivery;
     * your plugin chooses how to retain or deliver the item if an inventory is full.
     * @param success nonnull consumer of the durable photo item
     * @return a cancellable future settled after callback delivery; ordinary continuations have no thread guarantee
     */
    public CompletableFuture<ItemStack> captureMap(Consumer<ItemStack> success) { return captureMap(success, null); }
    /**
     * Requests a saved photo and delivers the success callback on the server thread after a final
     * lifecycle check. Callback exceptions fail the returned future. The photo is saved before delivery;
     * your plugin chooses how to retain or deliver the item if an inventory is full.
     * @param success nonnull consumer of the durable photo item
     * @param error optional server-thread failure observer, or null; failures still complete the future exceptionally
     * @return a cancellable future settled after callback delivery; ordinary continuations have no thread guarantee
     */
    public CompletableFuture<ItemStack> captureMap(Consumer<ItemStack> success, Consumer<Throwable> error) {
        Objects.requireNonNull(success, "success");
        return renderer.consumer(captureMap(), success, error);
    }

    private SnapshotBuilder copy(RenderSettings settings, double fov, double radius, int chunks,
                                 Set<UUID> ids, List<Entity> entities) {
        return new SnapshotBuilder(renderer, camera, source, settings, fov, radius, chunks, ids, entities, options);
    }

    /**
     * Rotates the camera around its viewing axis. Camera-relative lights follow the same roll.
     * The default is zero; this convenience method uses degrees, while SceneCamera uses radians.
     * @param degrees any finite roll angle, including full turns and inverted views
     * @throws IllegalArgumentException if degrees is nonfinite
     * @return a new builder with this setting
     */
    public SnapshotBuilder roll(double degrees){return options(options.controls(options.lighting(),degrees,options.progress()));}
    /**
     * Sets request-level world and explicit lighting. Layers inherit this setup unless they supply
     * their own complete lighting value. Exposure and post-render brightness are separate controls.
     * @param value immutable lighting setup; default RenderLighting.world()
     * @return a new builder with this setting
     */
    public SnapshotBuilder lighting(RenderLighting value){return options(options.controls(Objects.requireNonNull(value),options.roll(),options.progress()));}
    /**
     * Observes measured stage-local work on the server thread, at most once per tick. Updates are
     * coalesced; stages and render passes can restart at zero. Percentage is basisPoints / 100.0.
     * Keep the callback quick. Throwing fails pending work; a final coalesced notification may
     * arrive after successful completion and cannot revoke it. Lifecycle checks suppress callbacks
     * after cancellation or owner/provider invalidation.
     * @param value nonnull progress observer
     * @return a new builder with this setting
     */
    public SnapshotBuilder onProgress(Consumer<FrameProgress> value){return options(options.controls(options.lighting(),options.roll(),Objects.requireNonNull(value)));}
    FrameOptions frameOptions(){return options;}
    private SnapshotBuilder options(FrameOptions value){return new SnapshotBuilder(renderer,camera,source,settings,fov,radius,chunksPerTick,excludedIds,excludedEntities,value);}
    /**
     * Selects which captured block/entity geometry remains available to each render pass. Removed
     * geometry cannot contribute to camera, reflection or shadow rays; sampled environment and light
     * levels remain. Entity layers can instead restrict camera output while retaining scene geometry.
     * Use excluding(...) to omit entities before capture.
     * @param value immutable selection; default SceneContent.all()
     * @return a new builder with this setting
     */
    public SnapshotBuilder content(SceneContent value){return options(options.with(java.util.Objects.requireNonNull(value),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Sets rendering appearance, including exposure. Exposure is applied by the renderer; layer
     * brightness is a separate multiplication of already rendered pixels.
     * @param value immutable appearance overrides; unspecified fields inherit captured values
     * @return a new builder with this setting
     */
    public SnapshotBuilder appearance(RenderAppearance value){return options(options.with(options.content(),options.environment(),java.util.Objects.requireNonNull(value),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Replaces the ordered render recipe. Every layer derives independently from the same original
     * capture. Entity layers respect scene occlusion unless explicitly configured otherwise.
     * @param value nonempty immutable recipe; default one full-scene layer
     * @return a new builder with this setting
     */
    public SnapshotBuilder recipe(RenderRecipe value){return options(options.with(options.content(),options.environment(),options.appearance(),java.util.Objects.requireNonNull(value),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Sets map-palette conversion options. This affects map output without changing the rendered
     * RGBA image, capture settings or lighting.
     * @param value conversion settings; default no dithering
     * @return a new builder with this setting
     */
    public SnapshotBuilder mapOptions(MapOutputOptions value){return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),java.util.Objects.requireNonNull(value),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Applies Bukkit visibility rules for a viewer. Hidden entities are excluded from capture and
     * privacy is checked again before publication. Layers and explicit Xray cannot restore them.
     * @param value viewer whose canSee rules apply; does not itself create a player output
     * @return a new builder with this setting
     */
    public SnapshotBuilder visibleTo(Player value){return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),java.util.Objects.requireNonNull(value),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Overrides rendered time/weather without changing the live world or captured provenance.
     * Unspecified fields inherit captured facts. Layers can override individual fields again.
     * @param value immutable environment settings
     * @return a new builder with this setting
     */
    public SnapshotBuilder environment(RenderEnvironment value){java.util.Objects.requireNonNull(value);return environmentValue(()->value);}
    private SnapshotBuilder environmentValue(java.util.function.Supplier<RenderEnvironment> value){return options(options.with(options.content(),value,options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Adds entities from a list, set or other collection. References are defensively copied and UUIDs
     * are read on the server thread. Exclusions are cumulative and apply to every recipe layer.
     * @param values entity references to omit; no null elements
     * @return a new builder with this setting
     */
    public SnapshotBuilder excluding(java.util.Collection<? extends Entity> values){java.util.List<Entity> next=new java.util.ArrayList<>(options.entities());next.addAll(java.util.List.copyOf(values));return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),next,options.chunks(),options.sinks()));}
    /**
     * Adds UUIDs from any collection without retaining the caller's collection. Use this overload
     * when you already have IDs rather than live entity references.
     * @param values entity UUIDs to omit; no null elements
     * @return a new builder with this setting
     */
    public SnapshotBuilder excludingIds(java.util.Collection<UUID> values){return excluding(new java.util.HashSet<>(values));}
}
