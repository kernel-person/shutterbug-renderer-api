package ke.ric.renderer.api;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SdkUsabilityTest {
    @Test void defaultsUseMapResolutionWithoutChangingResourcePolicy() {
        RenderSettings settings = RenderSettings.defaults();
        assertEquals(128, settings.width());
        assertEquals(128, settings.height());
        assertEquals(RenderSettings.Profile.CLASSIC, settings.profile());
        assertEquals(Set.of(), settings.attachments());
        assertEquals(512L * 1024 * 1024, settings.nativeMemoryLimitBytes());
        assertEquals(Duration.ofMinutes(2), settings.timeout());
        assertEquals(RenderSettings.classic(128, 128), settings,
                "the convenience must preserve the existing worker/resource policy");
    }

    @Test void resolutionCopyPreservesQualityAttachmentsAndResourceLimits() {
        RenderSettings original = RenderSettings.classic(64, 32)
                .withProfile(RenderSettings.Profile.NORMAL).withWorkers(1)
                .withMemoryLimit(12345).withTimeout(Duration.ofSeconds(3))
                .withAttachments(Set.of(Attachment.OBJECT_ID));
        RenderSettings changed = original.withResolution(256, 192);
        assertEquals(new RenderSettings(1, RenderSettings.Profile.NORMAL, 256, 192,
                Set.of(Attachment.OBJECT_ID), 12345, 1, Duration.ofSeconds(3)), changed);
        assertEquals(64, original.width());
        assertEquals(32, original.height());
        for (int[] invalid : new int[][]{{0, 128}, {128, 0}, {-1, 128}, {128, -1}}) {
            assertThrows(IllegalArgumentException.class, () -> original.withResolution(invalid[0], invalid[1]));
        }
    }

    @Test void attachmentArgumentsReplaceDeduplicateAndDefensivelyCopy() {
        RenderSettings original = RenderSettings.classic(64, 32).withWorkers(1)
                .withAttachments(Set.of(Attachment.DEPTH));
        Attachment[] additional = {Attachment.OBJECT_ID, Attachment.NORMAL};
        RenderSettings changed = original.withAttachments(Attachment.OBJECT_ID, additional);
        additional[1] = Attachment.DEPTH;
        assertEquals(Set.of(Attachment.OBJECT_ID, Attachment.NORMAL), changed.attachments());
        assertEquals(original.withAttachments(Set.of(Attachment.OBJECT_ID, Attachment.NORMAL)), changed);
        assertEquals(Set.of(Attachment.DEPTH), original.attachments());
        assertThrows(UnsupportedOperationException.class, () -> changed.attachments().clear());
        RenderSettings single = original.withAttachments(Attachment.OBJECT_ID);
        assertEquals(Set.of(Attachment.OBJECT_ID), single.attachments());
        assertEquals(Set.of(Attachment.DEPTH, Attachment.NORMAL),
                original.withAttachments(Attachment.DEPTH, Attachment.NORMAL).attachments());
    }

    @Test void attachmentArgumentsRejectNullElementsAndArray() {
        RenderSettings settings = RenderSettings.classic(128, 128);
        assertThrows(NullPointerException.class, () -> settings.withAttachments((Attachment) null));
        assertThrows(NullPointerException.class, () -> settings.withAttachments(Attachment.OBJECT_ID, (Attachment[]) null));
        assertThrows(NullPointerException.class, () -> settings.withAttachments(Attachment.OBJECT_ID, (Attachment) null));
    }

    @Test void legacyBareNullAttachmentCallStillCompilesAndClears() {
        RenderSettings original = RenderSettings.classic(128, 128).withAttachments(Set.of(Attachment.OBJECT_ID));
        assertEquals(Set.of(), original.withAttachments(null).attachments());
    }

    @Test void defaultCaptureCopiesCameraAndKeepsExistingCaptureBudgets() {
        Location camera = new Location(null, 1, 64, 3, 90, 10);
        CaptureRequest request = CaptureRequest.at(camera);
        camera.setX(99);
        assertEquals(new Location(null, 1, 64, 3, 90, 10), request.camera());
        request.camera().setY(99);
        assertEquals(64, request.camera().getY());
        assertEquals(RenderSettings.classic(128, 128), request.settings());
        assertEquals(16, request.radius());
        assertEquals(2, request.chunksPerTick());
        assertEquals(Set.of(), request.excludedEntityIds());
        assertThrows(NullPointerException.class, () -> CaptureRequest.at(null));
    }

    @Test void namedSettingsCopiesPreserveOtherValuesAndValidation() throws Exception {
        RenderSettings original = RenderSettings.classic(64, 32).withAttachments(Set.of(Attachment.OBJECT_ID));
        RenderSettings changed = (RenderSettings) RenderSettings.class.getMethod("withWorkers", int.class).invoke(original, 1);
        changed = (RenderSettings) RenderSettings.class.getMethod("withMemoryLimit", long.class).invoke(changed, 12345L);
        changed = (RenderSettings) RenderSettings.class.getMethod("withTimeout", Duration.class).invoke(changed, Duration.ofSeconds(3));
        assertEquals(1, changed.workers());
        assertEquals(12345, changed.nativeMemoryLimitBytes());
        assertEquals(Duration.ofSeconds(3), changed.timeout());
        assertEquals(Set.of(Attachment.OBJECT_ID), changed.attachments());
        assertEquals(64, changed.width());
        assertEquals(32, changed.height());
        assertEquals(Duration.ofMinutes(2), original.timeout());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> RenderSettings.class.getMethod("withWorkers", int.class).invoke(original, 0)).getCause());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> RenderSettings.class.getMethod("withMemoryLimit", long.class).invoke(original, 0L)).getCause());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> RenderSettings.class.getMethod("withTimeout", Duration.class).invoke(original, Duration.ZERO)).getCause());
    }

    @Test void namedCaptureCopiesPreserveDefensiveCameraAndExclusions() throws Exception {
        Location camera = new Location(null, 1, 2, 3);
        CaptureRequest original = (CaptureRequest) CaptureRequest.class.getMethod("at", Location.class, RenderSettings.class)
                .invoke(null, camera, RenderSettings.classic(128, 128));
        camera.setX(9);
        assertEquals(16, original.radius());
        assertEquals(2, original.chunksPerTick());
        assertEquals(1, original.camera().getX());
        UUID excluded = UUID.randomUUID();
        Set<UUID> input = new HashSet<>(Set.of(excluded));
        CaptureRequest changed = (CaptureRequest) CaptureRequest.class.getMethod("withRadius", double.class).invoke(original, 32.0);
        changed = (CaptureRequest) CaptureRequest.class.getMethod("withChunksPerTick", int.class).invoke(changed, 4);
        changed = (CaptureRequest) CaptureRequest.class.getMethod("excluding", Set.class).invoke(changed, input);
        input.clear();
        assertEquals(32, changed.radius());
        assertEquals(4, changed.chunksPerTick());
        assertEquals(Set.of(excluded), changed.excludedEntityIds());
        assertEquals(Set.of(), original.excludedEntityIds());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> CaptureRequest.class.getMethod("withRadius", double.class).invoke(original, Double.NaN)).getCause());
    }

    @Test void oldExceptionConstructorsKeepTheirDescriptorAndDefaultDetail() throws Exception {
        Throwable cause = new IllegalStateException("cause");
        RendererException old = new RendererException(RenderFailureCode.MEMORY_LIMIT, "old", cause);
        assertEquals("UNSPECIFIED", RendererException.class.getMethod("detail").invoke(old).toString());
        assertSame(cause, old.getCause());
        assertEquals("UNSPECIFIED", RendererException.class.getMethod("detail").invoke(
                new RendererException(RenderFailureCode.INVALID_REQUEST, "old")).toString());
    }

    @Test void legacyMessageOnlyExceptionStillAllowsInitCause() {
        RendererException failure = new RendererException(RenderFailureCode.INVALID_REQUEST, "legacy");
        Throwable cause = new IllegalArgumentException("cause set later");
        assertSame(failure, assertDoesNotThrow(() -> failure.initCause(cause)));
        assertSame(cause, failure.getCause());
    }
}
