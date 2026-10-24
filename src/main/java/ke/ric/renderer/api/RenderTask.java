package ke.ric.renderer.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** A nonblocking task handle. Progress, cancellation and completion observation are safe from any thread. */
public interface RenderTask {
    /** Returns the provider-assigned task identifier. */
    UUID id();
    /** Returns the current coarse lifecycle state. */
    RenderStatus status();
    /** Returns actual-work progress in 0..10000 basis points; repeated snapshots may be equal and failures retain the last value. */
    RenderProgress progress();
    /** Cooperatively cancels from any thread; returns true only when cancellation wins, false after any terminal outcome. */
    boolean cancel();
    /** Returns the immutable frame or typed failure; continuations have no server-thread guarantee. Never block the server thread. */
    CompletionStage<RenderResult> completion();
}
