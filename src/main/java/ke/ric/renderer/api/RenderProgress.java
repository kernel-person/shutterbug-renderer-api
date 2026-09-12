package ke.ric.renderer.api;

import java.util.Objects;

/**
 * Immutable, non-estimated progress for an asynchronous render task.
 * Basis points are monotonic from {@code 0} through {@code 10_000}; failed or cancelled tasks keep
 * their last published snapshot.
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
        QUEUED,
        PREPARING,
        DECODING,
        RENDERING,
        POST_PROCESSING,
        DIAGNOSTICS,
        COPYING_OUTPUT,
        COMPLETE
    }
}
