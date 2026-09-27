package docs.examples;

import ke.ric.renderer.api.RendererCapabilities;

/** Small compile probe shared by the Maven and Gradle consumer builds. */
public final class ConsumerExample {
    public static boolean ready(RendererCapabilities capabilities) {
        return capabilities.nativeAvailable() && capabilities.packagedForProduction();
    }
}
