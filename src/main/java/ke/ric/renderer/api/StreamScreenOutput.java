package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;

final class StreamScreenOutput extends StreamOutput {
    final Location anchor;
    final BlockFace face;
    final List<ItemFrame> frames=new ArrayList<>();
    StreamScreenOutput(Renderer renderer,String token,Location anchor,BlockFace face,int columns,int rows) {
        super(renderer,token,columns,rows);this.anchor=anchor.clone();this.face=face;
    }
    static int[] backingPosition(int x,int y,int z,BlockFace face,int column,int row) {
        return switch(face) {
            case NORTH -> new int[]{x-column,y-row,z};
            case SOUTH -> new int[]{x+column,y-row,z};
            case EAST -> new int[]{x,y-row,z-column};
            case WEST -> new int[]{x,y-row,z+column};
            default -> throw new IllegalArgumentException("screen face must be NORTH, SOUTH, EAST or WEST");
        };
    }
    List<Location> validate() {
        World world=anchor.getWorld();if(world==null)throw new IllegalArgumentException("screen anchor requires a world");
        List<Location> locations=new ArrayList<>();
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++) {
            int[] p=backingPosition(anchor.getBlockX(),anchor.getBlockY(),anchor.getBlockZ(),face,col,row);
            int fx=p[0]+face.getModX(),fz=p[2]+face.getModZ();
            if(!world.isChunkLoaded(p[0]>>4,p[2]>>4)||!world.isChunkLoaded(fx>>4,fz>>4))throw new IllegalArgumentException("screen chunks must already be loaded");
            Block backing=world.getBlockAt(p[0],p[1],p[2]);
            if(!backing.getType().isSolid() || !world.getBlockAt(fx,p[1],fz).isEmpty())throw new IllegalArgumentException("screen requires a solid wall with empty space in front");
            Location front=new Location(world,fx+.5,p[1]+.5,fz+.5);
            if(world.getNearbyEntities(front,.49,.49,.49).stream().anyMatch(e->e instanceof ItemFrame))throw new IllegalArgumentException("screen space already contains an item frame");
            locations.add(front);
        }
        return locations;
    }
    @Override void open() {
        List<Location> positions=validate();allocate(anchor.getWorld());
        for(int i=0;i<positions.size();i++) {
            ItemFrame frame=(ItemFrame)anchor.getWorld().spawnEntity(positions.get(i),EntityType.ITEM_FRAME);
            try {
                frame.getPersistentDataContainer().set(TAG,PersistentDataType.STRING,token);
                frame.getPersistentDataContainer().set(CREATED_AT,PersistentDataType.LONG,System.currentTimeMillis());
            } catch(Throwable failure) { frame.remove();throw failure; }
            frames.add(frame);
            if(!frame.setFacingDirection(face,true))throw new IllegalStateException("could not face stream item frame");
            frame.setFixed(true);frame.setPersistent(true);frame.setItemDropChance(0);frame.setItem(item(i),false);
        }
    }
    @Override boolean usable() {
        return frames.size()==columns*rows && frames.stream().allMatch(frame->frame.isValid()
                && token.equals(frame.getPersistentDataContainer().get(TAG,PersistentDataType.STRING)));
    }
    @Override public void close() {
        for(ItemFrame frame:frames)if(token.equals(frame.getPersistentDataContainer().get(TAG,PersistentDataType.STRING)))frame.remove();
        frames.clear();super.close();
    }
}
