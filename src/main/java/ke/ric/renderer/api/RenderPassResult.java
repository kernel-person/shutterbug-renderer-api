package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Immutable original native evidence for one recipe layer. These pixels precede the layer's
 * post-render colour, brightness and opacity treatment. Native visibility describes this pass,
 * not inferred coverage in the final composite image.
 *
 * @param index zero-based recipe layer index
 * @param result original immutable pixels, attachments, visibility and measured timings
 */
public record RenderPassResult(int index, RenderResult result) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if index is negative
  * @throws NullPointerException if result is null
  */
 public RenderPassResult { if(index<0)throw new IllegalArgumentException("negative pass index");Objects.requireNonNull(result); }
}
