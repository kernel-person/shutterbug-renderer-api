package ke.ric.renderer.api;

import java.util.concurrent.CompletionStage;
import java.util.List;
import java.util.Set;

/**
 * A consuming plugin's lifecycle and ownership boundary for captures, scenes, assets, and tasks.
 * Clients become stale when closed or when their provider generation is disabled or reloaded.
 */
public interface RendererClient extends AutoCloseable {
    /** Returns a current lifecycle snapshot; individual operations always recheck validity. */
    boolean active();
    /** Returns a snapshot of this provider generation's advertised capabilities. */
    RendererCapabilities capabilities();
    /**
     * Returns explicit-lighting and primary-camera visibility limits for this provider generation.
     * Older providers safely report unsupported capabilities. Check these before requesting creative
     * controls; unsupported controls fail explicitly rather than being ignored.
     * @return immutable provider lighting capabilities
     */
    default LightingCapabilities lightingCapabilities() { return LightingCapabilities.unsupported(); }
    /** Starts a manual scene builder; callers must supply all required world/visual evidence instead of relying on live lookup. */
    SceneBuilder newSceneBuilder();
    /**
     * Captures loaded Paper world state asynchronously, batching thread-affine reads on the main
     * server thread. The completion continuation has no general thread guarantee.
     * Required chunks must already be loaded; missing evidence fails rather than loading chunks.
     * @param request copied camera, finite radius in blocks, dimensions and per-tick chunk budget
     * @return stage yielding an immutable client-owned scene or a typed failure; never block the server thread waiting
     */
    CompletionStage<Scene> capture(CaptureRequest request);
    /**
     * Captures loaded world evidence with a vertical field of view in degrees. Required chunks must
     * already be loaded. Older providers retain their 70° default and reject other values explicitly.
     * Completion continuations have no general server-thread guarantee.
     * @param request copied camera, capture radius, settings and per-tick budget
     * @param fovDegrees finite vertical FOV strictly between 0 and 180
     * @return asynchronous immutable client-owned capture
     * @throws IllegalArgumentException if FOV is invalid
     * @throws RendererException if the provider does not support the requested FOV
     */
    default CompletionStage<Scene> capture(CaptureRequest request, double fovDegrees) {
        if (!Double.isFinite(fovDegrees) || fovDegrees <= 0 || fovDegrees >= 180)
            throw new IllegalArgumentException("field of view must be finite and between 0 and 180 degrees");
        if (fovDegrees == 70) return capture(request);
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "custom capture field of view requires an updated renderer provider");
    }

    /**
     * Captures loaded evidence with FOV and roll in degrees. Optional progress may arrive on any
     * thread; fluent builders provide server-thread/coalesced observers. Older providers reject
     * nonzero roll explicitly and may omit capture progress.
     * @param request copied camera, capture radius, settings and per-tick budget
     * @param fovDegrees finite vertical FOV strictly between 0 and 180
     * @param rollDegrees any finite camera roll; zero preserves the ordinary orientation
     * @param progress optional capture-stage observer, or null
     * @return asynchronous immutable client-owned capture
     * @throws IllegalArgumentException if a requested angle is invalid
     * @throws RendererException if the provider cannot support the requested controls
     */
    default CompletionStage<Scene> capture(CaptureRequest request,double fovDegrees,double rollDegrees,
            java.util.function.Consumer<FrameProgress> progress) {
        if(!Double.isFinite(rollDegrees))throw new IllegalArgumentException("roll must be finite");
        if(rollDegrees!=0)throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"capture roll requires an updated renderer provider");
        return capture(request,fovDegrees);
    }

    /**
     * Saves a copied 128×128 map palette and creates an item only after durable photo storage.
     * Invoke on the server thread; conversion to palette bytes is the caller's responsibility.
     * The provider restores saved photos independently of the consuming plugin. Older providers
     * explicitly reject this optional operation.
     * @param world loaded world in which the map is allocated
     * @param palette exactly 16384 row-major Minecraft map-palette bytes; copied before retention
     * @return asynchronous saved photo item, or exceptional completion if allocation/persistence fails
     */
    default CompletionStage<org.bukkit.inventory.ItemStack> saveMap(org.bukkit.World world, byte[] palette) {
        return java.util.concurrent.CompletableFuture.failedFuture(new RendererException(
                RenderFailureCode.UNSUPPORTED_CAPABILITY, "saved maps require an updated renderer provider"));
    }

    /**
     * Creates a guarded temporary map on the server thread. The caller owns cleanup; this map is
     * not stored as a durable photograph. Older providers reject this optional operation.
     * @param world loaded allocation world
     * @param palette exactly 16384 row-major map-palette bytes
     * @return temporary map helper; close it when no longer needed
     * @throws RendererException if temporary allocation is unsupported or unsafe
     */
    default RenderedMap createTemporaryMap(org.bukkit.World world, byte[] palette) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "temporary map outputs require an updated renderer provider");
    }
    /**
     * Derives an immutable selection from existing scene evidence, without recapturing live state.
     * The source remains unchanged; removed evidence cannot be restored by a later derivation.
     * Providers predating this optional operation reject it with {@code UNSUPPORTED_CAPABILITY}.
     * This preview operation is supported only when validated with a matching development provider.
     * @param source scene owned by this active client/provider generation
     * @param selection block/entity evidence to retain; camera, object IDs and generation identity stay unchanged
     * @return immutable derivative; the source is not modified and no live Bukkit entity is read
     */
    default Scene deriveScene(Scene source, SceneSelection selection) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "scene derivation is not supported by this provider");
    }
    /** Derives immutable scene evidence after validating client and provider ownership.
     * Older providers reject even identity transforms with UNSUPPORTED_CAPABILITY.
     * @param source scene owned by this active client
     * @param transform immutable content/environment/appearance and optional lighting/visibility controls
     * @return immutable derivative, or the validated source for an identity transform
     */
    default Scene deriveScene(Scene source, SceneTransform transform) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY, "scene transforms are not supported by this provider");
    }
    /** Reserves composition bytes from the provider-generation shared pool until close or shutdown.
     * @param bytes positive reservation size
     * @return idempotent, thread-safe lease
     * @throws RendererException if unsupported, permanently oversized, or temporarily exhausted
     */
    default CompositionLease reserveComposition(long bytes) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY, "composition memory is not supported by this provider");
    }
    /** Returns the optional scene operations implemented by this provider. Older providers return empty. */
    default Set<SceneOperation> supportedSceneOperations() { return Set.of(); }
    /**
     * Appends copied, complete resolved entity evidence without changing the source or recapturing
     * world state. The scene must belong to this active client and provider generation. UUIDs and
     * nonzero object IDs must be unique across captured and appended entities. Targets require
     * actual meshes with finite, nondegenerate geometry enclosed by their world bounds.
     * Camera, chunks, lighting and weather are preserved. No live entity or resource lookup occurs.
     * @param source immutable scene owned by this client
     * @param entities immutable exact visual evidence; the list is defensively copied
     * @return a new client-owned scene; subsequent selections include the appended classifications
     * @throws RendererException for unsupported composition, stale ownership or invalid/oversized evidence
     */
    default Scene appendEntities(Scene source, List<SceneEntity> entities) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "entity composition is not supported by this provider");
    }
    /**
     * Renders a new camera pose against unchanged captured world evidence. This does not read the
     * world or refresh captured entities, environment or capture age. The camera must remain in the
     * original centre chunk and captured build-height coverage, within the displacement bound.
     * Age starts at the first capture read and is checked at derivation, admission and worker start.
     * Selecting or appending visuals never renews these limits. Camera-relative lights follow the new
     * effective camera. A fresh capture is required on STALE_SCENE or MISSING_CHUNK.
     * @param source captured scene owned by this active client/provider generation
     * @param camera effective camera in world blocks and radians; remains in the source world
     * @param policy maximum evidence age and displacement from the original capture
     * @return immutable camera-only derivative
     * @throws RendererException if unsupported, stale, foreign or outside captured coverage
     */
    default Scene reframeScene(Scene source, SceneCamera camera, SceneReusePolicy policy) {
        throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                "camera reframing is not supported by this provider");
    }
    /** Registers copied archive evidence and returns a client-owned handle; invalid/bounded archives fail synchronously. */
    AssetHandle registerAsset(AssetPack pack);
    /**
     * Validates and attempts bounded queue/memory admission, returning immediately on success.
     * @param job owned scene, matching settings and active asset hashes
     * @return nonblocking task; rendering is performed off the server thread
     * @throws RendererException if validation, capability, lifecycle, queue or memory admission fails
     */
    RenderTask submit(RenderJob job);
    /** Idempotently cancels or releases client-owned work and registrations. */
    @Override void close();
}
