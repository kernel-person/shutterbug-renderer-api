package ke.ric.renderer.api;

/** Stable machine-readable categories for failures reported by the renderer provider. */
public enum RenderFailureCode {
    /** A request, scene, or setting failed validation. */
    INVALID_REQUEST,
    /** The bounded render queue could not admit another task. */
    QUEUE_FULL,
    /** Cancellation won before the task became terminal. */
    CANCELLED,
    /** The end-to-end render deadline expired. */
    TIMEOUT,
    /** The owning renderer client was closed or is no longer current. */
    CLIENT_CLOSED,
    /** Required loaded chunk evidence was unavailable. */
    MISSING_CHUNK,
    /** A selected asset hash has no active registration. */
    MISSING_ASSET,
    /** A supplied asset archive failed bounded validation. */
    MALFORMED_ASSET,
    /** The runtime does not advertise a requested profile, attachment, or route. */
    UNSUPPORTED_CAPABILITY,
    /** The exact supported platform or native runtime is unavailable. */
    UNSUPPORTED_PLATFORM,
    /** Native execution, diagnostics, or provider containment failed. */
    NATIVE_FAILURE,
    /** Admission or execution would exceed a memory bound. */
    MEMORY_LIMIT,
    /** The provider was disabled or invalidated while the operation was active. */
    PROVIDER_DISABLED,
    /** A captured scene belongs to an earlier provider or catalog generation. */
    STALE_SCENE
}
