package ke.ric.renderer.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** A nonblocking handle for one admitted asynchronous render. */
public interface RenderTask {
    /** Returns the provider-assigned task identifier. */
    UUID id();
    /** Returns the current coarse lifecycle state. */
    RenderStatus status();
    /** Returns the latest monotonic, actual-work progress snapshot. */
    RenderProgress progress();
    /** Returns true only if this call wins cancellation of a nonterminal task. */
    boolean cancel();
    /** Returns the stage that yields an immutable result or a typed failure. */
    CompletionStage<RenderResult> completion();
}
