package ke.ric.renderer.api;

import org.bukkit.Location;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Describes an asynchronous Paper-world capture centered on a camera location.
 *
 * @param camera camera position and world; defensively cloned
 * @param radius finite positive capture radius in blocks
 * @param settings output dimensions and rendering intent
 * @param chunksPerTick maximum chunks whose state may be read in one server tick
 * @param excludedEntityIds entity UUIDs omitted before visual capture; defensively copied
 */
public record CaptureRequest(Location camera, double radius, RenderSettings settings, int chunksPerTick,
                             Set<UUID> excludedEntityIds) {
    public CaptureRequest { camera=Objects.requireNonNull(camera).clone(); settings=Objects.requireNonNull(settings); excludedEntityIds=Set.copyOf(excludedEntityIds); if(!Double.isFinite(radius)||radius<=0||chunksPerTick<=0)throw new IllegalArgumentException("invalid capture request"); }
    /** Captures all renderable entities, preserving the original request default. */
    public CaptureRequest(Location camera, double radius, RenderSettings settings, int chunksPerTick) {
        this(camera, radius, settings, chunksPerTick, Set.of());
    }
    /** Returns a fresh copy of the camera location; changing it cannot alter this request. */
    @Override public Location camera(){return camera.clone();}
    /**
     * Starts a capture with {@link RenderSettings#defaults()}: Classic at 128 by 128 pixels.
     * Uses a 16-block radius, at most two chunks per server tick and no exclusions.
     * Construction copies the location; it does not read or load the world.
     * @param camera camera position/orientation, with the world to capture
     * @return immutable request; required chunks must already be loaded when captured
     */
    public static CaptureRequest at(Location camera) { return at(camera, RenderSettings.defaults()); }
    /**
     * Starts a request with radius 16 blocks, at most two chunks per server tick and no exclusions.
     * Construction copies the location; it does not read or load the world.
     * @param camera camera position/orientation, with the world to capture
     * @param settings render dimensions, quality and resource limits
     * @return immutable request; required chunks must already be loaded when captured
     */
    public static CaptureRequest at(Location camera, RenderSettings settings) { return new CaptureRequest(camera, 16, settings, 2); }
    /**
     * Returns an immutable copy with a finite positive capture radius. Provider limits still apply.
     * @param radiusBlocks finite positive capture radius in world blocks
     * @return copied request with the new radius
     */
    public CaptureRequest withRadius(double radiusBlocks) { return new CaptureRequest(camera, radiusBlocks, settings, chunksPerTick, excludedEntityIds); }
    /**
     * Returns a copy limiting world reads per server tick.
     * @param chunksPerTick positive maximum chunks to read in one tick, subject to provider limits
     * @return copied request with the new per-tick budget
     */
    public CaptureRequest withChunksPerTick(int chunksPerTick) { return new CaptureRequest(camera, radius, settings, chunksPerTick, excludedEntityIds); }
    /**
     * Returns a copy replacing the exclusion set before entity visual capture.
     * @param excludedEntityIds non-null UUID set; copied, and must not contain null
     * @return copied request excluding exactly these UUIDs
     */
    public CaptureRequest excluding(Set<UUID> excludedEntityIds) { return new CaptureRequest(camera, radius, settings, chunksPerTick, excludedEntityIds); }
}
