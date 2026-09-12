package ke.ric.renderer.api;

import java.util.Objects;

/** One fully baked engine-neutral triangle. */
public final class ResolvedTriangle {
    /** Optional source-face orientation retained for lighting and culling semantics. */
    public enum Face { UNSPECIFIED,DOWN,UP,SOUTH,NORTH,WEST,EAST }
    private final float[] positions,uvs,normal;private final ResolvedMaterial material;private final int tintIndex,sampleWidth,sampleHeight;private final boolean shade;private final Face face;
    public ResolvedTriangle(float[] positions,float[] uvs,float[] normal,ResolvedMaterial material,int tintIndex,int sampleWidth,int sampleHeight,boolean shade,Face face){this.positions=copy(positions,9,"positions");this.uvs=copy(uvs,6,"uvs");this.normal=copy(normal,3,"normal");for(float value:this.positions)finite(value);for(float value:this.uvs)finite(value);for(float value:this.normal)finite(value);this.material=Objects.requireNonNull(material,"material");if(tintIndex< -1||sampleWidth<1||sampleHeight<1||sampleWidth>0xffff||sampleHeight>0xffff)throw new IllegalArgumentException("invalid triangle metadata");this.tintIndex=tintIndex;this.sampleWidth=sampleWidth;this.sampleHeight=sampleHeight;this.shade=shade;this.face=Objects.requireNonNull(face,"face");}
    private static float[] copy(float[] value,int length,String name){Objects.requireNonNull(value,name);if(value.length!=length)throw new IllegalArgumentException(name+" length");return value.clone();}private static void finite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("triangle values must be finite");}
    public float[] positions(){return positions.clone();}public float[] uvs(){return uvs.clone();}public float[] normal(){return normal.clone();}public ResolvedMaterial material(){return material;}public int tintIndex(){return tintIndex;}public int sampleWidth(){return sampleWidth;}public int sampleHeight(){return sampleHeight;}public boolean shade(){return shade;}public Face face(){return face;}
}
