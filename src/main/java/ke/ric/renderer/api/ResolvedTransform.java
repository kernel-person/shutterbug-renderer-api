package ke.ric.renderer.api;

/**
 * Exact finite world transform applied to a resolved entity target; unlike SceneBuilder camera angles, yaw uses degrees.
 * @param x world X position in blocks
 * @param y world Y position in blocks
 * @param z world Z position in blocks
 * @param yawDegrees horizontal rotation in degrees, matching Bukkit orientation units
 */
public record ResolvedTransform(double x,double y,double z,float yawDegrees) {public ResolvedTransform {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yawDegrees))throw new IllegalArgumentException("transform must be finite");}}
