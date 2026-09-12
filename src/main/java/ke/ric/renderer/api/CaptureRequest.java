package ke.ric.renderer.api;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Describes an asynchronous Paper-world capture centered on a camera location.
 *
 * @param camera camera position and world; defensively cloned
 * @param radius finite positive capture radius in blocks
 * @param settings output dimensions and rendering intent
 * @param chunksPerTick maximum chunks whose state may be read in one server tick
 */
public record CaptureRequest(Location camera, double radius, RenderSettings settings, int chunksPerTick) {
    public CaptureRequest { camera=Objects.requireNonNull(camera).clone(); settings=Objects.requireNonNull(settings); if(!Double.isFinite(radius)||radius<=0||chunksPerTick<=0)throw new IllegalArgumentException("invalid capture request"); }
    @Override public Location camera(){return camera.clone();}
}
