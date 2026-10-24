package ke.ric.renderer.api;
import java.util.List;
/**
 * Immutable, nonempty ordered render layers derived independently from one original capture.
 * Layers are composited in list order: later layers paint over earlier layers according to opacity.
 * Use {@link RenderedFrame#image()} for final composed pixels and {@link RenderedFrame#passes()}
 * for each pass's native attachments, visibility and timing evidence.
 *
 * <pre>{@code
 * var highlight = RenderRecipe.layers(
 *         RenderLayer.scene().brightness(0.25),
 *         RenderLayer.entities(EntitySelector.living()).color(LayerColor.tint(0xffcc55)));
 * // The entity layer respects walls. Add .occlusion(Occlusion.SELECTED_ONLY) for explicit Xray.
 * }</pre>
 *
 * @param orderedLayers nonempty list, defensively copied; no null elements
 */
public record RenderRecipe(List<RenderLayer> orderedLayers) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if the list is empty
  * @throws NullPointerException if the list or any layer is null
  */
 public RenderRecipe { orderedLayers=List.copyOf(orderedLayers);if(orderedLayers.isEmpty())throw new IllegalArgumentException("recipe needs at least one layer"); }
 /**
  * Copies layers in their composition order. Each layer uses the same original captured evidence.
  * @param layers at least one layer, from back to front
  * @return an immutable recipe
  * @throws IllegalArgumentException if no layers are supplied
  */
 public static RenderRecipe layers(RenderLayer... layers){return new RenderRecipe(List.of(layers));}
}
