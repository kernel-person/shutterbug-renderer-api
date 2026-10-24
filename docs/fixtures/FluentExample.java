package docs.examples;

import ke.ric.renderer.api.Renderer;
import ke.ric.renderer.api.RenderStream;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Call these methods from your plugin's commands or events on the server thread. */
public final class FluentExample extends JavaPlugin {
    private Renderer renderer;

    @Override public void onEnable() { renderer = Renderer.forPlugin(this); }

    public void photograph(Player player) {
        renderer.snapshot(player).fov(75).captureMap(item -> {
            // Callbacks run on the server thread. This example drops inventory overflow.
            if (player.isOnline()) player.getInventory().addItem(item).values().forEach(
                    extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }, failure -> getLogger().warning("Photo failed: " + failure.getMessage()));
    }

    public RenderStream monitor(Entity camera, Player viewer) {
        // --8<-- [start:live-feed]
        RenderStream feed = renderer.stream(camera)
            .fov(75)
            .toPlayer(viewer)
            .start();
        // --8<-- [end:live-feed]
        return feed;
    }

    @Override public void onDisable() { if (renderer != null) renderer.close(); }
}
