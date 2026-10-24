package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Measured progress within one stage of a snapshot or stream frame. This is not an estimate of
 * remaining time or a synthetic overall percentage. A new stage or render pass can restart at zero.
 * Divide {@code basisPoints} by 100.0 for a percentage within the reported stage.
 *
 * <p>{@link SnapshotBuilder#onProgress(java.util.function.Consumer)} and
 * {@link StreamBuilder#onProgress(java.util.function.Consumer)} dispatch on the server thread at
 * most once per tick. Updates are coalesced, so brief intermediate states need not be delivered.
 * Conversion and saving stages occur only when the requested output requires them. Low-level
 * {@link RendererClient#capture(CaptureRequest, double, double, java.util.function.Consumer)}
 * progress does not itself guarantee the server thread.</p>
 *
 * @param stage currently measured stage
 * @param passIndex zero-based recipe pass during RENDER, or -1 for every other stage
 * @param passCount total number of recipe render passes, at least 1
 * @param basisPoints measured completion in 0..10000 for this stage/pass
 */
public record FrameProgress(Stage stage,int passIndex,int passCount,int basisPoints) {
 /** Available work stages. */
 public enum Stage {
 /** Reading loaded world evidence in bounded server-thread batches. */ CAPTURE,
 /** Executing the render pass identified by passIndex. */ RENDER,
 /** Composing layers or converting image/palette output. */ CONVERSION,
 /** Persisting the requested PNG or durable map photo. */ SAVING
}
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException for invalid progress, counts or stage/index combinations
  * @throws NullPointerException if stage is null
  */
 public FrameProgress {Objects.requireNonNull(stage,"stage");if(passCount<1||basisPoints<0||basisPoints>10000||stage==Stage.RENDER&&(passIndex<0||passIndex>=passCount)||stage!=Stage.RENDER&&passIndex!=-1)throw new IllegalArgumentException("invalid stage progress or pass index");}
}
