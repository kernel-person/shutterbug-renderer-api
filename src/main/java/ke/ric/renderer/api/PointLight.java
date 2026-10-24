package ke.ric.renderer.api;
/**
 * Immutable omnidirectional point emitter with optional hard shadows. Intensity is an artistic
 * multiplier, not a physical unit; illumination attenuates with distance and stops at range.
 * Use {@link LightSpace#CAMERA} to make a light follow camera position, yaw, pitch and roll.
 *
 * <pre>{@code
 * var key = new PointLight(LightSpace.WORLD, 0xffe2b8,
 *         12, 68, -4, 8, 16, true);
 * var lighting = RenderLighting.studio().ambient(0.1).add(key);
 * }</pre>
 *
 * @param space coordinate system for the position
 * @param rgb 24-bit sRGB colour, 0x000000..0xffffff (no alpha channel)
 * @param x world X, or camera-right offset, in blocks
 * @param y world Y, or camera-up offset, in blocks
 * @param z world Z, or camera-forward offset, in blocks
 * @param intensity finite artistic intensity in 0..64
 * @param range finite maximum reach in blocks, greater than 0 and at most 64
 * @param shadows whether this emitter casts hard shadows
 */
public record PointLight(LightSpace space,int rgb,double x,double y,double z,double intensity,double range,boolean shadows) implements VirtualLight {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException for invalid colour, position, intensity or range
  * @throws NullPointerException if space is null
  */
 public PointLight { LightValidation.check(space,rgb,x,y,z,intensity,range); }
}
