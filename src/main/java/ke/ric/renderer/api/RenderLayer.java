package ke.ric.renderer.api;
import java.util.Objects;
/**
 * One immutable pass in a {@link RenderRecipe}. Use {@link #scene()} for a full-scene base and
 * {@link #entities(EntitySelector)} for a selected-entity overlay. Every pass derives independently
 * from the same original capture; a preceding pass never changes a later pass's source evidence.
 *
 * <p>Unspecified content, environment, appearance and lighting inherit the request. Content can
 * only narrow the request's selection. Environment and appearance inherit individual unspecified
 * fields; explicit lighting replaces the complete light setup. Capture exclusions and viewer
 * privacy remain authoritative in every pass.</p>
 *
 * <p>Entity layers respect scene occlusion by default. Use {@link Occlusion#SELECTED_ONLY}
 * explicitly for Xray visibility; selected entities still obscure each other. Primary-camera
 * selection does not itself remove hidden geometry's lighting or shadows. Layer colour is applied
 * to rendered pixels first, then brightness, then opacity during ordered composition.</p>
 *
 * @param entityOnly whether only selected entity-owned pixels contribute to the final composition
 * @param selector predicate over copied entity metadata
 * @param brightness finite nonnegative post-render RGB multiplier; 1 preserves brightness
 * @param opacity composition opacity in 0..1; 1 is fully opaque
 * @param content additional selection, or null to inherit request content
 * @param environment per-field overrides, or null to inherit request environment
 * @param appearance per-field overrides, or null to inherit request appearance
 * @param lighting complete replacement lighting, or null to inherit request lighting
 * @param occlusion visibility of selected entities relative to other captured geometry
 * @param color pixel colour treatment, applied before brightness and opacity
 */
public record RenderLayer(boolean entityOnly,EntitySelector selector,double brightness,double opacity,SceneContent content,RenderEnvironment environment,RenderAppearance appearance,RenderLighting lighting,Occlusion occlusion,LayerColor color) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException for invalid brightness or opacity
  * @throws NullPointerException if selector, occlusion or color is null
  */
 public RenderLayer {Objects.requireNonNull(selector,"selector");Objects.requireNonNull(occlusion,"occlusion");Objects.requireNonNull(color,"color");if(!Double.isFinite(brightness)||brightness<0)throw new IllegalArgumentException("brightness must be finite and nonnegative");if(!Double.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("opacity must be 0..1");}
 /**
  * Creates a layer with inherited request controls, original colours and normal scene occlusion.
  * @param entityOnly whether only selected entity-owned pixels contribute
  * @param selector entity selection over copied metadata
  * @param brightness finite nonnegative post-render multiplier
  * @param opacity composition opacity in 0..1
  */
 public RenderLayer(boolean entityOnly,EntitySelector selector,double brightness,double opacity){this(entityOnly,selector,brightness,opacity,null,null,null,null,Occlusion.RESPECT_SCENE,LayerColor.original());}
 /**
  * Creates a full-scene layer with original colours, brightness 1 and opacity 1.
  * @return a layer inheriting request controls
  */
 public static RenderLayer scene(){return new RenderLayer(false,EntitySelector.all(),1,1);}
 /**
  * Creates a selected-entity layer that respects other scene geometry by default. Native object-ID
  * evidence determines which pixels belong to the selected entities.
  * @param selector entity selection over copied metadata
  * @return an entity layer with original colours, brightness 1 and opacity 1
  */
 public static RenderLayer entities(EntitySelector selector){return new RenderLayer(true,selector,1,1);}
 /**
  * Sets a post-render RGB multiplier, after colour treatment and before opacity. This differs
  * from exposure, which changes renderer appearance, and from light intensity, which lights surfaces.
  * @param value finite multiplier at least 0; default 1
  * @return a new layer with this setting
  */
 public RenderLayer brightness(double value){return new RenderLayer(entityOnly,selector,value,opacity,content,environment,appearance,lighting,occlusion,color);}
 /**
  * Sets how strongly this layer is composited over earlier layers.
  * @param value finite opacity from 0 (invisible) to 1 (opaque)
  * @return a new layer with this setting
  */
 public RenderLayer opacity(double value){return new RenderLayer(entityOnly,selector,brightness,value,content,environment,appearance,lighting,occlusion,color);}
 /**
  * Narrows request content for this pass without changing the original capture. Removed geometry
  * is unavailable to camera, reflection and shadow rays. Use entity selection and occlusion to
  * restrict camera output while retaining the surrounding scene geometry.
  * @param value selection to intersect with request content, or null to inherit
  * @return a new layer with this setting
  */
 public RenderLayer content(SceneContent value){return new RenderLayer(entityOnly,selector,brightness,opacity,value,environment,appearance,lighting,occlusion,color);}
 /**
  * Sets per-field rendered time/weather overrides for this pass. Unset fields inherit the request.
  * @param value overrides, or null to inherit
  * @return a new layer with this setting
  */
 public RenderLayer environment(RenderEnvironment value){return new RenderLayer(entityOnly,selector,brightness,opacity,content,value,appearance,lighting,occlusion,color);}
 /**
  * Sets per-field appearance overrides for this pass, such as renderer exposure.
  * @param value overrides, or null to inherit
  * @return a new layer with this setting
  */
 public RenderLayer appearance(RenderAppearance value){return new RenderLayer(entityOnly,selector,brightness,opacity,content,environment,value,lighting,occlusion,color);}
 /**
  * Replaces the complete light setup for this pass. This value does not merge light lists.
  * @param value lighting setup, or null to inherit request lighting
  * @return a new layer with this setting
  */
 public RenderLayer lighting(RenderLighting value){return new RenderLayer(entityOnly,selector,brightness,opacity,content,environment,appearance,value,occlusion,color);}
 /**
  * Selects whether other captured geometry occludes the selected entities. Selected entities
  * always occlude each other; lighting geometry remains available.
  * @param value RESPECT_SCENE by default, or explicit SELECTED_ONLY for Xray
  * @return a new layer with this setting
  */
 public RenderLayer occlusion(Occlusion value){return new RenderLayer(entityOnly,selector,brightness,opacity,content,environment,appearance,lighting,value,color);}
 /**
  * Sets the RGB treatment applied before this layer's brightness and opacity.
  * @param value original colour, multiplicative tint or solid colour
  * @return a new layer with this setting
  */
 public RenderLayer color(LayerColor value){return new RenderLayer(entityOnly,selector,brightness,opacity,content,environment,appearance,lighting,occlusion,value);}
}
