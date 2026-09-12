package ke.ric.renderer.sample;

import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.CaptureRequest;
import ke.ric.renderer.api.ChunkSectionState;
import ke.ric.renderer.api.MapDither;
import ke.ric.renderer.api.MinecraftMapConverter;
import ke.ric.renderer.api.PngConverter;
import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.Scene;
import ke.ric.renderer.api.SceneBuilder;
import org.bukkit.Location;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Complete public-API capture, manual-scene, conversion, and typed-failure examples. */
final class RendererSampleWorkflow {
    private static final int COLD_SIZE = 128;
    private static final int MANUAL_SIZE = 64;
    private static final Duration NORMAL_TIMEOUT = Duration.ofSeconds(45);

    private final RendererClient client;
    private final RenderProgressPoller poller;
    private final Executor conversionExecutor;
    private final SampleCapabilities capabilities;

    RendererSampleWorkflow(RendererClient client, RenderProgressPoller poller,
                           Executor conversionExecutor) {
        this.client = Objects.requireNonNull(client, "client");
        this.poller = Objects.requireNonNull(poller, "poller");
        this.conversionExecutor = Objects.requireNonNull(conversionExecutor,
                "conversionExecutor");
        this.capabilities = SampleCapabilities.inspect(client.capabilities());
    }

    SampleCapabilities capabilities() {
        return capabilities;
    }

    CompletionStage<ConversionEvidence> captureCold128(Location camera) {
        if (!capabilities.coldCaptureAvailable()) {
            return unsupported("NORMAL cold capture is unavailable");
        }
        RenderSettings settings = settings(RenderSettings.Profile.NORMAL,
                COLD_SIZE, COLD_SIZE, capabilities.optionalAttachments(), NORMAL_TIMEOUT);
        CaptureRequest request = new CaptureRequest(camera, 8.0, settings, 1);
        return client.capture(request).thenCompose(scene -> submit(scene, settings));
    }

    CompletionStage<ConversionEvidence> renderManualExtreme() {
        if (!capabilities.manualExtremeAvailable()) {
            return unsupported("EXTREME (HQ4) manual rendering is unavailable");
        }
        try {
            Scene scene = completeManualScene();
            RenderSettings settings = settings(RenderSettings.Profile.EXTREME,
                    MANUAL_SIZE, MANUAL_SIZE, capabilities.optionalAttachments(), NORMAL_TIMEOUT);
            return submit(scene, settings);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    CompletionStage<Void> proveCancellation(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RenderTask task = client.submit(new RenderJob(1, scene,
                settings(RenderSettings.Profile.NORMAL, scene.width(), scene.height(),
                        Set.of(), NORMAL_TIMEOUT)));
        poller.observe(task);
        if (!task.cancel()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("sample render was already terminal"));
        }
        return requireFailure(task, RenderFailureCode.CANCELLED);
    }

    CompletionStage<Void> proveTinyTimeout(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RenderTask task = client.submit(new RenderJob(1, scene,
                settings(RenderSettings.Profile.NORMAL, scene.width(), scene.height(),
                        Set.of(), Duration.ofNanos(1))));
        poller.observe(task);
        return requireFailure(task, RenderFailureCode.TIMEOUT);
    }

    /** Hermetic fake-service evidence only; live sample commands never call this overload. */
    RenderFailureCode proveQueueFull(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        try {
            RenderTask unexpectedlyAdmitted = client.submit(new RenderJob(1, scene,
                    settings(RenderSettings.Profile.NORMAL, scene.width(), scene.height(),
                            Set.of(), NORMAL_TIMEOUT)));
            unexpectedlyAdmitted.cancel();
            throw new IllegalStateException("fake queue unexpectedly admitted the sample job");
        } catch (RendererException failure) {
            if (failure.code() != RenderFailureCode.QUEUE_FULL) throw failure;
            return failure.code();
        }
    }

    private CompletionStage<ConversionEvidence> submit(Scene scene, RenderSettings settings) {
        RenderTask task = client.submit(new RenderJob(1, scene, settings));
        poller.observe(task);
        return task.completion().thenApplyAsync(
                frame -> convert(scene, frame, settings), conversionExecutor);
    }

    private Scene completeManualScene() {
        SceneBuilder builder = client.newSceneBuilder()
                .dimensions(MANUAL_SIZE, MANUAL_SIZE)
                .camera(0.5, 2.0, 5.0, 0.0, 0.0, Math.toRadians(70.0))
                .world("minecraft:overworld", "renderer-sample-manual", 6_000L, 0, 16)
                .weather(false, false)
                .block(0, 0, 0, "minecraft:stone", 0, 15, "minecraft:plains")
                .requireChunk(0, 0)
                .chunkSection(0, 0, 0, ChunkSectionState.PALETTED);
        return builder.build();
    }

    private static RenderSettings settings(RenderSettings.Profile profile, int width, int height,
                                           Set<Attachment> attachments, Duration timeout) {
        return new RenderSettings(1, profile, width, height, attachments,
                RenderSettings.DEFAULT_MEMORY_LIMIT, RenderSettings.defaultWorkers(), timeout);
    }

    private static ConversionEvidence convert(Scene scene, RenderResult frame,
                                                RenderSettings settings) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(settings, "settings");
        if (frame.width() != settings.width() || frame.height() != settings.height()) {
            throw new IllegalStateException("renderer returned " + frame.width() + "x"
                    + frame.height() + " for requested " + settings.width() + "x"
                    + settings.height());
        }
        Set<Attachment> requestedAttachments = settings.attachments();
        int pixels = Math.multiplyExact(frame.width(), frame.height());
        byte[] firstColor = frame.rgba8();
        if (firstColor.length != Math.multiplyExact(pixels, 4)) {
            throw new IllegalStateException("renderer returned an invalid color length");
        }
        byte originalColor = firstColor[0];
        firstColor[0] ^= 1;
        if (frame.rgba8()[0] != originalColor) {
            throw new IllegalStateException("renderer color output is not caller-owned");
        }

        boolean depthValidated = validateDepth(frame, pixels,
                requestedAttachments.contains(Attachment.DEPTH));
        boolean objectIdValidated = validateObjectIds(frame, pixels,
                requestedAttachments.contains(Attachment.OBJECT_ID));
        byte[] png = PngConverter.encode(frame);
        byte[] mapColors = MinecraftMapConverter.quantize(frame, MapDither.NONE);
        if (mapColors.length != pixels) {
            throw new IllegalStateException("map conversion returned the wrong color length");
        }
        int mapTileCount = 0;
        if (frame.width() % 128 == 0 && frame.height() % 128 == 0) {
            byte[][] tiles = MinecraftMapConverter.tiles(frame, MapDither.NONE);
            int expectedTiles = Math.multiplyExact(frame.width() / 128, frame.height() / 128);
            if (tiles.length != expectedTiles
                    || Arrays.stream(tiles).anyMatch(tile -> tile.length != 128 * 128)) {
                throw new IllegalStateException("map conversion returned invalid 128x128 tiles");
            }
            mapTileCount = tiles.length;
        }
        return new ConversionEvidence(scene, frame, settings, png, mapColors, mapTileCount,
                depthValidated, objectIdValidated);
    }

