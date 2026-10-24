package ke.ric.renderer.api;

import org.bukkit.plugin.Plugin;

/** Bukkit service used to discover the current renderer provider generation. */
public interface RendererService {
    /** Renderer API contract version implemented by this service interface. */
    int API_VERSION = 1;

    /**
     * Creates a lifecycle-bound client for an enabled consuming plugin; discover/create on the server thread.
     * @param owner consuming plugin whose disable must release this client's work
     * @return a new client, which must not be reused across provider reloads
     */
    RendererClient createClient(Plugin owner);

    /** Returns a service-wide snapshot of advertised runtime capabilities. */
    RendererCapabilities capabilities();
}
