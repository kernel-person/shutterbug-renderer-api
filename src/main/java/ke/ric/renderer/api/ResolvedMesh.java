package ke.ric.renderer.api;

import java.util.List;

/** Baked mesh; every triangle retains its exact material and texture evidence. */
public record ResolvedMesh(int declaredTextureWidth,int declaredTextureHeight,List<ResolvedTriangle> triangles) {
    public ResolvedMesh {if(declaredTextureWidth<1||declaredTextureWidth>0xffff||declaredTextureHeight<1||declaredTextureHeight>0xffff)throw new IllegalArgumentException("invalid declared texture dimensions");triangles=List.copyOf(triangles);if(triangles.isEmpty())throw new IllegalArgumentException("resolved mesh must contain triangles");ResolvedMaterial first=triangles.getFirst().material();if(triangles.stream().map(ResolvedTriangle::material).anyMatch(material->material.alphaMode()!=first.alphaMode()||material.doubleSided()!=first.doubleSided()))throw new IllegalArgumentException("v1.2 selects alpha mode and sidedness per mesh");}
}
