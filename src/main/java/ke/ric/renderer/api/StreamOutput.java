package ke.ric.renderer.api;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-thread temporary output ownership. */
abstract class StreamOutput implements AutoCloseable {
    static final NamespacedKey TAG=new NamespacedKey("shutterbugrenderer","temporary_stream");
    static final NamespacedKey CREATED_AT=new NamespacedKey("shutterbugrenderer","temporary_stream_created_at");
    final Renderer renderer;
    final String token;
    final int columns,rows;
    final List<RenderedMap> maps=new ArrayList<>();
    World world;
    StreamOutput(Renderer renderer,String token,int columns,int rows) {
        this.renderer=renderer;this.token=token;this.columns=columns;this.rows=rows;
    }
    abstract void open();
    abstract boolean usable();
    void allocate(World world) {
        this.world=world;
        byte[][] blank=noSignal(columns,rows);
        for(int i=0;i<columns*rows;i++)maps.add(renderer.client().createTemporaryMap(world,blank[i]));
    }
    ItemStack item(int index) {
        ItemStack item=maps.get(index).item("Live camera");
        ItemMeta meta=item.getItemMeta(); meta.getPersistentDataContainer().set(TAG,PersistentDataType.STRING,token);
        meta.getPersistentDataContainer().set(CREATED_AT,PersistentDataType.LONG,System.currentTimeMillis());item.setItemMeta(meta);
        return item;
    }
    static String tag(ItemStack item) {
        if(item==null || !item.hasItemMeta())return null;
        return item.getItemMeta().getPersistentDataContainer().get(TAG,PersistentDataType.STRING);
    }
    void update(byte[][] palette) { for(int i=0;i<maps.size();i++)maps.get(i).update(palette[i]); }
    void noSignal() { update(noSignal(columns,rows)); }
    @Override public void close() { for(RenderedMap map:maps)map.close(); maps.clear(); }
    static byte[][] convert(RenderResult result,int columns,int rows) {
        return MinecraftMapConverter.tiles(RendererImages.fit(result,columns*128,rows*128),MapDither.NONE);
    }
    /** Small fixed glyphs keep timeout display updates independent of the conversion queue. */
    static byte[][] noSignal(int columns,int rows) {
        int width=columns*128,height=rows*128;byte[] all=new byte[width*height];java.util.Arrays.fill(all,(byte)119);
        String[] glyphs={"1000111001101011000110001","0111010001100011000101110","0000000000000000000000000","0111110000011100000111110","1111100100001000010011111","0111110000101111000101110","1000111001101011001110001","0111010001100011111110001","1000010000100001000011111"};
        // N O [space] S I G N A L, each glyph is five columns by five rows.
        int scale=2,startX=(width-(glyphs.length*6-1)*scale)/2,startY=(height-5*scale)/2;
        for(int g=0;g<glyphs.length;g++)for(int y=0;y<5;y++)for(int x=0;x<5;x++)if(glyphs[g].charAt(y*5+x)=='1')
            for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++)all[(startY+y*scale+dy)*width+startX+(g*6+x)*scale+dx]=(byte)34;
        byte[][] tiles=new byte[columns*rows][16384];
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++)for(int y=0;y<128;y++)
            System.arraycopy(all,(row*128+y)*width+col*128,tiles[row*columns+col],y*128,128);
        return tiles;
    }
}
