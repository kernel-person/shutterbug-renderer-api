package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveGateThreadingTest {
    @Test
    void completionOnMinecraftMainThreadIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> LiveGateThreading.requireAsyncCompletion(true));
        assertDoesNotThrow(() -> LiveGateThreading.requireAsyncCompletion(false));
    }
}
