package ke.ric.renderer.api;
/**
 * Primary-camera visibility policy for selected entity layers. This does not remove lighting or
 * shadow geometry. Request-level exclusions and viewer privacy remain authoritative in either mode.
 */
public enum Occlusion {
 /** Default: walls and other captured entities can obscure selected entities. */ RESPECT_SCENE,
 /** Explicit Xray: other geometry does not hide selected entities; selected entities still obscure each other. */ SELECTED_ONLY
}
