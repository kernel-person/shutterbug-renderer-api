package ke.ric.renderer.api;
/**
 * Immutable unit direction. Construction normalizes any finite nonzero vector; its input length
 * has no effect on intensity or range. The coordinate system is selected by the light using it.
 *
 * @param x X or camera-right component
 * @param y Y or camera-up component
 * @param z Z or camera-forward component
 */
public record LightDirection(double x,double y,double z) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if any component is nonfinite or the vector is zero
  */
 public LightDirection { double scale=Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z))); if(!Double.isFinite(scale)||scale==0)throw new IllegalArgumentException("direction must be finite and nonzero");x/=scale;y/=scale;z/=scale;double length=Math.sqrt(x*x+y*y+z*z);x/=length;y/=length;z/=length; }
}
