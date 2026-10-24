package ke.ric.renderer.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class StreamPlayerOutput extends StreamOutput {
    final Player player;
    StreamPlayerOutput(Renderer renderer,String token,Player player) { super(renderer,token,1,1);this.player=player; }
    @Override void open() {
        if(!player.isOnline())throw new IllegalArgumentException("stream player is disconnected");
        int slot=player.getInventory().firstEmpty();
        if(slot<0)throw new IllegalArgumentException("stream player needs a free inventory slot");
        allocate(player.getWorld());
        // Allocation can invoke provider hooks; never overwrite a slot that changed meanwhile.
        ItemStack current=player.getInventory().getItem(slot);
        if(current!=null && !current.getType().isAir())throw new IllegalStateException("stream inventory slot is no longer free");
        player.getInventory().setItem(slot,item(0));
    }
    @Override boolean usable() { return player.isOnline(); }
    @Override public void close() {
        for(int slot=0;slot<player.getInventory().getSize();slot++)if(token.equals(tag(player.getInventory().getItem(slot))))player.getInventory().setItem(slot,null);
        super.close();
    }
}
