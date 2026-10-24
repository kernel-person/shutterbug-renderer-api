package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Immutable RGB treatment for a recipe layer. Composition applies colour treatment, then layer
 * brightness, then opacity. This changes the output colour, not physical lighting or surface normals.
 * Entity-only layers still use native object-ID coverage; a solid colour does not fill the background.
 *
 * @param mode original colour, multiplicative tint or solid replacement colour
 * @param rgb 24-bit sRGB colour in 0x000000..0xffffff; ignored for ORIGINAL
 */
public record LayerColor(Mode mode,int rgb) {
 /** Available color treatments. */
 public enum Mode {
 /** Preserves the rendered RGB channels. */ ORIGINAL,
 /** Multiplies each rendered channel by the corresponding tint channel divided by 255. */ TINT,
 /** Replaces rendered RGB channels with the supplied colour while retaining coverage. */ SOLID
}
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if rgb is outside the 24-bit range
  * @throws NullPointerException if mode is null
  */
 public LayerColor {Objects.requireNonNull(mode,"mode");if(rgb<0||rgb>0xffffff)throw new IllegalArgumentException("RGB must be 0..0xffffff");}
 /**
  * Preserves the rendered RGB channels.
  * @return original-colour treatment
  */
 public static LayerColor original(){return new LayerColor(Mode.ORIGINAL,0xffffff);}
 /**
  * Multiplies the rendered RGB by a tint; white preserves the input and black removes its colour.
  * @param rgb 24-bit sRGB tint without alpha
  * @return multiplicative tint treatment
  * @throws IllegalArgumentException if rgb is outside 0..0xffffff
  */
 public static LayerColor tint(int rgb){return new LayerColor(Mode.TINT,rgb);}
 /**
  * Replaces RGB with one colour while retaining the layer's pixel coverage and opacity.
  * @param rgb 24-bit sRGB output colour without alpha
  * @return solid-colour treatment
  * @throws IllegalArgumentException if rgb is outside 0..0xffffff
  */
 public static LayerColor solid(int rgb){return new LayerColor(Mode.SOLID,rgb);}
 double channel(int source,int channel){int target=(rgb>>(16-channel*8))&255;return switch(mode){case ORIGINAL->source;case TINT->source*target/255.0;case SOLID->target;};}
}