    private static boolean validateDepth(RenderResult frame, int pixels, boolean requested) {
        float[] first = frame.depth();
        if (!requested) {
            if (first != null) throw new IllegalStateException("unrequested depth was returned");
            return false;
        }
        if (first == null || first.length != pixels) {
            throw new IllegalStateException("requested depth is missing or malformed");
        }
        int original = Float.floatToRawIntBits(first[0]);
        first[0] = Float.intBitsToFloat(original ^ 1);
        if (Float.floatToRawIntBits(frame.depth()[0]) != original) {
            throw new IllegalStateException("depth output is not caller-owned");
        }
        return true;
    }

    private static boolean validateObjectIds(RenderResult frame, int pixels, boolean requested) {
        int[] first = frame.objectIds();
        if (!requested) {
            if (first != null) throw new IllegalStateException("unrequested object IDs were returned");
            return false;
        }
        if (first == null || first.length != pixels) {
            throw new IllegalStateException("requested object IDs are missing or malformed");
        }
        int original = first[0];
        first[0] ^= 1;
        if (frame.objectIds()[0] != original) {
            throw new IllegalStateException("object-ID output is not caller-owned");
        }
        return true;
    }

    private static CompletionStage<Void> requireFailure(
            RenderTask task, RenderFailureCode expected) {
        return task.completion().handle((ignored, failure) -> {
            Throwable cause = unwrap(failure);
            if (!(cause instanceof RendererException rendererFailure)
                    || rendererFailure.code() != expected) {
                throw new IllegalStateException(
                        "sample expected typed render failure " + expected, cause);
            }
            return null;
        });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> unsupported(String message) {
        return CompletableFuture.failedFuture(new RendererException(
                RenderFailureCode.UNSUPPORTED_CAPABILITY, message));
    }

    record ConversionEvidence(Scene scene, RenderResult frame, RenderSettings settings,
                              byte[] png, byte[] mapColors, int mapTileCount,
                              boolean depthValidated, boolean objectIdValidated) {
        ConversionEvidence {
            Objects.requireNonNull(scene, "scene");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(settings, "settings");
            png = png.clone();
            mapColors = mapColors.clone();
        }

        @Override public byte[] png() { return png.clone(); }
        @Override public byte[] mapColors() { return mapColors.clone(); }
    }
}
