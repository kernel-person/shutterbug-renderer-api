package ke.ric.renderer.api;

import java.util.Objects;

/**
 * One namespaced structure key and finite inclusive world-space bounds supplied to a manual scene.
 * @param key non-null namespaced structure identifier
 * @param minX minimum world X bound in blocks
 * @param minY minimum world Y bound in blocks
 * @param minZ minimum world Z bound in blocks
 * @param maxX maximum world X bound in blocks
 * @param maxY maximum world Y bound in blocks
 * @param maxZ maximum world Z bound in blocks
 */
public record SceneStructure(String key,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
    public SceneStructure { Objects.requireNonNull(key,"key");if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("invalid structure bounds"); }
}
