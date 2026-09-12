package ke.ric.renderer.api;

import java.util.List;
import java.util.Objects;

/** One complete entity render target, including its independent world placement and visual evidence. */
public record ResolvedRenderTarget(ResolvedTransform transform,
                                   double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                                   List<ResolvedMesh> meshes,int fallbackArgb,int tintRgb,boolean translucent) {
    /** Creates an unplaced target using inferred translucency and the standard fallback color. */
    public ResolvedRenderTarget(List<ResolvedMesh> meshes){this(meshes,0xffff00ff,0,isTranslucent(meshes));}
    /** Creates an unplaced target; {@link SceneEntity} applies its world placement when selected. */
    public ResolvedRenderTarget(List<ResolvedMesh> meshes,int fallbackArgb,int tintRgb,boolean translucent){this(new ResolvedTransform(0,0,0,0),0,0,0,0,0,0,meshes,fallbackArgb,tintRgb,translucent);}
    public ResolvedRenderTarget {transform=Objects.requireNonNull(transform,"transform");meshes=List.copyOf(meshes);if((tintRgb>>>24)!=0)throw new IllegalArgumentException("tint must be RGB");if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("invalid target bounds");}
    ResolvedRenderTarget withPlacement(ResolvedTransform value,double a,double b,double c,double d,double e,double f){return new ResolvedRenderTarget(value,a,b,c,d,e,f,meshes,fallbackArgb,tintRgb,translucent);}
    private static boolean isTranslucent(List<ResolvedMesh> meshes){return meshes.stream().flatMap(mesh->mesh.triangles().stream()).anyMatch(triangle->triangle.material().alphaMode()==ResolvedMaterial.AlphaMode.BLEND);}
}
