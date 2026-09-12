package ke.ric.renderer.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderProgressTest {
    @Test
    void exposesTheExactStablePhaseOrderAndImmutableValue() {
        assertEquals(List.of(
                RenderProgress.Phase.QUEUED,
                RenderProgress.Phase.PREPARING,
                RenderProgress.Phase.DECODING,
                RenderProgress.Phase.RENDERING,
                RenderProgress.Phase.POST_PROCESSING,
                RenderProgress.Phase.DIAGNOSTICS,
                RenderProgress.Phase.COPYING_OUTPUT,
                RenderProgress.Phase.COMPLETE),
                List.of(RenderProgress.Phase.values()));
        assertEquals(new RenderProgress(RenderProgress.Phase.RENDERING, 4_200),
                new RenderProgress(RenderProgress.Phase.RENDERING, 4_200));
    }

    @Test
    void rejectsNullPhaseAndBasisPointsOutsideThePublicRange() {
        assertThrows(NullPointerException.class, () -> new RenderProgress(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RenderProgress(RenderProgress.Phase.QUEUED, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new RenderProgress(RenderProgress.Phase.COMPLETE, 10_001));
        assertEquals(0, new RenderProgress(RenderProgress.Phase.QUEUED, 0).basisPoints());
        assertEquals(10_000,
                new RenderProgress(RenderProgress.Phase.COMPLETE, 10_000).basisPoints());
    }

    @Test
    void renderTaskExposesThePollableProgressContract() {
        RenderProgress expected = new RenderProgress(RenderProgress.Phase.QUEUED, 0);
        RenderTask task = (RenderTask) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{RenderTask.class},
                (proxy, method, arguments) -> method.getName().equals("progress")
                        ? expected : null);
        assertSame(expected, task.progress());
    }
}
