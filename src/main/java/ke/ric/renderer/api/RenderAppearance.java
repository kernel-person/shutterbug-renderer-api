package ke.ric.renderer.api;
import java.util.OptionalDouble;
/**
 * Immutable per-field rendering appearance overrides. Every modifier returns a new value.
 * Exposure changes renderer output appearance; it is distinct from field of view, surface-light
 * intensity and {@link RenderLayer#brightness(double)}, which scales already rendered pixels.
 * At request level, unset fields inherit capture settings; in a layer, they inherit the request.
 */
public final class RenderAppearance {
 private final Double exposure;
 private RenderAppearance(Double exposure){this.exposure=exposure;}
 /**
  * Inherits source exposure, normally 1.0; at layer level an unset exposure inherits the request.
  * @return appearance with no explicit overrides
  */
 public static RenderAppearance defaults(){return new RenderAppearance(null);}
 /**
  * Overrides renderer exposure without changing captured provenance or light positions.
  * @param value finite strictly positive exposure
  * @return a new appearance value
  * @throws IllegalArgumentException if exposure is nonfinite or not positive
  */
 public RenderAppearance exposure(double value){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("exposure must be finite and positive");return new RenderAppearance(value);}
 /**
  * Returns the requested exposure override.
  * @return exposure, or empty when inherited
  */
 public OptionalDouble exposure(){return exposure==null?OptionalDouble.empty():OptionalDouble.of(exposure);}
 RenderAppearance inherit(RenderAppearance request){return exposure==null?request:this;}
 /**
  * Reports whether every appearance field inherits its source.
  * @return true when exposure is unspecified
  */
 public boolean isDefault(){return exposure==null;}
}
