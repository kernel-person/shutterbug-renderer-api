package ke.ric.renderer.api;

/**
 * A client-owned registration for one immutable asset pack.
 *
 * <p>The content hash can be placed in a {@link RenderJob}. Closing the handle releases the
 * registration after in-flight users finish; closing it more than once has no additional effect.</p>
 */
public interface AssetHandle extends AutoCloseable {
    /** Returns the stable content hash used to select this pack in a render job. */
    String contentHash();

    /** Returns whether this registration can still be selected by its owning client. */
    boolean active();

    /** Releases this registration. */
    @Override
    void close();
}
