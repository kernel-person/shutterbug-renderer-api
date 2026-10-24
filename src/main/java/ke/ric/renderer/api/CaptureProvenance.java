package ke.ric.renderer.api;
import java.time.Instant;
import java.util.*;
/**
 * Immutable facts from the original capture, retained unchanged by scene derivation and camera
 * reuse. Rendered time/weather overrides do not rewrite these facts. Compare
 * {@link RenderedFrame#effectiveCamera()} with {@code sourceCamera} to inspect a reframed camera.
 *
 * <p>A batched capture covers an interval, not one instant. Monotonic nanosecond values are useful
 * for ages and durations only in the same server process; {@code capturedAt} provides a wall-clock
 * label. Camera biome and precipitation describe the source camera's captured environment.</p>
 *
 * @param worldId stable Bukkit world UUID
 * @param worldName captured world name
 * @param worldEnvironment captured world environment identifier
 * @param sourceCamera original source camera, with angles in radians
 * @param capturedAt wall-clock capture timestamp
 * @param startedNanos monotonic time at the start of world evidence capture
 * @param completedNanos monotonic time at the end of world evidence capture
 * @param fullTime captured world time including elapsed days
 * @param timeOfDay captured day ticks in 0..23999
 * @param weather captured local precipitation, including rain versus snow
 * @param thundering captured thunder state
 * @param cameraBiome captured camera biome identifier
 */
public record CaptureProvenance(UUID worldId,String worldName,String worldEnvironment,SceneCamera sourceCamera,Instant capturedAt,long startedNanos,long completedNanos,long fullTime,long timeOfDay,Weather weather,boolean thundering,String cameraBiome) {
 /**
  * Constructs a validated immutable value.
  * @throws IllegalArgumentException for a reversed interval or invalid time of day
  * @throws NullPointerException if a reference component is null
  */
 public CaptureProvenance {Objects.requireNonNull(worldId,"worldId");Objects.requireNonNull(worldName,"worldName");Objects.requireNonNull(worldEnvironment,"worldEnvironment");Objects.requireNonNull(sourceCamera,"sourceCamera");Objects.requireNonNull(capturedAt,"capturedAt");Objects.requireNonNull(weather,"weather");Objects.requireNonNull(cameraBiome,"cameraBiome");if(completedNanos-startedNanos<0)throw new IllegalArgumentException("capture interval must be nonnegative");if(timeOfDay<0||timeOfDay>=24000)throw new IllegalArgumentException("time of day must be 0..23999");}
}
