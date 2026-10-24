package ke.ric.renderer.api;

import java.util.Objects;

/** One fully baked engine-neutral triangle. */
public final class ResolvedTriangle {
    /** Optional source-face orientation retained for lighting and culling semantics. */
    public enum Face {
        /** No source-face orientation is supplied. */
        UNSPECIFIED,
        /** Down-facing source face. */
        DOWN,
        /** Up-facing source face. */
        UP,
        /** South-facing source face. */
        SOUTH,
        /** North-facing source face. */
        NORTH,
        /** West-facing source face. */
        WEST,
        /** East-facing source face. */
        EAST
    }
    private final float[] positions,uvs,normal;private final ResolvedMaterial material;private final int tintIndex,sampleWidth,sampleHeight;private final boolean shade;private final Face face;
    /**
     * Creates a triangle with copied finite vertex/UV/normal arrays and immutable material evidence.
     * @param positions nine XYZ values: three baked vertices in the target's local coordinate system
     * @param uvs six UV values: one pair per vertex
     * @param normal three normal components
     * @param material non-null exact texture and alpha behavior
     * @param tintIndex source tint index, or -1 for none
     * @param sampleWidth texture sampling width in 1..65535 pixels
     * @param sampleHeight texture sampling height in 1..65535 pixels
     * @param shade whether directional shading is enabled
     * @param face non-null source-face orientation, or UNSPECIFIED
     */
    public ResolvedTriangle(float[] positions,float[] uvs,float[] normal,ResolvedMaterial material,int tintIndex,int sampleWidth,int sampleHeight,boolean shade,Face face){this.positions=copy(positions,9,"positions");this.uvs=copy(uvs,6,"uvs");this.normal=copy(normal,3,"normal");for(float value:this.positions)finite(value);for(float value:this.uvs)finite(value);for(float value:this.normal)finite(value);this.material=Objects.requireNonNull(material,"material");if(tintIndex< -1||sampleWidth<1||sampleHeight<1||sampleWidth>0xffff||sampleHeight>0xffff)throw new IllegalArgumentException("invalid triangle metadata");this.tintIndex=tintIndex;this.sampleWidth=sampleWidth;this.sampleHeight=sampleHeight;this.shade=shade;this.face=Objects.requireNonNull(face,"face");}
    private static float[] copy(float[] value,int length,String name){Objects.requireNonNull(value,name);if(value.length!=length)throw new IllegalArgumentException(name+" length");return value.clone();}private static void finite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("triangle values must be finite");}
    /** Returns nine copied XYZ values, one triplet per local-space vertex. */
    public float[] positions(){return positions.clone();}
    /** Returns six copied UV values, one pair per vertex. */
    public float[] uvs(){return uvs.clone();}
    /** Returns a copied three-component normal. */
    public float[] normal(){return normal.clone();}
    /** Returns immutable material and texture evidence. */
    public ResolvedMaterial material(){return material;}
    /** Returns the source tint index, or -1 when no tint index is supplied. */
    public int tintIndex(){return tintIndex;}
    /** Returns texture sampling width in pixels. */
    public int sampleWidth(){return sampleWidth;}
    /** Returns texture sampling height in pixels. */
    public int sampleHeight(){return sampleHeight;}
    /** Returns whether directional shading is enabled. */
    public boolean shade(){return shade;}
    /** Returns the retained source-face orientation, or UNSPECIFIED. */
    public Face face(){return face;}
}
