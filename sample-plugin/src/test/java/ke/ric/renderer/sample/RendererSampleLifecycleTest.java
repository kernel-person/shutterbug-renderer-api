package ke.ric.renderer.sample;

import ke.ric.renderer.api.AssetHandle;
import ke.ric.renderer.api.AssetPack;
import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.CaptureRequest;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RendererCapabilities;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererService;
import ke.ric.renderer.api.Scene;
import ke.ric.renderer.api.SceneBuilder;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererSampleLifecycleTest {
    @Test
    void unavailableActivationStaysSafeAndReplacementClosesEveryOwnedClientOnce() {
        AtomicReference<RendererService> discovered = new AtomicReference<>();
        AtomicReference<FakeClient> nextClient = new AtomicReference<>();
        List<String> unavailable = new ArrayList<>();
        List<RendererCapabilities> available = new ArrayList<>();
        RendererSampleLifecycle lifecycle = new RendererSampleLifecycle(
                discovered::get,
                ignored -> {
                    FakeClient client = nextClient.get();
                    if (client == null) throw new IllegalStateException("activation unavailable");
                    return client;
                },
                unavailable::add,
                available::add);

        assertNull(lifecycle.activate());
        assertFalse(unavailable.isEmpty(), "service absence must be logged as unavailable");

        FakeClient first = new FakeClient();
        discovered.set(new FakeService());
        nextClient.set(first);
        assertSame(first, lifecycle.activate());
        assertSame(first, lifecycle.client());
        assertTrue(available.contains(CAPABILITIES));

        FakeClient replacement = new FakeClient();
        nextClient.set(replacement);
        assertSame(replacement, lifecycle.activate());
        assertSame(replacement, lifecycle.client());
        org.junit.jupiter.api.Assertions.assertEquals(1, first.closeCalls);

        nextClient.set(null);
        assertNull(lifecycle.activate());
        assertNull(lifecycle.client());
        org.junit.jupiter.api.Assertions.assertEquals(1, replacement.closeCalls);
        assertTrue(unavailable.getLast().contains("activation unavailable"));

        lifecycle.close();
        lifecycle.close();
        org.junit.jupiter.api.Assertions.assertEquals(1, first.closeCalls);
        org.junit.jupiter.api.Assertions.assertEquals(1, replacement.closeCalls);
    }

    private static final RendererCapabilities CAPABILITIES = new RendererCapabilities(
            "test", "test", true, false,
            EnumSet.allOf(RenderSettings.Profile.class), EnumSet.allOf(Attachment.class));

    private static final class FakeService implements RendererService {
        @Override
        public RendererClient createClient(Plugin owner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RendererCapabilities capabilities() {
            return CAPABILITIES;
        }
    }

    private static final class FakeClient implements RendererClient {
        int closeCalls;

        @Override public boolean active() { return true; }
        @Override public RendererCapabilities capabilities() { return CAPABILITIES; }
        @Override public SceneBuilder newSceneBuilder() { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<Scene> capture(CaptureRequest request) { throw new UnsupportedOperationException(); }
        @Override public AssetHandle registerAsset(AssetPack pack) { throw new UnsupportedOperationException(); }
        @Override public RenderTask submit(RenderJob job) { throw new UnsupportedOperationException(); }
        @Override public void close() { closeCalls++; }
    }
}
