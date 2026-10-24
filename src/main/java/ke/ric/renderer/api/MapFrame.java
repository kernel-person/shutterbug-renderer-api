package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Immutable 128×128 Minecraft map palette plus the original frame that produced it. Stream sinks
 * receive an aspect-fitted image with black letterboxing, without allocating or saving map items.
 * The constructor and palette accessor copy bytes, so callers cannot change a published frame.
 */
public final class MapFrame {
 private final byte[] palette; private final RenderedFrame source;
 /**
  * Copies exactly one map's palette and retains immutable source evidence.
  * @param palette exactly 16384 row-major Minecraft map-palette bytes
  * @param source source frame, including original passes and final colour pixels
  * @throws IllegalArgumentException if palette has the wrong size
  * @throws NullPointerException if palette or source is null
  */
 public MapFrame(byte[] palette,RenderedFrame source){if(palette.length!=16384)throw new IllegalArgumentException("palette must be 128x128");this.palette=palette.clone();this.source=Objects.requireNonNull(source);}
 /**
  * Returns a defensive copy of the row-major 128×128 palette.
  * @return exactly 16384 map-palette bytes
  */
 public byte[] palette(){return palette.clone();}
 /**
  * Returns original native pass evidence and final composed colour pixels.
  * @return the immutable frame from which this palette was converted
  */
 public RenderedFrame sourceFrame(){return source;}
 /**
  * Returns the source camera sample time, not the palette-conversion time.
  * @return a System.nanoTime() timestamp within this server process
  */
 public long cameraCapturedNanos(){return source.cameraCapturedNanos();}
 /**
  * Returns the original world-evidence time; scene reuse does not renew it.
  * @return a System.nanoTime() timestamp within this server process
  */
 public long worldCapturedNanos(){return source.worldCapturedNanos();}
 /**
  * Returns the source frame's publication-admission time, not this sink's callback time.
  * @return a System.nanoTime() timestamp within this server process
  */
 public long publishedNanos(){return source.publishedNanos();}
}
