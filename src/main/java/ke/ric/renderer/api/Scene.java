package ke.ric.renderer.api;

/** Immutable engine-neutral scene. Implementations are provider-owned. */
public interface Scene {
    /** Original capture facts, absent for manual scenes and older providers. */
    default java.util.Optional<CaptureProvenance> provenance() { return java.util.Optional.empty(); }
    /** Immutable retained entity metadata; absent means unavailable, not an empty scene. */
    default java.util.Optional<java.util.List<CapturedEntity>> capturedEntities() { return java.util.Optional.empty(); }
    /** Effective camera for this scene, including any reframe/roll. */
    default java.util.Optional<SceneCamera> effectiveCamera() { return java.util.Optional.empty(); }
    /** Returns the scene contract version, currently 1. */
    int version();
    /** Returns output width in pixels; submitted settings must match. */
    int width();
    /** Returns output height in pixels; submitted settings must match. */
    int height();
    /**
     * Monotonic {@link System#nanoTime()} timestamp of the first live world read, or zero when
     * unavailable (including manual scenes and older providers). Derivatives retain the original
     * timestamp. Meaningful only within this server process; never persist it as a wall-clock time.
     */
    default long worldCapturedNanos() { return 0L; }
}
