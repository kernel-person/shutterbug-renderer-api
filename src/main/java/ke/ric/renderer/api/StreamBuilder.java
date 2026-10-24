package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Immutable live-camera configuration. Each modifier returns a new builder. Call {@link #start()}
 * on the server thread after adding at least one frame consumer, palette sink or output.
 *
 * <p>Defaults are Classic 128×128, 70° vertical FOV, zero roll, a 16-block radius and a target of
 * 20 FPS. Smooth mode can reuse unchanged world evidence for at most 500 ms and two blocks of
 * camera movement within the original centre chunk. {@link #fresh()} requests new evidence for
 * every frame. FPS is a target: obsolete frames are discarded and work never queues to catch up.</p>
 *
 * <p>Streams share a fair capture coordinator with four chunks per server tick and bounded
 * conversion work. Each stream has at most one render/conversion in progress. Required chunks
 * must already be loaded. Maps are allocated once and updated, never saved as photographs.
 * After one second without a usable frame, built-in outputs show “No signal” until recovery.</p>
 *
 * <p>Camera and environment suppliers, frame/error/progress callbacks and Bukkit mutations run on
 * the server thread. Source death/logout, a null camera supplier, owner disable, provider replacement
 * or affected world unload ends or fails the feed. Partial output setup is rolled back, and cleanup
 * removes only owned temporary output.</p>
 *
 * <pre>{@code
 * RenderStream stream = renderer.stream(drone)
 *         .toScreen(upperLeftBackingBlock, BlockFace.NORTH, 2, 2)
 *         .toPlayer(operator)
 *         .onError(error -> plugin.getLogger().warning(error.getMessage()))
 *         .start();
 * // Later:
 * stream.stop();
 * }</pre>
 */
public final class StreamBuilder {
    private final FrameOptions options;
    final Renderer renderer;
    final StreamSource source;
    final RenderSettings settings;
    final double radius,fov;
    final int fps;
    final SceneReusePolicy reuse;
    final boolean fresh,explicitResolution;
    final Set<UUID> exclusions;
    final List<Consumer<RenderedFrame>> frames;
    final List<Consumer<Throwable>> errors;
    final List<OutputSpec> outputs;
    interface OutputSpec { StreamOutput create(Renderer renderer,String token); }

    StreamBuilder(Renderer renderer,Location camera) { this(renderer,new StreamSource(camera)); }
    StreamBuilder(Renderer renderer,Entity camera) { this(renderer,new StreamSource(camera)); }
    StreamBuilder(Renderer renderer,Supplier<Location> camera) { this(renderer,new StreamSource(camera)); }
    private StreamBuilder(Renderer renderer,StreamSource source) {
        this(renderer,source,RenderSettings.defaults(),16,70,20,new SceneReusePolicy(Duration.ofMillis(500),2),false,false,
                Set.of(),List.of(),List.of(),List.of(),FrameOptions.defaults());
    }
    private StreamBuilder(Renderer renderer,StreamSource source,RenderSettings settings,double radius,double fov,int fps,
                          SceneReusePolicy reuse,boolean fresh,boolean explicit,Set<UUID> exclusions,
                          List<Consumer<RenderedFrame>> frames,List<Consumer<Throwable>> errors,List<OutputSpec> outputs,FrameOptions options) {
        this.options=options;
        this.renderer=renderer;this.source=source;this.settings=settings;this.radius=radius;this.fov=fov;this.fps=fps;
        this.reuse=reuse;this.fresh=fresh;explicitResolution=explicit;this.exclusions=Set.copyOf(exclusions);
        this.frames=List.copyOf(frames);this.errors=List.copyOf(errors);this.outputs=List.copyOf(outputs);
    }
    private StreamBuilder copy(RenderSettings settings,double radius,double fov,int fps,SceneReusePolicy reuse,boolean fresh,
                               boolean explicit,Set<UUID> exclusions,List<Consumer<RenderedFrame>> frames,
                               List<Consumer<Throwable>> errors,List<OutputSpec> outputs) {
        return new StreamBuilder(renderer,source,settings,radius,fov,fps,reuse,fresh,explicit,exclusions,frames,errors,outputs,options);
    }
    /**
     * Sets a target frame rate. Slow work skips frames instead of queuing catch-up requests; actual
     * throughput depends on capture, rendering, resolution, conversion and server load.
     * @param fps target from 1 to 20 inclusive; default 20
     * @return a new builder with this target
     * @throws IllegalArgumentException if fps is outside 1..20
     */
    public StreamBuilder fps(int fps) {
        if(fps<1||fps>20)throw new IllegalArgumentException("stream fps must be 1..20");
        return copy(settings,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,outputs);
    }
    /**
     * Sets explicit render dimensions. The immutable original builder is unchanged.
     * Each map or screen preserves image aspect ratio with black letterboxing. This overrides
     * resolution inference from a screen grid.
     * @param width positive output width in pixels
     * @param height positive output height in pixels
     * @throws IllegalArgumentException for invalid dimensions
     * @return a new builder with this setting
     */
    public StreamBuilder resolution(int width,int height) { return settings(settings.withResolution(width,height)); }
    /**
     * Selects rendering quality. Check the active provider's advertised profiles before choosing
     * a profile; explicit requested lights and shadows are not silently disabled by quality.
     * @param profile supported quality profile; default CLASSIC
     * @return a new builder with this setting
     */
    public StreamBuilder profile(RenderSettings.Profile profile) {
        return copy(settings.withProfile(profile),radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,outputs);
    }
    /**
     * Replaces rendering settings, including resolution, profile, attachments, memory and timeout.
     * Camera FOV, roll, capture radius and recipe controls remain as configured.
     * Its dimensions count as an explicit resolution and disable screen-grid inference.
     * @param settings complete immutable render settings
     * @return a new builder with this setting
     */
    public StreamBuilder settings(RenderSettings settings) {
        return copy(Objects.requireNonNull(settings),radius,fov,fps,reuse,fresh,true,exclusions,frames,errors,outputs);
    }
    /**
     * Sets vertical field of view, changing the camera framing rather than image brightness.
     * The default is 70°. Older providers explicitly reject unsupported custom FOV.
     * @param degrees finite vertical angle strictly between 0 and 180
     * @throws IllegalArgumentException for an invalid angle
     * @return a new builder with this setting
     */
    public StreamBuilder fov(double degrees) {
        if(!Double.isFinite(degrees)||degrees<=0||degrees>=180)throw new IllegalArgumentException("fov must be in (0,180)");
        return copy(settings,radius,degrees,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,outputs);
    }
    /**
     * Sets how far loaded-world evidence is captured around the source camera. Larger radii increase
     * capture and render work; required chunks must already be loaded.
     * @param blocks finite positive radius in blocks; default 16
     * @throws IllegalArgumentException for a nonpositive or nonfinite radius
     * @return a new builder with this setting
     */
    public StreamBuilder radius(double blocks) {
        if(!Double.isFinite(blocks)||blocks<=0)throw new IllegalArgumentException("radius must be finite and positive");
        return copy(settings,blocks,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,outputs);
    }
    /**
     * Requests newly captured world evidence for every frame. The resulting camera pose is the
     * pose used for that capture; smooth mode instead reframes recent evidence for a newer pose.
     * @return a new builder with scene reuse disabled
     */
    public StreamBuilder fresh() { return copy(settings,radius,fov,fps,reuse,true,explicitResolution,exclusions,frames,errors,outputs); }
    /**
     * Enables smooth camera reframing with bounded evidence age and displacement. Neither limit is
     * renewed by reuse. The camera must stay in the captured centre chunk and covered build height.
     * @param policy immutable reuse bounds; defaults are 500 ms and two blocks
     * @return a new builder with scene reuse enabled
     */
    public StreamBuilder reuse(SceneReusePolicy policy) {
        return copy(settings,radius,fov,fps,Objects.requireNonNull(policy),false,explicitResolution,exclusions,frames,errors,outputs);
    }
    /**
     * Enables smooth camera reframing against unchanged captured world evidence. A fresh capture is
     * needed when its age, displacement or original centre-chunk coverage is exceeded.
     * @param maxWorldAge positive duration that fits in nanoseconds, measured from original capture
     * @param maxCameraDisplacement finite nonnegative distance from the original capture pose, in blocks
     * @return a new builder with these reuse bounds
     * @throws IllegalArgumentException for invalid bounds
     */
    public StreamBuilder reuse(Duration maxWorldAge,double maxCameraDisplacement) { return reuse(new SceneReusePolicy(maxWorldAge,maxCameraDisplacement)); }
    /**
     * Adds a defensive copy of UUID exclusions before visual capture. Exclusions are cumulative and
     * apply to every layer, including Xray and lighting; the camera entity is already excluded.
     * @param ids UUIDs to omit; no null elements
     * @return a new builder with this setting
     */
    public StreamBuilder excluding(Set<UUID> ids) {
        Set<UUID> all=new HashSet<>(exclusions);all.addAll(Set.copyOf(ids));
        return copy(settings,radius,fov,fps,reuse,fresh,explicitResolution,all,frames,errors,outputs);
    }
    /**
     * Adds entities to omit before visual capture. References are copied now; UUIDs are read on the
     * server thread when preparing capture. The camera entity is already excluded.
     * @param entities entities to omit; no null elements
     * @return a new builder with this setting
     */
    public StreamBuilder excluding(Entity... entities) {
        return excluding(java.util.List.of(entities));
    }
    /**
     * Adds a frame consumer called on the server thread after freshness and lifecycle checks.
     * Frames cannot rewind the feed. Keep callbacks quick; an exception fails and cleans up the stream.
     * @param consumer nonnull frame observer; multiple observers run in registration order
     * @return a new builder with this observer
     */
    public StreamBuilder onFrame(Consumer<RenderedFrame> consumer) {
        List<Consumer<RenderedFrame>> next=new ArrayList<>(frames);next.add(Objects.requireNonNull(consumer));
        return copy(settings,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,next,errors,outputs);
    }
    /**
     * Adds a server-thread error observer. Some errors, such as temporary queue pressure or missing
     * capture evidence, skip frames and may recover; permanent failures terminate the stream.
     * Inspect RenderStream.status() or completion() to distinguish terminal failure.
     * @param consumer nonnull error observer
     * @return a new builder with this observer
     */
    public StreamBuilder onError(Consumer<Throwable> consumer) {
        List<Consumer<Throwable>> next=new ArrayList<>(errors);next.add(Objects.requireNonNull(consumer));
        return copy(settings,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,next,outputs);
    }
    /**
     * Adds one temporary handheld map in a free inventory slot without replacing items. The image
     * preserves aspect ratio with black letterboxing. A full inventory fails setup; disconnect removes
     * this output. The map is updated in place and does not enter the saved-photo store.
     * @param player recipient of this temporary output
     * @return a new builder with this output
     */
    public StreamBuilder toPlayer(Player player) {
        Objects.requireNonNull(player);List<OutputSpec> next=new ArrayList<>(outputs);next.add((r,t)->new StreamPlayerOutput(r,t,player));
        return copy(settings,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,next);
    }
    /**
     * Adds a temporary wall screen made of owned item frames. The anchor is the upper-left backing
     * block as viewed from the requested outward face; columns extend right and rows extend down.
     * The wall and frame positions are validated at start. Partial setup is rolled back, and stop
     * removes only owned output. Existing item frames are rejected.
     *
     * <p>Unless resolution was explicitly set, this screen sets render dimensions to columns×128 by
     * rows×128. Multiple screens use the last inferred resolution. Outputs fit the final image to
     * their own grid without distorting its aspect ratio. Live maps are never persisted as photos.</p>
     * @param anchor copied location of the upper-left backing block
     * @param face NORTH, SOUTH, EAST or WEST, pointing outward from the backing wall
     * @param columns grid width, 1..16 maps
     * @param rows grid height, 1..16 maps; total at most 256
     * @return a new builder with this screen
     * @throws IllegalArgumentException for an unsupported face or grid size
     */
    public StreamBuilder toScreen(Location anchor,BlockFace face,int columns,int rows) {
        Location copy=Objects.requireNonNull(anchor).clone();Objects.requireNonNull(face);
        StreamScreenOutput.backingPosition(0,0,0,face,0,0);
        if(columns<1||rows<1||columns>16||rows>16||columns*rows>256)throw new IllegalArgumentException("screen grid must be 1..16 per side, at most 256 maps");
        List<OutputSpec> next=new ArrayList<>(outputs);next.add((r,t)->new StreamScreenOutput(r,t,copy,face,columns,rows));
        RenderSettings resolution=explicitResolution?settings:settings.withResolution(columns*128,rows*128);
        return copy(resolution,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,next);
    }
    /**
     * Starts the configured feed on the server thread and allocates its temporary outputs once.
     * At least one output, palette sink or frame consumer is required. Setup failure rolls back
     * partial output creation. Owner disable also stops this stream automatically.
     * @return the lifecycle handle; stop it when the feed is no longer needed
     * @throws IllegalStateException if called off the server thread or no destination is configured
     * @throws RendererException if the owner/provider is unavailable or setup cannot complete
     */
    public RenderStream start() { renderer.requireOpen();return new RenderStream(this); }

    /**
     * Rotates the camera around its viewing axis. Camera-relative lights follow the same roll.
     * The default is zero; this convenience method uses degrees, while SceneCamera uses radians.
     * @param degrees any finite roll angle, including full turns and inverted views
     * @throws IllegalArgumentException if degrees is nonfinite
     * @return a new builder with this setting
     */
    public StreamBuilder roll(double degrees){return options(options.controls(options.lighting(),degrees,options.progress()));}
    /**
     * Sets request-level world and explicit lighting. Layers inherit this setup unless they supply
     * their own complete lighting value. Exposure and post-render brightness are separate controls.
     * @param value immutable lighting setup; default RenderLighting.world()
     * @return a new builder with this setting
     */
    public StreamBuilder lighting(RenderLighting value){return options(options.controls(Objects.requireNonNull(value),options.roll(),options.progress()));}
    /**
     * Observes measured stage-local work on the server thread, at most once per tick. Updates are
     * coalesced; stages and render passes can restart at zero. Percentage is basisPoints / 100.0.
     * Keep the callback quick. Throwing fails pending work; a final coalesced notification may
     * arrive after successful completion and cannot revoke it. Lifecycle checks suppress callbacks
     * after cancellation or owner/provider invalidation.
     * @param value nonnull progress observer
     * @return a new builder with this setting
     */
    public StreamBuilder onProgress(Consumer<FrameProgress> value){return options(options.controls(options.lighting(),options.roll(),Objects.requireNonNull(value)));}
    FrameOptions frameOptions(){return options;}
    private StreamBuilder options(FrameOptions value){return new StreamBuilder(renderer,source,settings,radius,fov,fps,reuse,fresh,explicitResolution,exclusions,frames,errors,outputs,value);}
    /**
     * Selects which captured block/entity geometry remains available to each render pass. Removed
     * geometry cannot contribute to camera, reflection or shadow rays; sampled environment and light
     * levels remain. Entity layers can instead restrict camera output while retaining scene geometry.
     * Use excluding(...) to omit entities before capture.
     * @param value immutable selection; default SceneContent.all()
     * @return a new builder with this setting
     */
    public StreamBuilder content(SceneContent value){return options(options.with(java.util.Objects.requireNonNull(value),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Sets rendering appearance, including exposure. Exposure is applied by the renderer; layer
     * brightness is a separate multiplication of already rendered pixels.
     * @param value immutable appearance overrides; unspecified fields inherit captured values
     * @return a new builder with this setting
     */
    public StreamBuilder appearance(RenderAppearance value){return options(options.with(options.content(),options.environment(),java.util.Objects.requireNonNull(value),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Replaces the ordered render recipe. Every layer derives independently from the same original
     * capture. Entity layers respect scene occlusion unless explicitly configured otherwise.
     * @param value nonempty immutable recipe; default one full-scene layer
     * @return a new builder with this setting
     */
    public StreamBuilder recipe(RenderRecipe value){return options(options.with(options.content(),options.environment(),options.appearance(),java.util.Objects.requireNonNull(value),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Sets map-palette conversion options. This affects map output without changing the rendered
     * RGBA image, capture settings or lighting.
     * @param value conversion settings; default no dithering
     * @return a new builder with this setting
     */
    public StreamBuilder mapOptions(MapOutputOptions value){return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),java.util.Objects.requireNonNull(value),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Applies Bukkit visibility rules for a viewer. Hidden entities are excluded from capture and
     * privacy is checked again before publication. Layers and explicit Xray cannot restore them.
     * @param value viewer whose canSee rules apply; does not itself create a player output
     * @return a new builder with this setting
     */
    public StreamBuilder visibleTo(Player value){return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),java.util.Objects.requireNonNull(value),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Overrides rendered time/weather without changing the live world or captured provenance.
     * Unspecified fields inherit captured facts. Layers can override individual fields again.
     * @param value immutable environment settings
     * @return a new builder with this setting
     */
    public StreamBuilder environment(RenderEnvironment value){java.util.Objects.requireNonNull(value);return environmentValue(()->value);}
    private StreamBuilder environmentValue(java.util.function.Supplier<RenderEnvironment> value){return options(options.with(options.content(),value,options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),options.sinks()));}
    /**
     * Adds entities from a list, set or other collection. References are defensively copied and UUIDs
     * are read on the server thread. Exclusions are cumulative and apply to every recipe layer.
     * @param values entity references to omit; no null elements
     * @return a new builder with this setting
     */
    public StreamBuilder excluding(java.util.Collection<? extends Entity> values){java.util.List<Entity> next=new java.util.ArrayList<>(options.entities());next.addAll(java.util.List.copyOf(values));return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),next,options.chunks(),options.sinks()));}
    /**
     * Adds UUIDs from any collection without retaining the caller's collection. Use this overload
     * when you already have IDs rather than live entity references.
     * @param values entity UUIDs to omit; no null elements
     * @return a new builder with this setting
     */
    public StreamBuilder excludingIds(java.util.Collection<UUID> values){return excluding(new java.util.HashSet<>(values));}
    /**
     * Adds copied UUID exclusions before capture. The source entity is already omitted.
     * @param values entity identities to omit; no null elements
     * @return a new builder with combined exclusions
     */
    public StreamBuilder excluding(UUID... values){return excludingIds(java.util.List.of(values));}
    /**
     * Samples rendered environment overrides once per prepared frame on the server thread. Keep
     * the supplier quick. A null value or thrown exception fails the feed; captured provenance remains
     * unchanged. This can change time/weather while reusing existing world evidence.
     * @param value nonnull supplier returning a nonnull immutable environment
     * @return a new builder using this supplier
     */
    public StreamBuilder environment(Supplier<RenderEnvironment> value){return environmentValue(value);}
    /**
     * Limits this stream's share of the global four-chunk capture budget per server tick. All
     * convenience streams share the fair coordinator; this does not increase its global budget.
     * @param value positive requested budget; values above four are capped at four
     * @return a new builder with this capture budget
     * @throws IllegalArgumentException if value is not positive
     */
    public StreamBuilder chunksPerTick(int value){return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),Math.min(4,value),options.sinks()));}
    /**
     * Adds a caller-owned destination for an aspect-fitted 128×128 palette. This allocates no maps
     * and performs no photo saving. Sink callbacks run on the server thread; a thrown exception
     * terminates the stream. The sink is closed once during terminal cleanup.
     * @param value nonnull palette destination
     * @return a new builder with this sink
     */
    public StreamBuilder toMapSink(MapSink value){java.util.List<MapSink> next=new java.util.ArrayList<>(options.sinks());next.add(java.util.Objects.requireNonNull(value));return options(options.with(options.content(),options.environment(),options.appearance(),options.recipe(),options.viewer(),options.maps(),options.entities(),options.chunks(),next));}
}
