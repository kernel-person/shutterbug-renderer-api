package ke.ric.renderer.api;

/** Dithering strategy used while quantizing RGBA8 output to Minecraft's map palette. */
public enum MapDither {
    /** Quantize each pixel independently. */
    NONE,
    /** Apply an ordered 4x4 Bayer pattern. */
    BAYER,
    /** Diffuse quantization error using Floyd-Steinberg weights. */
    FLOYD_STEINBERG
}
