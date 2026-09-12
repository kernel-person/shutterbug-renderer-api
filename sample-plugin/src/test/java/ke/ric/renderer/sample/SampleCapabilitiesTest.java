package ke.ric.renderer.sample;

import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RendererCapabilities;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleCapabilitiesTest {
    @Test
    void inspectsNormalExtremeDepthAndObjectIdWithoutInferringPackaging() {
        SampleCapabilities capabilities = SampleCapabilities.inspect(new RendererCapabilities(
                "test", "test", true, false,
                EnumSet.allOf(RenderSettings.Profile.class),
                EnumSet.allOf(Attachment.class)));

        assertTrue(capabilities.coldCaptureAvailable());
        assertTrue(capabilities.manualExtremeAvailable());
        assertEquals(EnumSet.allOf(Attachment.class),
                capabilities.optionalAttachments());
        assertTrue(capabilities.messages().isEmpty());
    }

    @Test
    void unsupportedOptionalEvidenceAndProfileProduceGracefulMessages() {
        SampleCapabilities capabilities = SampleCapabilities.inspect(new RendererCapabilities(
                "test", "test", true, false,
                Set.of(RenderSettings.Profile.NORMAL), Set.of()));

        assertTrue(capabilities.coldCaptureAvailable());
        assertFalse(capabilities.manualExtremeAvailable());
        assertTrue(capabilities.optionalAttachments().isEmpty());
        String messages = String.join("\n", capabilities.messages());
        assertTrue(messages.contains("EXTREME"), messages);
        assertTrue(messages.contains("DEPTH"), messages);
        assertTrue(messages.contains("OBJECT_ID"), messages);
    }

    @Test
    void unavailableNativeRuntimeSkipsBothWorkflowsWithoutDisablingConsumer() {
        SampleCapabilities capabilities = SampleCapabilities.inspect(new RendererCapabilities(
                "test", "test", false, false,
                Set.of(RenderSettings.Profile.NORMAL, RenderSettings.Profile.EXTREME),
                Set.of(Attachment.DEPTH, Attachment.OBJECT_ID)));

        assertFalse(capabilities.coldCaptureAvailable());
        assertFalse(capabilities.manualExtremeAvailable());
        assertTrue(capabilities.messages().stream().anyMatch(
                message -> message.contains("native runtime")));
    }
}
