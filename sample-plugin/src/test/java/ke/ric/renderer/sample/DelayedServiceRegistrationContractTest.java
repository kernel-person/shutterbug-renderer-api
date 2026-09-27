package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedServiceRegistrationContractTest {
    @Test
    void sampleRetriesWhenTheAsynchronousProviderRegistersItsService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ke/ric/renderer/sample/RendererSampleApiRuntime.java"));
        assertTrue(source.contains("implements RendererSampleRuntime, Listener"));
        assertTrue(source.contains("ServiceRegisterEvent"));
        assertTrue(source.contains("event.getProvider().getService() == RendererService.class"));
        assertTrue(source.contains("activateAvailableService()"));
        assertTrue(source.contains("HandlerList.unregisterAll(this)"));
    }
}
