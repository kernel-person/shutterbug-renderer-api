package ke.ric.renderer.api;
import java.util.*;
/**
 * Immutable lighting for a request or one recipe layer. Every modifier returns a new value.
 * {@link #world()} preserves the existing inherited lighting path. {@link #studio()} starts with
 * ambient, sunlight and captured block lighting disabled, ready for explicit lights.
 *
 * <p>Entity-layer selection and occlusion can restrict primary-camera output while retaining
 * surrounding geometry for lighting and shadows. Geometry excluded by {@link SceneContent} is
 * instead removed from all rays. Capture exclusions and viewer privacy remove evidence before it can
 * influence rendering. Explicit lights and requested
 * hard shadows are supported in every advertised quality profile by the matching provider.</p>
 *
 * <p>Limits are per render pass: at most 16 virtual lights, eight casting shadows, range at most
 * 64 blocks and factors/intensity from 0 to 64. Check {@link RendererClient#lightingCapabilities()}
 * for the active provider. Unsupported controls or lower provider limits fail explicitly.</p>
 *
 * @param ambientFactor multiplier for inherited ambient light, finite in 0..64
 * @param sunFactor multiplier for inherited sunlight, finite in 0..64
 * @param blockFactor multiplier for captured block illumination and coloured block emitters, finite in 0..64
 * @param sunDirection world-space direction from a surface toward the sun, or null to follow rendered time
 * @param lights copied list of immutable {@link PointLight} or {@link SpotLight} values
 */
public record RenderLighting(double ambientFactor,double sunFactor,double blockFactor,LightDirection sunDirection,List<VirtualLight> lights) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException if factors, light types or light-count limits are invalid
  * @throws NullPointerException if lights or an element is null
  */
 public RenderLighting {LightValidation.factor(ambientFactor);LightValidation.factor(sunFactor);LightValidation.factor(blockFactor);lights=List.copyOf(lights);for(VirtualLight light:lights)if(!(light instanceof PointLight)&&!(light instanceof SpotLight))throw new IllegalArgumentException("only immutable PointLight and SpotLight are supported");if(lights.size()>16||lights.stream().filter(VirtualLight::shadows).count()>8)throw new IllegalArgumentException("at most 16 lights and 8 shadow lights per pass");}
 /**
  * Preserves inherited ambient, sunlight and block lighting without adding virtual lights.
  * @return lighting with factors 1, automatic sun direction and no explicit lights
  */
 public static RenderLighting world(){return new RenderLighting(1,1,1,null,List.of());}
 /**
  * Disables inherited ambient, sunlight and block lighting. Add lights explicitly for a studio setup.
  * @return lighting with factors 0 and no explicit lights
  */
 public static RenderLighting studio(){return new RenderLighting(0,0,0,null,List.of());}
 /**
  * Sets the inherited ambient-light multiplier. This does not scale explicit lights.
  * @param value finite factor in 0..64; 0 disables ambient light
  * @return a new lighting value
  * @throws IllegalArgumentException if value is outside the finite range
  */
 public RenderLighting ambient(double value){return new RenderLighting(value,sunFactor,blockFactor,sunDirection,lights);}
 /**
  * Sets the inherited sunlight multiplier. Its direction follows rendered time unless overridden.
  * @param value finite factor in 0..64; 0 disables sunlight
  * @return a new lighting value
  * @throws IllegalArgumentException if value is outside the finite range
  */
 public RenderLighting sun(double value){return new RenderLighting(ambientFactor,value,blockFactor,sunDirection,lights);}
 /**
  * Scales both captured block illumination and coloured block emitters. Explicit virtual lights
  * are independent of this control.
  * @param value finite factor in 0..64; 0 disables inherited block lighting
  * @return a new lighting value
  * @throws IllegalArgumentException if value is outside the finite range
  */
 public RenderLighting block(double value){return new RenderLighting(ambientFactor,sunFactor,value,sunDirection,lights);}
 /**
  * Overrides the direction toward the sun in world coordinates. The vector is normalized.
  * @param x world X component
  * @param y world Y component
  * @param z world Z component
  * @return a new lighting value with fixed sun direction
  * @throws IllegalArgumentException if the vector is nonfinite or zero
  */
 public RenderLighting sunDirection(double x,double y,double z){return new RenderLighting(ambientFactor,sunFactor,blockFactor,new LightDirection(x,y,z),lights);}
 /**
  * Adds an explicit point or spot light without changing the existing list.
  * @param light immutable light to append
  * @return a new lighting value
  * @throws IllegalArgumentException for unsupported light implementations, more than 16 lights or more than eight shadow lights
  * @throws NullPointerException if light is null
  */
 public RenderLighting add(VirtualLight light){var next=new ArrayList<>(lights);next.add(Objects.requireNonNull(light));return new RenderLighting(ambientFactor,sunFactor,blockFactor,sunDirection,next);}
 /**
  * Reports whether this value exactly preserves inherited world lighting.
  * @return true for factors 1, automatic sun direction and no explicit lights
  */
 public boolean isWorld(){return ambientFactor==1&&sunFactor==1&&blockFactor==1&&sunDirection==null&&lights.isEmpty();}
}
