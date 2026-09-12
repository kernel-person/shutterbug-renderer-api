package ke.ric.renderer.api;

import java.util.Objects;

/**
 * One namespaced structure key and finite inclusive world-space bounds supplied to a manual scene.
 */
public record SceneStructure(String key,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
    public SceneStructure { Objects.requireNonNull(key,"key");if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("invalid structure bounds"); }
}
