package ke.ric.renderer.api;
/**
 * Immutable directional spot emitter with a smooth falloff between its inner and outer cones.
 * Cone values are full angles in degrees, not half-angles. Position and direction use the same
 * {@link LightSpace}; camera-relative lights follow position, yaw, pitch and roll even during reuse.
 * Intensity is artistic rather than a physical unit. Requested shadows are hard shadows.
 *
 * <pre>{@code
 * var flash = new SpotLight(LightSpace.CAMERA, 0xffffff,
 *         0, 0, 0, new LightDirection(0, 0, 1), 8, 16, 30, 60, true);
 * }</pre>
 *
 * @param space coordinate system for both position and direction
 * @param rgb 24-bit sRGB colour, 0x000000..0xffffff (no alpha channel)
 * @param x world X, or camera-right offset, in blocks
 * @param y world Y, or camera-up offset, in blocks
 * @param z world Z, or camera-forward offset, in blocks
 * @param direction normalized direction in which the emitter points
 * @param intensity finite artistic intensity in 0..64
 * @param range finite maximum reach in blocks, greater than 0 and at most 64
 * @param innerConeDegrees finite full angle at which the smooth transition begins, at least 0
 * @param outerConeDegrees finite full cutoff angle, greater than inner and at most 180
 * @param shadows whether this emitter casts hard shadows
 */
public record SpotLight(LightSpace space,int rgb,double x,double y,double z,LightDirection direction,double intensity,double range,double innerConeDegrees,double outerConeDegrees,boolean shadows) implements VirtualLight {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException for invalid colour, position, intensity, range or cone angles
  * @throws NullPointerException if space or direction is null
  */
 public SpotLight { LightValidation.check(space,rgb,x,y,z,intensity,range);java.util.Objects.requireNonNull(direction,"direction");if(!Double.isFinite(innerConeDegrees)||!Double.isFinite(outerConeDegrees)||innerConeDegrees<0||innerConeDegrees>=outerConeDegrees||outerConeDegrees>180)throw new IllegalArgumentException("cones require 0 <= inner < outer <= 180 degrees"); }
}
