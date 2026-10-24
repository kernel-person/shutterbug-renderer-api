package ke.ric.renderer.api;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Pure image conversion helpers. Run substantial conversions on a bounded worker executor. */
final class RendererImages {
    private RendererImages() {}

    /** Returns a newly allocated image, preserving the source RGBA alpha channel. */
    public static BufferedImage toBufferedImage(RenderResult source) {
        Objects.requireNonNull(source, "source");
        BufferedImage image = new BufferedImage(source.width(), source.height(), BufferedImage.TYPE_INT_ARGB);
        byte[] pixels = source.rgba8();
        for (int y = 0; y < source.height(); y++) {
            checkpoint();
            for (int x = 0; x < source.width(); x++) {
                int i = (y * source.width() + x) * 4;
                image.setRGB(x, y, (pixels[i + 3] & 255) << 24 | (pixels[i] & 255) << 16
                        | (pixels[i + 1] & 255) << 8 | (pixels[i + 2] & 255));
            }
        }
        return image;
    }

    /**
     * Fits color pixels inside the requested canvas, using nearest-neighbor sampling and opaque
     * black aspect-preserving letterboxing. Resized output contains colors only; original
     * attachments and visibility evidence remain available from the original result.
     */
    public static RenderResult fit(RenderResult source, int width, int height) {
        Objects.requireNonNull(source, "source");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("image dimensions must be positive");
        if (source.width() == width && source.height() == height) return source;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int i = 3; i < output.length; i += 4) output[i] = (byte) 255;
        double scale = Math.min((double) width / source.width(), (double) height / source.height());
        int fittedWidth = Math.max(1, Math.min(width, (int) Math.round(source.width() * scale)));
        int fittedHeight = Math.max(1, Math.min(height, (int) Math.round(source.height() * scale)));
        int left = (width - fittedWidth) / 2, top = (height - fittedHeight) / 2;
        byte[] input = source.rgba8();
        for (int y = 0; y < fittedHeight; y++) {
            checkpoint();
            int sourceY = (int) ((long) y * source.height() / fittedHeight);
            for (int x = 0; x < fittedWidth; x++) {
                int sourceX = (int) ((long) x * source.width() / fittedWidth);
                int from = (sourceY * source.width() + sourceX) * 4;
                int to = ((top + y) * width + left + x) * 4;
                System.arraycopy(input, from, output, to, 4);
            }
        }
        return new RenderResult(width, height, output, null, null, null, null,
                VisibilityStatistics.empty(Math.multiplyExact(width, height)), source.timings());
    }

    /** Returns fitted row-major Minecraft map palette bytes without dithering. */
    public static byte[] palette(RenderResult source, int width, int height) {
        return palette(source,width,height,MapOutputOptions.defaults());
    }
    static byte[] palette(RenderResult source,int width,int height,MapOutputOptions options) {
        checkpoint();
        byte[] palette = MinecraftMapConverter.quantize(fit(source, width, height), options.dither());
        checkpoint();
        return palette;
    }

    private static void checkpoint() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}
