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
    @Override public Location camera(){return camera.clone();}
}
