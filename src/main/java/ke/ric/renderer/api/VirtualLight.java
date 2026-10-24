package ke.ric.renderer.api;
/**
 * Common read-only light properties. {@link RenderLighting} accepts only the immutable
 * {@link PointLight} and {@link SpotLight} records; arbitrary implementations are rejected before
 * a request retains them. RGB is 24-bit sRGB, position/range use blocks, and intensity is artistic.
 * Limits are per render pass and advertised by {@link RendererClient#lightingCapabilities()}.
 */
public interface VirtualLight {
 /**
  * Returns the coordinate system shared by this light's position and direction.
  * @return WORLD or CAMERA
  */
LightSpace space(); /**
 * Returns this light's 24-bit sRGB colour without alpha.
 * @return RGB in 0x000000..0xffffff
 */
int rgb(); /**
 * Returns the world X position or camera-right offset.
 * @return finite position in blocks
 */
double x(); /**
 * Returns the world Y position or camera-up offset.
 * @return finite position in blocks
 */
double y(); /**
 * Returns the world Z position or camera-forward offset.
 * @return finite position in blocks
 */
double z(); /**
 * Returns this emitter's artistic intensity, independent of inherited-world light factors.
 * @return finite intensity in 0..64
 */
double intensity(); /**
 * Returns the maximum distance reached by this emitter.
 * @return finite range in blocks, greater than 0 and at most 64
 */
double range(); /**
 * Reports whether this emitter requests hard shadows.
 * @return true when shadow geometry should occlude this emitter
 */
boolean shadows();
}
