package ke.ric.renderer.api;
/**
 * Immutable provider limits for explicit lighting and primary-camera visibility controls.
 * These are capability limits, not a claim about frame rate. A request must fit every advertised
 * limit; unsupported providers reject controls rather than ignoring them.
 *
 * @param supported whether creative lighting and primary visibility controls are supported
 * @param maxLights maximum virtual lights per pass
 * @param maxShadowLights maximum shadow-casting virtual lights per pass
 * @param maxRange maximum light range in blocks
 * @param maxIntensity maximum artistic light intensity
 */
public record LightingCapabilities(boolean supported,int maxLights,int maxShadowLights,double maxRange,double maxIntensity) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if a limit is negative or nonfinite, or shadow count exceeds light count
  */
 public LightingCapabilities {if(maxLights<0||maxShadowLights<0||maxShadowLights>maxLights||!Double.isFinite(maxRange)||maxRange<0||!Double.isFinite(maxIntensity)||maxIntensity<0)throw new IllegalArgumentException("invalid lighting limits");}
 /**
  * Returns the safe default for providers that predate creative lighting.
  * @return unsupported capabilities with all limits zero
  */
 public static LightingCapabilities unsupported(){return new LightingCapabilities(false,0,0,0,0);}
 /**
  * Returns the standard creative-light limits: 16 lights, eight shadow lights, range 64 and intensity 64.
  * @return the standard supported capability value
  */
 public static LightingCapabilities standard(){return new LightingCapabilities(true,16,8,64,64);}
}
