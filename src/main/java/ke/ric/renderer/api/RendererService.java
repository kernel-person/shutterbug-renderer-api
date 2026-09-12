package ke.ric.renderer.api;

import org.bukkit.plugin.Plugin;

/** Bukkit service used to discover the current renderer provider generation. */
public interface RendererService {
    /** Renderer API contract version implemented by this service interface. */
    int API_VERSION = 1;

    /** Creates a lifecycle-bound client owned by the supplied enabled consuming plugin. */
    RendererClient createClient(Plugin owner);

    /** Returns a service-wide snapshot of advertised runtime capabilities. */
    RendererCapabilities capabilities();
}
