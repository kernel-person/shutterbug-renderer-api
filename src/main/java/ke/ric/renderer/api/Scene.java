package ke.ric.renderer.api;

/** Immutable engine-neutral scene. Implementations are provider-owned. */
public interface Scene {
    int version();
    int width();
    int height();
}
