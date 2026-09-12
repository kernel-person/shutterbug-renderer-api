package ke.ric.renderer.sample;

import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RenderTimings;
import ke.ric.renderer.api.VisibilityStatistics;
import ke.ric.renderer.api.Attachment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveGateEvidenceTest {
    @Test
    void exactRepeatedPixelsAttachmentsAndDefensiveCopiesProduceStableHash() {
        RenderResult first = result(new byte[]{1, 2, 3, (byte) 255},
                Map.of("renderer-sample-canary", 1));
        RenderResult second = result(new byte[]{1, 2, 3, (byte) 255},
                Map.of("renderer-sample-canary", 1));

        String hash = LiveGateEvidence.verify(first, second, "renderer-sample-canary");

        assertEquals("3e6f9aae16382bf563d8991b6da1b92213911f0dd5deea3ecaccf2f35a56794a",
                hash);
    }

    @Test
    void repeatedPixelMismatchIsRejected() {
        RenderResult first = result(new byte[]{1, 2, 3, (byte) 255},
                Map.of("renderer-sample-canary", 1));
        RenderResult second = result(new byte[]{3, 2, 1, (byte) 255},
                Map.of("renderer-sample-canary", 1));

        assertThrows(IllegalStateException.class,
                () -> LiveGateEvidence.verify(first, second, "renderer-sample-canary"));
    }

    @Test
    void missingResolvedEntityEvidenceIsRejected() {
        RenderResult first = result(new byte[]{1, 2, 3, (byte) 255}, Map.of());

        assertThrows(IllegalStateException.class,
                () -> LiveGateEvidence.verify(first, first, "renderer-sample-canary"));
    }

    @Test
    void unsupportedOptionalAttachmentsDoNotInvalidateRepeatedColorEvidence() {
        RenderResult first = colorOnlyResult(new byte[]{1, 2, 3, (byte) 255});
        RenderResult second = colorOnlyResult(new byte[]{1, 2, 3, (byte) 255});

        String hash = LiveGateEvidence.verify(first, second,
                "renderer-sample-canary", Set.<Attachment>of());

        assertEquals("3e6f9aae16382bf563d8991b6da1b92213911f0dd5deea3ecaccf2f35a56794a",
                hash);
    }

    @Test
    void liveFailureMarkerExposesOnlyTheTypedFailureCode() {
        RendererException failure = new RendererException(
                RenderFailureCode.MISSING_ASSET, "private implementation detail");

        assertEquals("MISSING_ASSET", RendererSampleApiRuntime.failureCode(failure));
    }

    private static RenderResult result(byte[] rgba, Map<String, Integer> entities) {
        return new RenderResult(1, 1, rgba, new float[]{2.0f},
                new float[]{0.0f, 1.0f, 0.0f}, new int[]{7}, new int[]{11},
                new VisibilityStatistics(entities, Map.of("stone", 1), Map.of(), Map.of(), 0, 1),
                new RenderTimings(1, 2, 3, 4, 5, 6, 7, 8));
    }

    private static RenderResult colorOnlyResult(byte[] rgba) {
        return new RenderResult(1, 1, rgba, null, null, null, null,
                new VisibilityStatistics(Map.of("renderer-sample-canary", 1),
                        Map.of("stone", 1), Map.of(), Map.of(), 0, 1),
                new RenderTimings(1, 2, 3, 4, 5, 6, 7, 8));
    }
}
