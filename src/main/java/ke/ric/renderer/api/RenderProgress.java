package ke.ric.renderer.api;

import java.util.Objects;

/**
 * Immutable, non-estimated progress for an asynchronous render task.
 * Basis points are monotonic from {@code 0} through {@code 10_000}; failed or cancelled tasks keep
 * their last published snapshot.
 * @param phase current actual-work phase
 * @param basisPoints monotonic progress in 0..10000 (100 basis points equal one percent)
 */
public record RenderProgress(Phase phase, int basisPoints) {
    public RenderProgress {
        Objects.requireNonNull(phase, "phase");
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("basisPoints must be between 0 and 10000");
        }
    }

    /** Ordered provider work phases exposed without implementation-specific timing estimates. */
    public enum Phase {
        /** Admitted but waiting to start. */
        QUEUED,
        /** Preparing bounded scene and output resources. */
        PREPARING,
        /** Decoding input evidence. */
        DECODING,
        /** Executing core rendering. */
        RENDERING,
        /** Applying renderer post-processing. */
        POST_PROCESSING,
        /** Producing or decoding render diagnostics. */
        DIAGNOSTICS,
        /** Copying/validating immutable output; native completion alone is not final success. */
        COPYING_OUTPUT,
        /** Immutable result publication succeeded; progress is 10000. */
        COMPLETE
    }
}
