package ke.ric.renderer.api;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Removes only this plugin's tagged abandoned stream objects, without loading any chunks. */
final class StreamOutputCleanup implements Listener {
    private static final Set<String> LIVE=new HashSet<>();
    private static final WeakHashMap<Renderer,StreamOutputCleanup> INSTALLED=new WeakHashMap<>();
    private final String prefix;
    private StreamOutputCleanup(Renderer renderer) { prefix=renderer.owner().getName()+"/"; }
    static void live(String token) { LIVE.add(token); }
    static void finished(String token) { LIVE.remove(token); }
    static void install(Renderer renderer) {
        if(INSTALLED.containsKey(renderer))return;
        StreamOutputCleanup listener=new StreamOutputCleanup(renderer);INSTALLED.put(renderer,listener);
        renderer.owner().getServer().getPluginManager().registerEvents(listener,renderer.owner());
        renderer.onClose(()-> { HandlerList.unregisterAll(listener);INSTALLED.remove(renderer); });
        for(var world:renderer.owner().getServer().getWorlds())for(Chunk chunk:world.getLoadedChunks())listener.clean(chunk);
        for(Player player:renderer.owner().getServer().getOnlinePlayers())listener.clean(player);
    }
    private boolean abandoned(String token) { return token!=null && token.startsWith(prefix) && !LIVE.contains(token); }
    @EventHandler public void loaded(ChunkLoadEvent event) { clean(event.getChunk()); }
    @EventHandler public void joined(PlayerJoinEvent event) { clean(event.getPlayer()); }
    private void clean(Chunk chunk) {
        for(Entity entity:chunk.getEntities()) {
            if(entity instanceof ItemFrame frame && abandoned(frame.getPersistentDataContainer().get(StreamOutput.TAG,PersistentDataType.STRING)))frame.remove();
            else if(entity instanceof Item item && abandoned(StreamOutput.tag(item.getItemStack())))item.remove();
        }
    }
    private void clean(Player player) {
        for(int slot=0;slot<player.getInventory().getSize();slot++)if(abandoned(StreamOutput.tag(player.getInventory().getItem(slot))))player.getInventory().setItem(slot,null);
    }
}
