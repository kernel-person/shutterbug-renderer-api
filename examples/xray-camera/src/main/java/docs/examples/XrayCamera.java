package docs.examples;

import ke.ric.renderer.api.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Development example: one capture, two ordered layers, provider-owned durable photos. */
public final class XrayCamera extends JavaPlugin {
    private Renderer renderer;
    private boolean busy;

    @Override public void onEnable() { renderer = Renderer.forPlugin(this); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Use /xrayphoto in game."); return true; }
        if (!player.hasPermission("xraycamera.use") || busy) return true;
        busy = true;
        renderer.snapshot(player)
                .settings(RenderSettings.defaults().withWorkers(1))
                .visibleTo(player)
                .recipe(RenderRecipe.layers(
                        RenderLayer.scene().brightness(0.2),
                        RenderLayer.entities(EntitySelector.living()).occlusion(Occlusion.SELECTED_ONLY)))
                .captureMap(item -> {
                    busy = false;
                    if (player.isOnline()) {
                        var meta = item.getItemMeta();meta.setDisplayName("X-ray photo");item.setItemMeta(meta);
                        player.getInventory().addItem(item).values().forEach(
                                extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                    }
                }, error -> {
                    busy = false;
                    getLogger().warning("Photo failed; try again when the renderer is available.");
                });
        return true;
    }

    @Override public void onDisable() { if (renderer != null) renderer.close(); }
}
