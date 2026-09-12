package ke.ric.renderer.sample;

import ke.ric.renderer.api.AssetHandle;
import ke.ric.renderer.api.AssetPack;
import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.CaptureRequest;
import ke.ric.renderer.api.ChunkSectionState;
import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderProgress;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderStatus;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RenderTimings;
import ke.ric.renderer.api.RendererCapabilities;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.RendererService;
import ke.ric.renderer.api.Scene;
import ke.ric.renderer.api.SceneBuilder;
import ke.ric.renderer.api.SceneEntity;
import ke.ric.renderer.api.SceneStructure;
import ke.ric.renderer.api.VisibilityStatistics;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererSampleWorkflowTest {
    @Test
    void fakeServiceCompletesColdNormalAndManualExtremeThroughRealConverters() {
        FakeClient fake = new FakeClient(allCapabilities());
        RendererClient client = new FakeService(fake).createClient(null);
        QueueExecutor conversions = new QueueExecutor();
        RenderProgressPoller poller = new RenderProgressPoller(new FakeScheduler(), ignored -> {});
        RendererSampleWorkflow workflow = new RendererSampleWorkflow(client, poller, conversions);

        FakeTask coldTask = new FakeTask();
        fake.nextTask = coldTask;
        CompletionStage<RendererSampleWorkflow.ConversionEvidence> cold =
                workflow.captureCold128(new Location(null, 1, 70, 3));
        assertFalse(cold.toCompletableFuture().isDone());
        assertNotNull(fake.captureRequest);
        assertEquals(128, fake.captureRequest.settings().width());
        assertEquals(128, fake.captureRequest.settings().height());
        assertEquals(RenderSettings.Profile.NORMAL, fake.captureRequest.settings().profile());
        assertEquals(EnumSet.allOf(Attachment.class),
                fake.captureRequest.settings().attachments());

        Scene capturedScene = new FakeScene(128, 128);
        fake.capture.complete(capturedScene);
        assertEquals(RenderSettings.Profile.NORMAL, fake.jobs.getFirst().settings().profile());
        coldTask.completion.complete(result(128, 128, true, true));
        assertFalse(cold.toCompletableFuture().isDone(),
                "PNG/map conversion must remain on the supplied asynchronous executor");
        conversions.runNext();

        RendererSampleWorkflow.ConversionEvidence coldEvidence =
                cold.toCompletableFuture().getNow(null);
        assertNotNull(coldEvidence);
        assertSame(capturedScene, coldEvidence.scene());
        assertEquals(128, coldEvidence.frame().width());
        assertEquals(RenderSettings.Profile.NORMAL, coldEvidence.settings().profile());
        assertEquals(128 * 128, coldEvidence.mapColors().length);
        assertEquals(1, coldEvidence.mapTileCount());
        assertTrue(coldEvidence.depthValidated());
        assertTrue(coldEvidence.objectIdValidated());
        assertTrue(startsWithPngSignature(coldEvidence.png()));

        FakeTask manualTask = new FakeTask();
        fake.nextTask = manualTask;
        CompletionStage<RendererSampleWorkflow.ConversionEvidence> manual =
                workflow.renderManualExtreme();
        FakeSceneBuilder builder = fake.lastBuilder;
        assertEquals(64, builder.width);
        assertEquals(64, builder.height);
        assertEquals(0, builder.minHeight);
        assertEquals(16, builder.maxHeight);
        assertEquals(List.of("0,0"), builder.requiredChunks);
        assertEquals(List.of("0,0,0=PALETTED"), builder.sections);
        assertEquals(1, builder.blocks);
        assertEquals(RenderSettings.Profile.EXTREME, fake.jobs.getLast().settings().profile());

        manualTask.completion.complete(result(64, 64, true, true));
        assertFalse(manual.toCompletableFuture().isDone());
        conversions.runNext();
        RendererSampleWorkflow.ConversionEvidence manualEvidence =
                manual.toCompletableFuture().getNow(null);
        assertSame(builder.builtScene, manualEvidence.scene());
        assertEquals(64, manualEvidence.frame().width());
        assertEquals(RenderSettings.Profile.EXTREME, manualEvidence.settings().profile());
        assertEquals(64 * 64, manualEvidence.mapColors().length);
        assertEquals(0, manualEvidence.mapTileCount());
        assertTrue(startsWithPngSignature(manualEvidence.png()));
    }

    @Test
    void optionalAttachmentsAreOmittedAndUnsupportedManualProfileIsTyped() {
        RendererCapabilities limited = new RendererCapabilities(
                "test", "test", true, false,
                Set.of(RenderSettings.Profile.NORMAL), Set.of());
        FakeClient client = new FakeClient(limited);
        RendererSampleWorkflow workflow = new RendererSampleWorkflow(client,
                new RenderProgressPoller(new FakeScheduler(), ignored -> {}), Runnable::run);
        FakeTask coldTask = new FakeTask();
        client.nextTask = coldTask;

        CompletionStage<RendererSampleWorkflow.ConversionEvidence> cold =
                workflow.captureCold128(new Location(null, 0, 64, 0));
        client.capture.complete(new FakeScene(128, 128));
        assertTrue(client.jobs.getFirst().settings().attachments().isEmpty());
        coldTask.completion.complete(result(128, 128, false, false));
        RendererSampleWorkflow.ConversionEvidence evidence =
                cold.toCompletableFuture().getNow(null);
        assertNotNull(evidence);
        assertFalse(evidence.depthValidated());
        assertFalse(evidence.objectIdValidated());

        CompletionException failure = assertThrows(CompletionException.class,
                () -> workflow.renderManualExtreme().toCompletableFuture().join());
        assertEquals(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                ((RendererException) failure.getCause()).code());
        assertTrue(workflow.capabilities().messages().stream()
                .anyMatch(message -> message.contains("EXTREME")));
    }

    @Test
    void coldCaptureRejectsAFrameThatDoesNotMatchTheRequested128Dimensions() {
        FakeClient client = new FakeClient(allCapabilities());
        RendererSampleWorkflow workflow = new RendererSampleWorkflow(client,
                new RenderProgressPoller(new FakeScheduler(), ignored -> {}), Runnable::run);
        FakeTask task = new FakeTask();
        client.nextTask = task;

        CompletionStage<RendererSampleWorkflow.ConversionEvidence> capture =
                workflow.captureCold128(new Location(null, 0, 64, 0));
        client.capture.complete(new FakeScene(128, 128));
        task.completion.complete(result(64, 64, true, true));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> capture.toCompletableFuture().join());
        assertTrue(failure.getCause().getMessage().contains("128x128"),
                failure.getCause().getMessage());
    }

    @Test
    void hermeticFakeProvesCancelledTimeoutAndSynchronousQueueFullCodes() {
        FakeClient client = new FakeClient(allCapabilities());
        RendererSampleWorkflow workflow = new RendererSampleWorkflow(client,
                new RenderProgressPoller(new FakeScheduler(), ignored -> {}), Runnable::run);
        Scene scene = new FakeScene(64, 64);

        FakeTask cancelled = new FakeTask();
        client.nextTask = cancelled;
        CompletionStage<Void> cancellation = workflow.proveCancellation(scene);
        assertTrue(cancelled.cancelCalled);
        assertTrue(cancellation.toCompletableFuture().isDone());
        assertFalse(cancellation.toCompletableFuture().isCompletedExceptionally());

        FakeTask timedOut = new FakeTask();
        timedOut.completion.completeExceptionally(new RendererException(
                RenderFailureCode.TIMEOUT, "fake deadline"));
        client.nextTask = timedOut;
        CompletionStage<Void> timeout = workflow.proveTinyTimeout(scene);
        assertEquals(Duration.ofNanos(1), client.jobs.getLast().settings().timeout());
        assertTrue(timeout.toCompletableFuture().isDone());
        assertFalse(timeout.toCompletableFuture().isCompletedExceptionally());

        client.submitFailure = new RendererException(
                RenderFailureCode.QUEUE_FULL, "fake bounded queue");
        assertEquals(RenderFailureCode.QUEUE_FULL, workflow.proveQueueFull(scene));
    }

    private static RendererCapabilities allCapabilities() {
        return new RendererCapabilities("test", "test", true, false,
                EnumSet.allOf(RenderSettings.Profile.class), EnumSet.allOf(Attachment.class));
    }

    private static RenderResult result(
            int width, int height, boolean includeDepth, boolean includeObjectIds) {
        int pixels = width * height;
        byte[] rgba = new byte[pixels * 4];
        for (int index = 0; index < pixels; index++) {
            rgba[index * 4] = 12;
            rgba[index * 4 + 1] = 34;
            rgba[index * 4 + 2] = 56;
            rgba[index * 4 + 3] = (byte) 255;
        }
        return new RenderResult(width, height, rgba,
                includeDepth ? new float[pixels] : null, null, null,
                includeObjectIds ? new int[pixels] : null,
                VisibilityStatistics.empty(pixels),
                new RenderTimings(1, 2, 3, 4, 5, 6, 7, 8));
    }

    private static boolean startsWithPngSignature(byte[] png) {
        return png.length > 8 && png[0] == (byte) 0x89 && png[1] == 0x50
                && png[2] == 0x4e && png[3] == 0x47;
    }

    private static final class FakeService implements RendererService {
        private final FakeClient client;

        private FakeService(FakeClient client) {
            this.client = client;
        }

        @Override public RendererClient createClient(Plugin owner) { return client; }
        @Override public RendererCapabilities capabilities() { return client.capabilities; }
    }

    private static final class FakeClient implements RendererClient {
        private final RendererCapabilities capabilities;
        private CompletableFuture<Scene> capture = new CompletableFuture<>();
        private CaptureRequest captureRequest;
        private FakeTask nextTask;
        private RendererException submitFailure;
        private final List<RenderJob> jobs = new ArrayList<>();
        private FakeSceneBuilder lastBuilder;

        private FakeClient(RendererCapabilities capabilities) {
            this.capabilities = capabilities;
        }

        @Override public boolean active() { return true; }
        @Override public RendererCapabilities capabilities() { return capabilities; }
        @Override public SceneBuilder newSceneBuilder() {
            lastBuilder = new FakeSceneBuilder();
            return lastBuilder;
        }
        @Override public CompletionStage<Scene> capture(CaptureRequest request) {
            captureRequest = request;
            return capture;
        }
        @Override public AssetHandle registerAsset(AssetPack pack) {
            throw new UnsupportedOperationException();
        }
        @Override public RenderTask submit(RenderJob job) {
            jobs.add(job);
            if (submitFailure != null) {
                RendererException failure = submitFailure;
                submitFailure = null;
                throw failure;
            }
            FakeTask task = nextTask;
            nextTask = null;
            if (task == null) throw new AssertionError("test did not supply a render task");
            return task;
        }
        @Override public void close() {}
    }

    private static final class FakeSceneBuilder implements SceneBuilder {
        private int width;
        private int height;
        private int minHeight;
        private int maxHeight;
        private int blocks;
        private final List<String> requiredChunks = new ArrayList<>();
        private final List<String> sections = new ArrayList<>();
        private Scene builtScene;

        @Override public SceneBuilder dimensions(int width, int height) {
            this.width = width; this.height = height; return this;
        }
        @Override public SceneBuilder camera(double x, double y, double z, double yaw,
                                             double pitch, double fov) { return this; }
        @Override public SceneBuilder world(String environment, String name, long time,
                                            int minHeight, int maxHeight) {
            this.minHeight = minHeight; this.maxHeight = maxHeight; return this;
        }
        @Override public SceneBuilder weather(boolean raining, boolean snowy) { return this; }
        @Override public SceneBuilder block(int x, int y, int z, String state,
                                            int blockLight, int skyLight, String biome) {
            blocks++; return this;
        }
        @Override public SceneBuilder entity(SceneEntity entity) { return this; }
        @Override public SceneBuilder structure(SceneStructure structure) { return this; }
        @Override public SceneBuilder chunkSection(int x, int y, int z, boolean present) {
            sections.add(x + "," + y + "," + z + "=" + present); return this;
        }
        @Override public SceneBuilder chunkSection(
                int x, int y, int z, ChunkSectionState state) {
            sections.add(x + "," + y + "," + z + "=" + state); return this;
        }
        @Override public SceneBuilder requireChunk(int x, int z) {
            requiredChunks.add(x + "," + z); return this;
        }
        @Override public Scene build() {
            builtScene = new FakeScene(width, height);
            return builtScene;
        }
    }

    private record FakeScene(int width, int height) implements Scene {
        @Override public int version() { return 1; }
    }

    private static final class FakeTask implements RenderTask {
        private final CompletableFuture<RenderResult> completion = new CompletableFuture<>();
        private boolean cancelCalled;

        @Override public UUID id() { return new UUID(0, 2); }
        @Override public RenderStatus status() { return RenderStatus.QUEUED; }
        @Override public RenderProgress progress() {
            return new RenderProgress(RenderProgress.Phase.QUEUED, 0);
        }
        @Override public boolean cancel() {
            cancelCalled = true;
            return completion.completeExceptionally(new RendererException(
                    RenderFailureCode.CANCELLED, "fake cancellation"));
        }
        @Override public CompletionStage<RenderResult> completion() { return completion; }
    }

    private static final class FakeScheduler implements RenderProgressPoller.Scheduler {
        @Override public RenderProgressPoller.Cancellable repeat(Runnable task) {
            return () -> {};
        }
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove().run(); }
    }
}
