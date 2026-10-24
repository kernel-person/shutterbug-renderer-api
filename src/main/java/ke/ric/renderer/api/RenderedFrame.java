package ke.ric.renderer.api;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Immutable rendered pixels and original capture/pass evidence, with optional asynchronous PNG
 * and durable photo-map output. A raw capture allocates no maps. Pure image/evidence access remains
 * available after the renderer closes; asynchronous output needs the owning facade and provider
 * generation to remain active.
 *
 * <p>Use {@link #image()} for final colour pixels, including composed layer treatments. Use
 * {@link #passes()} for native attachments, visibility and timings from each original pass.
 * {@link #result()} and the convenience visibility accessors are intentionally restricted to
 * single-pass frames because one combined native visibility result would be ambiguous.</p>
 *
 * <p>Output futures follow normal Java threading rules. Use the map consumer overloads when the
 * result needs Bukkit operations on the server thread, and never block that thread waiting for
 * conversion or saving. Cancelling an output propagates to its pending work; a file already
 * atomically replaced or a completed durable save cannot be undone by cancellation.</p>
 */
public final class RenderedFrame {
    private final java.util.Optional<CaptureProvenance> provenance;
    private final java.util.Optional<SceneCamera> effectiveCamera;
    private final FrameProgressReporter progress;
    private final Renderer renderer;
    private final RenderResult result;
    private final List<RenderPassResult> passes;
    private final World world;
    private final RendererService generation;
    private final long cameraCapturedNanos, worldCapturedNanos, publishedNanos;

    RenderedFrame(Renderer renderer, RenderResult result, World world, long cameraCapturedNanos, long worldCapturedNanos) {
        this(renderer,new RecipePipeline.Pixels(result,List.of(new RenderPassResult(0,result))),world,cameraCapturedNanos,worldCapturedNanos);
    }

    RenderedFrame(Renderer renderer,RecipePipeline.Pixels pixels,World world,long cameraCapturedNanos,long worldCapturedNanos) {
        this(renderer,pixels,world,cameraCapturedNanos,worldCapturedNanos,System.nanoTime());
    }

    RenderedFrame(Renderer renderer,RecipePipeline.Pixels pixels,World world,long cameraCapturedNanos,long worldCapturedNanos,long publishedNanos) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.result = pixels.image();
        this.passes=pixels.passes();this.progress=pixels.progress();
        this.provenance=pixels.provenance();this.effectiveCamera=pixels.camera();
        this.world = Objects.requireNonNull(world, "world");
        this.generation = renderer.generation();
        this.cameraCapturedNanos = cameraCapturedNanos;
        this.worldCapturedNanos = worldCapturedNanos;
        this.publishedNanos = publishedNanos;
    }

    /**
     * Returns facts frozen during the original capture. Rendered time/weather overrides and camera
     * reuse do not alter them.
     * @return captured provenance, or empty when an older provider cannot supply it
     */
    public java.util.Optional<CaptureProvenance> provenance(){return provenance;}
    /**
     * Returns the effective camera for this frame, including roll. During scene reuse this can
     * differ from provenance().sourceCamera(); angles in SceneCamera are radians.
     * @return the rendered camera when supplied by the provider
     */
    public java.util.Optional<SceneCamera> effectiveCamera(){return effectiveCamera;}
    /**
     * Returns native pixels, attachments, visibility and measured timings for a single-pass frame.
     * Layer colour, brightness and opacity are composed separately; use image() for final pixels.
     * @return the original native result for the only pass
     * @throws IllegalStateException if the frame has multiple passes; inspect passes() instead
     */
    public RenderResult result() { if(passes.size()!=1)throw new IllegalStateException("multi-pass evidence is ambiguous; use passes()");return passes.getFirst().result(); }
    /**
     * Returns native evidence for every recipe pass in composition order. These results precede
     * post-render colour, brightness and opacity; each preserves its own attachments and visibility.
     * @return immutable ordered pass results
     */
    public List<RenderPassResult> passes(){return passes;}
    RenderResult finalPixels(){return result;}
    /**
     * Returns when this frame's camera pose was sampled, on the System.nanoTime() clock.
     * Camera age and world-evidence age can differ during smooth scene reuse.
     * @return a monotonic timestamp meaningful only within this server process
     */
    public long cameraCapturedNanos() { return cameraCapturedNanos; }
    /**
     * Returns the first-world-evidence time, or a conservative capture-start fallback when unavailable.
     * Reframing does not renew this timestamp. Compare with cameraCapturedNanos() to measure reuse.
     * @return a monotonic timestamp meaningful only within this server process
     */
    public long worldCapturedNanos() { return worldCapturedNanos; }
    /**
     * Returns the monotonic publication-admission time, sampled at the final lifecycle and
     * freshness gate before output delivery. Stream reuse age is checked against this exact
     * timestamp. Individual callbacks and map updates may occur later; this is not their time.
     * Snapshot frames sample it during final server-thread publication. Like other frame
     * timestamps, it is meaningful only within this server process.
     */
    public long publishedNanos() { return publishedNanos; }
    /**
     * Returns native entity identities and primary-ray pixel coverage for a single-pass frame.
     * Names are labels, never identity keys. Coverage is native pass evidence, not inferred from
     * post-processing or name lists; transparent contributors that do not own a primary ray are omitted.
     * @return immutable visible-entity records
     * @throws IllegalStateException if multiple passes make one visibility list ambiguous
     * @throws RendererException with UNSUPPORTED_CAPABILITY if an older provider lacks entity identities
     */
    public List<VisibleEntity> getVisibleEntities() {
        RenderResult evidence=result();
        if (!evidence.hasEntityIdentities()) throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "this provider result does not include visible entity identities");
        return evidence.visibleEntities();
    }
    /**
     * Returns single-pass visible entity records whose captured type is minecraft:player.
     * @return an immutable list of player identities and primary-ray coverage
     * @throws IllegalStateException for a multi-pass frame
     * @throws RendererException if the provider lacks entity identities
     */
    public List<VisibleEntity> getVisiblePlayers() { return getVisibleEntities().stream().filter(VisibleEntity::player).toList(); }
    /**
     * Copies the final composed RGBA pixels into a new mutable BufferedImage. This pure conversion
     * does not allocate maps and remains usable after renderer close. It is synchronous; perform
     * large conversions on a worker executor. Modifying the returned image does not alter this frame.
     * @return a newly allocated image preserving final RGBA alpha
     */
    public BufferedImage toBufferedImage() { return RendererImages.toBufferedImage(result); }
    /**
     * Copies final composed pixels into a new BufferedImage; equivalent to toBufferedImage().
     * @return a new independently mutable image; large conversions should run on a worker executor
     */
    public BufferedImage image() { return toBufferedImage(); }

    /**
     * Encodes final pixels and atomically replaces the destination PNG on a bounded conversion
     * worker. The parent directory must already exist and support atomic replacement. Cancellation
     * interrupts pending encoding/I/O; a completed replacement cannot be revoked.
     * @param destination file path to create or replace
     * @return a future completing with the supplied path only after atomic saving succeeds
     * @throws NullPointerException if destination is null
     */
    public CompletableFuture<Path> savePng(Path destination) {
        Objects.requireNonNull(destination, "destination");
        CompletableFuture<Path> output = renderer.track(new CompletableFuture<>());
        renderer.bind(output, generation);
        renderer.dispatch(output, () -> {
            renderer.requireGeneration(generation);
            FrameProgressReporter outputProgress=progress==null?null:progress.fork(output,world);
            if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.CONVERSION,0);
            CompletableFuture<Path> write = renderer.convert(generation, () -> {
                byte[] png = PngConverter.encode(result);
                if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.SAVING,0);
                renderer.checkWorkerGeneration(generation);
                Path absolute = destination.toAbsolutePath();
                Path temporary = Files.createTempFile(absolute.getParent(), ".renderer-photo-", ".png.tmp");
                try {
                    Files.write(temporary, png);
                    renderer.checkWorkerGeneration(generation);
                    try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException failure) {
                        throw new IOException("destination does not support atomic PNG replacement", failure);
                    }
                    if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.SAVING,10000);
                    return destination;
                } finally { Files.deleteIfExists(temporary); }
            });
            Renderer.follow(output, write);
            write.whenComplete((path, failure) -> {
                if (failure == null) renderer.dispatch(output, () -> {
                    renderer.requireGeneration(generation);
                    output.complete(path);
                });
                else output.completeExceptionally(Renderer.unwrap(failure));
            });
        });
        return output;
    }

    /**
     * Fits final pixels into one 128×128 map with black aspect-preserving letterboxing, then saves
     * a durable provider-owned photo. Conversion and disk persistence run off-thread; Bukkit map
     * allocation runs on the server thread. Uses no dithering by default.
     * @return a future for the tagged map item, completed only after saving succeeds
     */
    public CompletableFuture<ItemStack> toMapItem() { return toMapItem(MapOutputOptions.defaults()); }

    /**
     * Converts final pixels to one aspect-fitted 128×128 map using the chosen palette dither, then
     * saves a durable provider-owned photo. Each call requests a photo; ordinary future callbacks
     * have no server-thread guarantee. Your plugin owns delivery and inventory-full policy.
     * @param options immutable map conversion settings
     * @return a future for the tagged item, completed only after successful persistence
     * @throws NullPointerException if options is null
     */
    public CompletableFuture<ItemStack> toMapItem(MapOutputOptions options) {
        Objects.requireNonNull(options,"options");
        CompletableFuture<ItemStack> output = renderer.track(new CompletableFuture<>());
        renderer.bind(output, generation);
        renderer.dispatch(output, () -> {
            renderer.bindWorlds(output, List.of(world));
            if (output.isDone()) return;
            renderer.requireGeneration(generation);
            FrameProgressReporter outputProgress=progress==null?null:progress.fork(output,world);
            if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.CONVERSION,0);
            CompletableFuture<byte[]> conversion = renderer.convert(generation, () -> RendererImages.palette(result, 128, 128,options));
            Renderer.follow(output, conversion);
            conversion.whenComplete((palette, failure) -> {
                if (failure != null) { output.completeExceptionally(Renderer.unwrap(failure)); return; }
                renderer.dispatch(output, () -> {
                    renderer.requireGeneration(generation);
                    if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.SAVING,0);
                    CompletableFuture<ItemStack> save = renderer.client().saveMap(world, palette).toCompletableFuture();
                    Renderer.follow(output, save);
                    save.whenComplete((item, saveFailure) -> {
                        if (saveFailure != null) { output.completeExceptionally(Renderer.unwrap(saveFailure)); return; }
                        renderer.dispatch(output, () -> { renderer.requireGeneration(generation);if(outputProgress!=null)outputProgress.stage(FrameProgress.Stage.SAVING,10000); output.complete(item); });
                    });
                });
            });
        });
        return output;
    }
    /**
     * Saves a durable photo before delivering its item on the server thread. Lifecycle is checked
     * before delivery; callback exceptions fail the returned future. Your plugin retains or delivers
     * the item and handles inventory capacity.
     * @param success nonnull server-thread consumer of the saved photo item
     * @return a cancellable future settled after callback delivery
     */
    public CompletableFuture<ItemStack> toMapItem(java.util.function.Consumer<ItemStack> success){return toMapItem(MapOutputOptions.defaults(),success,null);}
    /**
     * Saves a durable photo before delivering its item on the server thread. Lifecycle is checked
     * before delivery; callback exceptions fail the returned future. Your plugin retains or delivers
     * the item and handles inventory capacity.
     * @param success nonnull server-thread consumer of the saved photo item
     * @param error optional server-thread failure observer, or null; the future still fails
     * @return a cancellable future settled after callback delivery
     */
    public CompletableFuture<ItemStack> toMapItem(java.util.function.Consumer<ItemStack> success,java.util.function.Consumer<Throwable> error){return toMapItem(MapOutputOptions.defaults(),success,error);}
    /**
     * Saves a durable photo before delivering its item on the server thread. Lifecycle is checked
     * before delivery; callback exceptions fail the returned future. Your plugin retains or delivers
     * the item and handles inventory capacity.
     * @param options immutable palette conversion settings
     * @param success nonnull server-thread consumer of the saved photo item
     * @return a cancellable future settled after callback delivery
     */
    public CompletableFuture<ItemStack> toMapItem(MapOutputOptions options,java.util.function.Consumer<ItemStack> success){return toMapItem(options,success,null);}
    /**
     * Saves a durable photo before delivering its item on the server thread. Lifecycle is checked
     * before delivery; callback exceptions fail the returned future. Your plugin retains or delivers
     * the item and handles inventory capacity.
     * @param options immutable palette conversion settings
     * @param success nonnull server-thread consumer of the saved photo item
     * @param error optional server-thread failure observer, or null; the future still fails
     * @return a cancellable future settled after callback delivery
     */
    public CompletableFuture<ItemStack> toMapItem(MapOutputOptions options,java.util.function.Consumer<ItemStack> success,java.util.function.Consumer<Throwable> error){return renderer.consumer(toMapItem(options),Objects.requireNonNull(success),error);}
}
