package ke.ric.renderer.api;
import java.util.*;
/**
 * Immutable rendered-environment overrides. These do not modify the live Bukkit world, captured
 * biome or {@link CaptureProvenance}. Every modifier returns a new value. Unspecified fields
 * inherit the captured scene; in a recipe layer, they first inherit request-level overrides.
 * Rendered time also drives sunlight direction unless lighting explicitly overrides that direction.
 */
public final class RenderEnvironment {
 private final Integer time;private final Weather weather;
 private RenderEnvironment(Integer time,Weather weather){this.time=time;this.weather=weather;}
 /**
  * Inherits captured or manually supplied environment facts. In a layer, unspecified fields
  * inherit the request's environment overrides first.
  * @return a value with no explicit overrides
  */
 public static RenderEnvironment captured(){return new RenderEnvironment(null,null);}
 /**
  * Overrides rendered time of day while preserving the source day and moon phase. It changes
  * rendered lighting/time effects without changing the server clock or captured provenance.
  * @param ticks day ticks in 0..23999; for example, 6000 is midday
  * @return a new environment value
  * @throws IllegalArgumentException if ticks is outside 0..23999
  */
 public RenderEnvironment timeOfDay(int ticks){if(ticks<0||ticks>=24000)throw new IllegalArgumentException("time of day must be 0..23999");return new RenderEnvironment(ticks,weather);}
 /**
  * Overrides rendered precipitation independently of the live world. This does not edit biomes
  * or the captured thunder fact.
  * @param value CLEAR, RAIN or SNOW
  * @return a new environment value
  * @throws NullPointerException if value is null
  */
 public RenderEnvironment weather(Weather value){return new RenderEnvironment(time,Objects.requireNonNull(value,"weather"));}
 /**
  * Returns the requested rendered day tick override.
  * @return ticks in 0..23999, or empty when inherited
  */
 public OptionalInt timeOfDay(){return time==null?OptionalInt.empty():OptionalInt.of(time);}
 /**
  * Returns the requested rendered precipitation override.
  * @return explicit weather, or empty when inherited
  */
 public Optional<Weather> weather(){return Optional.ofNullable(weather);}
 RenderEnvironment inherit(RenderEnvironment request){return new RenderEnvironment(time==null?request.time:time,weather==null?request.weather:weather);}
 /**
  * Reports whether every field inherits source environment evidence.
  * @return true when both time and weather are unspecified
  */
 public boolean isCaptured(){return time==null&&weather==null;}
}
