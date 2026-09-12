package ke.ric.renderer.api;

/** Coarse lifecycle state for an asynchronous render task. */
public enum RenderStatus {
    /** Admitted and waiting for provider work. */
    QUEUED,
    /** Preparation, native rendering, diagnostics, or copying is in progress. */
    RUNNING,
    /** An immutable result completed successfully. */
    SUCCEEDED,
    /** Cancellation won the terminal transition. */
    CANCELLED,
    /** The task completed exceptionally for another reason. */
    FAILED
}
