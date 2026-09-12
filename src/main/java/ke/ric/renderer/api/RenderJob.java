package ke.ric.renderer.api;

import java.util.List;import java.util.Objects;

/**
 * One immutable submission joining a scene, render settings, and selected registered assets.
 *
 * @param version wire-contract version; v1 requires {@code 1}
 * @param scene captured or manually built scene owned by the submitting client
 * @param settings output and resource settings
 * @param assetHashes content hashes returned by active {@link AssetHandle} instances
 */
public record RenderJob(int version,Scene scene,RenderSettings settings,List<String> assetHashes) {
    public RenderJob(int version,Scene scene,RenderSettings settings){this(version,scene,settings,List.of());}
    public RenderJob {if(version!=1)throw new IllegalArgumentException("unsupported render job version");scene=Objects.requireNonNull(scene,"scene");settings=Objects.requireNonNull(settings,"settings");assetHashes=List.copyOf(assetHashes==null?List.of():assetHashes);}
}
