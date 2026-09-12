package docs.examples;

import ke.ric.renderer.api.MapDither;
import ke.ric.renderer.api.MinecraftMapConverter;
import ke.ric.renderer.api.PngConverter;
import ke.ric.renderer.api.RenderResult;

public final class ConversionExample {
    public static byte[] png(RenderResult frame) { return PngConverter.encode(frame); }
    public static byte[] map(RenderResult frame) { return MinecraftMapConverter.quantize(frame, MapDither.NONE); }
}
