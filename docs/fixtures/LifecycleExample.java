package docs.examples;

import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class LifecycleExample {
    public static RendererClient discover(Plugin owner) {
        RendererService service = Bukkit.getServicesManager().load(RendererService.class);
        return service == null ? null : service.createClient(owner);
    }
}
