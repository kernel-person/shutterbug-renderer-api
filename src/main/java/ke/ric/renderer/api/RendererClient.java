package ke.ric.renderer.api;

import java.util.concurrent.CompletionStage;

/**
 * A consuming plugin's lifecycle and ownership boundary for captures, scenes, assets, and tasks.
 * Clients become stale when closed or when their provider generation is disabled or reloaded.
 */
public interface RendererClient extends AutoCloseable {
    /** Returns a current lifecycle snapshot; individual operations always recheck validity. */
    boolean active();
    /** Returns a snapshot of this provider generation's advertised capabilities. */
    RendererCapabilities capabilities();
    /** Starts a complete caller-supplied engine-neutral scene. */
    SceneBuilder newSceneBuilder();
    /**
     * Captures loaded Paper world state asynchronously, batching thread-affine reads on the main
     * server thread. The completion continuation has no general thread guarantee.
     */
    CompletionStage<Scene> capture(CaptureRequest request);
    /** Validates and registers an immutable client-owned asset pack. */
    AssetHandle registerAsset(AssetPack pack);
    /** Validates and attempts bounded queue admission, returning immediately on success. */
    RenderTask submit(RenderJob job);
    /** Idempotently cancels or releases client-owned work and registrations. */
    @Override void close();
}
