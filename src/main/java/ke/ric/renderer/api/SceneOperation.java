package ke.ric.renderer.api;

/** Optional operations on immutable client-owned scene evidence. */
public enum SceneOperation {
    /** Append complete resolved entities while preserving captured world evidence. */
    APPEND_ENTITIES,
    /** Replace only camera pose while explicitly bounding age and spatial reuse of capture evidence. */
    REFRAME_CAMERA,
    /** Filter evidence and override scene environment and exposure. */
    TRANSFORM_SCENE,
    /** Reserve bytes in the provider-generation composition memory pool. */
    COMPOSITION_MEMORY
}
