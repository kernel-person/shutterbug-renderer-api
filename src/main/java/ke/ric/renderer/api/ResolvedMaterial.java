package ke.ric.renderer.api;

import java.util.Objects;

/**
 * Exact texture and alpha behavior for a resolved mesh; textures are immutable evidence, not live resource lookups.
 * @param texture non-null immutable RGBA8 source texture
 * @param alphaMode non-null source-alpha interpretation
 * @param doubleSided whether both sides of triangles are rendered
 */
public record ResolvedMaterial(ResolvedTexture texture,AlphaMode alphaMode,boolean doubleSided) {
    /** How the renderer interprets source alpha for the material. */
    public enum AlphaMode {
        /** Uses source alpha for blended transparency. */
        BLEND,
        /** Uses source alpha for cutout material coverage. */
        CUTOUT
    }
    public ResolvedMaterial {Objects.requireNonNull(texture,"texture");Objects.requireNonNull(alphaMode,"alphaMode");}
}
