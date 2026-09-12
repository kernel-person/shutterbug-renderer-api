package ke.ric.renderer.api;

/** Exact world transform applied to a resolved entity target. */
public record ResolvedTransform(double x,double y,double z,float yawDegrees) {public ResolvedTransform {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yawDegrees))throw new IllegalArgumentException("transform must be finite");}}
