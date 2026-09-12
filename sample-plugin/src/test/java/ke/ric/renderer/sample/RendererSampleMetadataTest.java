package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererSampleMetadataTest {
    @Test
    void absentProviderDoesNotPreventSamplePluginFromEnabling() throws IOException {
        try (var stream = RendererSampleMetadataTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream, "packaged plugin.yml");
            String plugin = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(plugin.lines().anyMatch(
                    line -> line.strip().equals("softdepend: [ShutterBugRenderer]")), plugin);
            assertFalse(plugin.lines().anyMatch(
                    line -> line.stripLeading().startsWith("depend:")), plugin);
        }
    }
}
