package ke.ric.renderer.api;

import java.util.Objects;

/** Exact texture and alpha behavior for one resolved mesh. */
public record ResolvedMaterial(ResolvedTexture texture,AlphaMode alphaMode,boolean doubleSided) {
    /** How the renderer interprets source alpha for the material. */
    public enum AlphaMode { BLEND, CUTOUT }
    public ResolvedMaterial {Objects.requireNonNull(texture,"texture");Objects.requireNonNull(alphaMode,"alphaMode");}
}
