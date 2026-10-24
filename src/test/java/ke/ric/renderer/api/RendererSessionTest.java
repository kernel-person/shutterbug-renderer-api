package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RendererSessionTest {
    final SdkBukkitTestSupport bukkit = new SdkBukkitTestSupport();
    final FakeClient client = new FakeClient();
    RendererSession session;

    RendererSession start() {
        bukkit.service = service(client);
        session = new RendererSession(bukkit.owner);
        return session;
    }

    @AfterEach void close() { if (session != null) session.close(); }

    @Test void customFieldOfViewAndWorldEvidenceTimestampReachTheResult() {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)), 95);
        assertEquals(95, client.fov);
        client.capture.complete(new Scene() {
            public int version() { return 1; }
            public int width() { return 1; }
            public int height() { return 1; }
            public long worldCapturedNanos() { return -913L; }
        });
        client.task.result.complete(frame());
        assertEquals(-913L, output.join().worldCapturedNanos());
    }

    @Test void compositionCopiesEvidenceAndSubmitsTheDerivedScene() {
        var entity=SceneEntity.builder(new UUID(0,8),"example:marker").build();
        List<SceneEntity> additions=new ArrayList<>(List.of(entity));
        var output=start().captureAndRender(request(Duration.ofSeconds(2)),additions);
        additions.clear();
        Scene captured=scene();
        client.capture.complete(captured);
        assertSame(captured,client.appendSource);
        assertEquals(List.of(entity),client.appended);
        assertSame(client.composed,client.submitted.scene());
        client.task.result.complete(frame());
        assertTrue(output.isDone());
        assertFalse(output.isCompletedExceptionally());
    }

    @Test void compositionFailureSettlesWithoutSubmitting() {
        client.appendFailure=new RendererException(RenderFailureCode.INVALID_REQUEST,"bad exact evidence");
        var output=start().captureAndRender(request(Duration.ofSeconds(2)),List.of(
                SceneEntity.builder(new UUID(0,9),"example:marker").build()));
        client.capture.complete(scene());
        assertSame(client.appendFailure,failure(output));
        assertEquals(0,client.submissions);
    }

    @Test void cancellationDuringCompositionPreventsSubmission() {
        var output=start().captureAndRender(request(Duration.ofSeconds(2)),List.of(
                SceneEntity.builder(new UUID(0,10),"example:marker").build()));
        client.onAppend=()->output.cancel(false);
        client.capture.complete(scene());
        assertTrue(output.isCancelled());
        assertEquals(0,client.submissions);
    }

    @Test void cancellationDuringCapturePreventsSubmission() {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        assertTrue(output.cancel(true));
        assertTrue(client.capture.isCancelled());
        client.capture.complete(scene());
        bukkit.drain();
        assertEquals(0, client.submissions);
    }

    @Test void cancellationAfterCaptureCancelsRender() {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        client.capture.complete(scene());
        assertEquals(1, client.submissions);
        assertTrue(output.cancel(false));
        assertTrue(client.task.cancelled);
    }

    @Test void cancellationBeforeQueuedCaptureContinuationPreventsSubmission() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        worker(() -> client.capture.complete(scene()));
        assertFalse(bukkit.queued.isEmpty());
        worker(() -> output.cancel(false));
        bukkit.drain();
        assertEquals(0, client.submissions);
    }

    @Test void cancellationRacingSubmissionCancelsNewlyReturnedTask() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        client.onSubmit = () -> {
            try { worker(() -> output.cancel(false)); } catch (Exception failure) { throw new AssertionError(failure); }
        };
        client.capture.complete(scene());
        assertTrue(output.isCancelled());
        assertTrue(client.task.cancelled);
    }

    @Test void successfulResultPublishesOnServerThreadAndCannotBeCancelledLater() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        AtomicReference<Thread> callback = new AtomicReference<>();
        output.thenAccept(ignored -> callback.set(Thread.currentThread()));
        client.capture.complete(scene());
        RenderResult frame = frame();
        worker(() -> client.task.result.complete(frame));
        assertFalse(output.isDone());
        bukkit.drain();
        assertSame(frame, output.join());
        assertSame(Thread.currentThread(), callback.get());
        assertFalse(output.cancel(false));
    }

    @Test void synchronousCaptureAndSubmissionFailuresSettleReturnedFuture() {
        start();
        client.captureFailure = new IllegalArgumentException("capture");
        assertSame(client.captureFailure, failure(session.captureAndRender(request(Duration.ofSeconds(2)))));
        client.captureFailure = null;
        client.submitFailure = new IllegalStateException("submit");
        var output = session.captureAndRender(request(Duration.ofSeconds(2)));
        client.capture.complete(scene());
        assertSame(client.submitFailure, failure(output));
    }

    @Test void missingServiceBecomesTypedFutureFailure() {
        start();
        bukkit.service = null;
        assertEquals(RenderFailureCode.PROVIDER_DISABLED,
                ((RendererException) failure(session.captureAndRender(request(Duration.ofSeconds(2))))).code());
    }

    @Test void timeoutCoversCaptureAndCancelsItsFuture() throws Exception {
        var output = start().captureAndRender(request(Duration.ofMillis(40)));
        assertEquals(RenderFailureCode.TIMEOUT, ((RendererException) failureWithin(output)).code());
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> client.capture.get(1, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(client.capture.isCancelled());
    }

    @Test void timeoutAlsoCancelsAnActiveRender() throws Exception {
        var output = start().captureAndRender(request(Duration.ofMillis(40)));
        client.capture.complete(scene());
        assertEquals(RenderFailureCode.TIMEOUT, ((RendererException) failureWithin(output)).code());
        client.task.cancellation.get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(client.task.cancelled);
    }

    @Test void closeAndOwnerDisableSettleOwnedWorkAndReleaseClient() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        bukkit.enabled = false;
        bukkit.fire(new PluginDisableEvent(bukkit.owner));
        assertEquals(RenderFailureCode.CLIENT_CLOSED, ((RendererException) failure(output)).code());
        assertTrue(client.closed);
        assertTrue(client.capture.isCancelled());
        session.close();
        assertThrows(RendererException.class, session::client);
    }

    @Test void unregisterCancelsOldWorkButAllowsRebinding() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        RendererService old = bukkit.service;
        bukkit.fire(new ServiceUnregisterEvent(new RegisteredServiceProvider<>(RendererService.class, old,
                ServicePriority.Normal, bukkit.owner)));
        assertEquals(RenderFailureCode.PROVIDER_DISABLED, ((RendererException) failure(output)).code());
        FakeClient replacement = new FakeClient();
        bukkit.service = service(replacement);
        assertSame(replacement, session.client());
        assertTrue(client.closed);
    }

    @Test void replacementBeforeQueuedResultSuppressesStaleSuccess() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        client.capture.complete(scene());
        worker(() -> client.task.result.complete(frame()));
        FakeClient replacement = new FakeClient();
        bukkit.service = service(replacement);
        bukkit.drain();
        assertEquals(RenderFailureCode.PROVIDER_DISABLED, ((RendererException) failure(output)).code());
        assertSame(replacement, session.client());
    }

    @Test void closeFromInvalidationCallbackPreventsReplacementClientAcquisition() {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        output.whenComplete((ignored, failure) -> session.close());
        AtomicBoolean acquired = new AtomicBoolean();
        bukkit.service = new RendererService() {
            public RendererClient createClient(org.bukkit.plugin.Plugin owner) {
                acquired.set(true);
                return new FakeClient();
            }
            public RendererCapabilities capabilities() { return null; }
        };
        RendererException failure = assertThrows(RendererException.class, session::client);
        assertEquals(RenderFailureCode.CLIENT_CLOSED, failure.code());
        assertFalse(acquired.get());
    }

    @Test void replacementWorkStartedByInvalidationCallbackSurvivesTheOldSweep() {
        start();
        CaptureRequest request = request(Duration.ofMinutes(1));
        List<CompletableFuture<RenderResult>> oldOutputs = new ArrayList<>();
        for (int index = 0; index < 400; index++) oldOutputs.add(session.captureAndRender(request));

        List<CompletableFuture<RenderResult>> replacements = new ArrayList<>();
        AtomicBoolean restarted = new AtomicBoolean();
        for (var output : oldOutputs) output.whenComplete((ignored, failure) -> {
            if (restarted.compareAndSet(false, true)) {
                for (int index = 0; index < 400; index++) replacements.add(session.captureAndRender(request));
            }
        });
        FakeClient replacement = new FakeClient();
        bukkit.service = service(replacement);

        assertSame(replacement, session.client());
        assertTrue(client.closed);
        assertFalse(replacement.closed);
        for (var output : oldOutputs) {
            assertEquals(RenderFailureCode.PROVIDER_DISABLED, ((RendererException) failure(output)).code());
        }
        assertEquals(400, replacements.size());
        for (var output : replacements) {
            assertFalse(output.isDone(), "replacement work must survive invalidation of the old client");
        }
        replacement.captures.forEach(capture -> capture.complete(scene()));
        RenderResult frame = frame();
        replacement.task.result.complete(frame);
        for (var output : replacements) assertSame(frame, output.join());
    }

    @Test void schedulerFailureNeverLeavesOperationPending() throws Exception {
        var output = start().captureAndRender(request(Duration.ofSeconds(2)));
        bukkit.rejectScheduling = true;
        worker(() -> client.capture.complete(scene()));
        assertInstanceOf(RejectedExecutionException.class, failure(output));
    }

    @Test void explicitExecutorRejectsSchedulingFailureButDoesNotDropAcceptedCommands() throws Exception {
        start();
        AtomicBoolean delivered = new AtomicBoolean();
        worker(() -> session.mainThreadExecutor().execute(() -> delivered.set(true)));
        bukkit.service = service(new FakeClient());
        session.client();
        bukkit.drain();
        assertTrue(delivered.get());
        bukkit.rejectScheduling = true;
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        worker(() -> { try { session.mainThreadExecutor().execute(() -> {}); } catch (Throwable failure) { rejected.set(failure); } });
        assertInstanceOf(RejectedExecutionException.class, rejected.get());
    }

    @Test void constructionClientRequestAndCloseRequireServerThread() throws Exception {
        start();
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        for (Runnable operation : new Runnable[]{() -> new RendererSession(bukkit.owner), () -> session.client(),
                () -> session.captureAndRender(request(Duration.ofSeconds(2))), () -> session.close()}) {
            worker(() -> { try { operation.run(); } catch (Throwable failure) { rejected.set(failure); } });
            assertInstanceOf(IllegalStateException.class, rejected.getAndSet(null));
        }
    }

    static void worker(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> { try { action.run(); } catch (Throwable caught) { failure.set(caught); } });
        thread.start(); thread.join(1000);
        assertFalse(thread.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    static Throwable failure(CompletableFuture<?> output) { return assertThrows(CompletionException.class, output::join).getCause(); }
    static Throwable failureWithin(CompletableFuture<?> output) throws Exception {
        return assertThrows(ExecutionException.class, () -> output.get(1, TimeUnit.SECONDS)).getCause();
    }
    static CaptureRequest request(Duration timeout) { return CaptureRequest.at(new Location(null, 0, 64, 0), RenderSettings.classic(1, 1).withTimeout(timeout)); }
    static Scene scene() { return new Scene() { public int version() { return 1; } public int width() { return 1; } public int height() { return 1; } }; }
    static RenderResult frame() { return new RenderResult(1, 1, new byte[4], null, null, null, null, VisibilityStatistics.empty(1), new RenderTimings(0, 0, 0, 0, 0)); }
    static RendererService service(FakeClient client) {
        return new RendererService() {
            public RendererClient createClient(org.bukkit.plugin.Plugin owner) { return client; }
            public RendererCapabilities capabilities() { return null; }
        };
    }
    static final class FakeClient implements RendererClient {
        final CompletableFuture<Scene> capture = new CompletableFuture<>();
        final List<CompletableFuture<Scene>> captures = new ArrayList<>();
        final FakeTask task = new FakeTask();
        boolean closed;
        int submissions;
        double fov;
        RuntimeException captureFailure, submitFailure, appendFailure;
        Scene appendSource;
        final Scene composed=scene();
        List<SceneEntity> appended;
        RenderJob submitted;
        Runnable onSubmit = () -> {};
        Runnable onAppend = () -> {};
        public boolean active() { return !closed; }
        public RendererCapabilities capabilities() { return null; }
        public SceneBuilder newSceneBuilder() { throw new UnsupportedOperationException(); }
        public CompletionStage<Scene> capture(CaptureRequest request, double fovDegrees) {
            fov = fovDegrees;
            return capture(request);
        }
        public CompletionStage<Scene> capture(CaptureRequest request) {
            if (captureFailure != null) throw captureFailure;
            CompletableFuture<Scene> pending = captures.isEmpty() ? capture : new CompletableFuture<>();
            captures.add(pending);
            return pending;
        }
        public AssetHandle registerAsset(AssetPack pack) { throw new UnsupportedOperationException(); }
        public Scene appendEntities(Scene source,List<SceneEntity> entities) {
            if(appendFailure!=null)throw appendFailure;
            onAppend.run();
            appendSource=source;appended=List.copyOf(entities);return composed;
        }
        public RenderTask submit(RenderJob job) {
            submitted=job;
            if (submitFailure != null) throw submitFailure;
            submissions++; onSubmit.run(); return task;
        }
        public void close() { closed = true; }
    }
    static final class FakeTask implements RenderTask {
        final CompletableFuture<RenderResult> result = new CompletableFuture<>();
        final CompletableFuture<Void> cancellation = new CompletableFuture<>();
        volatile boolean cancelled;
        public UUID id() { return new UUID(0, 1); }
        public RenderStatus status() { return RenderStatus.RUNNING; }
        volatile RenderProgress progress=new RenderProgress(RenderProgress.Phase.QUEUED,0);
        public RenderProgress progress() { return progress; }
        public boolean cancel() { cancelled = true; cancellation.complete(null); return true; }
        public CompletionStage<RenderResult> completion() { return result; }
    }
}
