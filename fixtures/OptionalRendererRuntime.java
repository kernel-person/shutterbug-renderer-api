package docs.examples;

import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated API-linked runtime loaded only after the API-free entry point checks availability. */
public final class OptionalRendererRuntime {
    private static JavaPlugin owner;
    private static Listener registrationListener;
    private static RendererClient client;

    public static synchronized void enable(JavaPlugin plugin) {
        if (owner != null) return;
        owner = java.util.Objects.requireNonNull(plugin, "plugin");
        registrationListener = new Listener() {
            @EventHandler
            public void onServiceRegister(ServiceRegisterEvent event) {
                if (event.getProvider().getService() == RendererService.class) attach();
            }
        };
        Bukkit.getPluginManager().registerEvents(registrationListener, plugin);
        attach();
    }

    private static synchronized void attach() {
        if (owner == null || client != null) return;
        RendererService service = Bukkit.getServicesManager().load(RendererService.class);
        if (service == null) return;
        client = service.createClient(owner);
    }

    public static void disable() {
        RendererClient closingClient;
        Listener closingListener;
        synchronized (OptionalRendererRuntime.class) {
            closingClient = client;
            closingListener = registrationListener;
            client = null;
            registrationListener = null;
            owner = null;
        }
        if (closingListener != null) HandlerList.unregisterAll(closingListener);
        if (closingClient != null) closingClient.close();
    }
}
