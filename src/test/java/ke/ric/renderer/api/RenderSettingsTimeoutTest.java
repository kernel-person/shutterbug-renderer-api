package ke.ric.renderer.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class RenderSettingsTimeoutTest {
    @Test void timeoutWhoseNanosecondsDoNotFitIsRejectedByThePublicValidationContract() {
        assertThrowsExactly(IllegalArgumentException.class, () -> settings(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test void largestNanosecondTimeoutIsAcceptedExactly() {
        Duration largestRepresentable = Duration.ofNanos(Long.MAX_VALUE);
        assertEquals(largestRepresentable, settings(largestRepresentable).timeout());
    }

    private static RenderSettings settings(Duration timeout) {
        return new RenderSettings(1, RenderSettings.Profile.CLASSIC, 1, 1, Set.of(),
                RenderSettings.DEFAULT_MEMORY_LIMIT, 1, timeout);
    }
}
