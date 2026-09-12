package docs.examples;

import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated API-linked runtime loaded only after the API-free entry point checks availability. */
public final class OptionalRendererRuntime {
    private static RendererClient client;
    public static void enable(JavaPlugin plugin) {
        RendererService service = Bukkit.getServicesManager().load(RendererService.class);
        if (service == null) return;
        client = service.createClient(plugin);
    }
    public static void disable() { if (client != null) { client.close(); client = null; } }
}
