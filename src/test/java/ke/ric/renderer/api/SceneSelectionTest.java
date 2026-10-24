package ke.ric.renderer.api;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class SceneSelectionTest {
    @Test void immutableSelectionIsAvailableToOldAndNewClients() {
        Class<?> selection = assertDoesNotThrow(
                () -> Class.forName("ke.ric.renderer.api.SceneSelection"));
        assertTrue(selection.isRecord());
        assertDoesNotThrow(() -> RendererClient.class.getMethod("deriveScene", Scene.class, selection));
    }

    @Test void nullEntityModeIsRejected() {
        assertThrows(NullPointerException.class, () -> new SceneSelection(true, null));
    }

    @Test void oldProvidersRejectDerivationWithTypedUnsupportedCapability() {
        RendererClient legacy = new RendererClient() {
            public boolean active() { return true; }
            public RendererCapabilities capabilities() { throw new UnsupportedOperationException(); }
            public SceneBuilder newSceneBuilder() { throw new UnsupportedOperationException(); }
            public CompletionStage<Scene> capture(CaptureRequest request) { throw new UnsupportedOperationException(); }
            public AssetHandle registerAsset(AssetPack pack) { throw new UnsupportedOperationException(); }
            public RenderTask submit(RenderJob job) { throw new UnsupportedOperationException(); }
            public void close() { }
        };
        Scene scene = new Scene() {
            public int version() { return 1; }
            public int width() { return 1; }
            public int height() { return 1; }
        };
        RendererException failure = assertThrows(RendererException.class,
                () -> legacy.deriveScene(scene, SceneSelection.entitiesOnly()));
        assertEquals(RenderFailureCode.UNSUPPORTED_CAPABILITY, failure.code());
        assertTrue(legacy.supportedSceneOperations().isEmpty());
        assertEquals(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                assertThrows(RendererException.class,()->legacy.deriveScene(scene,SceneTransform.defaults())).code());
        assertEquals(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                assertThrows(RendererException.class,()->legacy.reserveComposition(1)).code());
        assertEquals(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                assertThrows(RendererException.class, () -> legacy.appendEntities(scene, java.util.List.of())).code());
    }

    @Test void sessionSupportsCaptureWithExactEntities() {
        assertDoesNotThrow(() -> RendererSession.class.getMethod("captureAndRender", CaptureRequest.class, java.util.List.class));
    }
}
