package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Immutable Minecraft map-palette conversion options. These affect palette output, not the
 * original RGBA image or native visibility evidence. Built-in single-map outputs preserve aspect
 * ratio with black letterboxing; tile conversion remains available through MinecraftMapConverter.
 *
 * @param dither palette dithering strategy; no dithering by default
 */
public record MapOutputOptions(MapDither dither) {
 /**
  * Constructs a validated immutable value.
  * @throws NullPointerException if dither is null
  */
 public MapOutputOptions { Objects.requireNonNull(dither); }
 /**
  * Selects map conversion without dithering.
  * @return options using MapDither.NONE
  */
 public static MapOutputOptions defaults(){return new MapOutputOptions(MapDither.NONE);}
 /**
  * Returns new conversion settings with the chosen palette-dither strategy.
  * @param value NONE, BAYER or FLOYD_STEINBERG
  * @return a new immutable options value
  */
 public MapOutputOptions dither(MapDither value){return new MapOutputOptions(value);}
}
