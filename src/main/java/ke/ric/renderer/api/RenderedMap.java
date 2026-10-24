package ke.ric.renderer.api;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Optional 128x128 palette-backed map, created fresh for its caller. All operations require the
 * server thread. Does not convert images, write inventories, persist data, or take over existing maps.
 * This development-preview helper should be validated with the matching development provider.
 */
public final class RenderedMap implements AutoCloseable {
    private final MapView view;
    private final PaletteRenderer renderer;
    private boolean closed;

    private RenderedMap(MapView view, byte[] palette) {
        this.view = view;
        renderer = new PaletteRenderer(palette);
        for (MapRenderer initial : view.getRenderers()) view.removeRenderer(initial);
        view.addRenderer(renderer);
    }

    /** Creates a new map in this world using a defensive copy of exactly 16384 palette bytes. */
    public static RenderedMap create(World world, byte[] palette) {
        requireMainThread();
        Objects.requireNonNull(world, "world");
        byte[] copy = copyPalette(palette);
        return new RenderedMap(Bukkit.createMap(world), copy);
    }

    /** Replaces the palette; every canvas receives the update on its next render. */
    public void update(byte[] palette) {
        requireOpen();
        renderer.palette = copyPalette(palette);
        renderer.revision++;
    }

    /** Creates an item referring to this map, without delivering it to any inventory. */
    public ItemStack item(String name) {
        requireOpen();
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        if (name != null) meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the newly created map view; the caller must not reuse its renderer elsewhere. */
    public MapView view() { requireMainThread(); return view; }

    /** Idempotently detaches only this helper's renderer, leaving other renderers untouched. */
    @Override public void close() {
        requireMainThread();
        if (closed) return;
        closed = true;
        view.removeRenderer(renderer);
        renderer.delivered.clear();
    }

    private void requireOpen() {
        requireMainThread();
        if (closed) throw new IllegalStateException("rendered map is closed");
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("operation requires the server thread");
    }

    private static byte[] copyPalette(byte[] palette) {
        Objects.requireNonNull(palette, "palette");
        if (palette.length != 128 * 128) throw new IllegalArgumentException("map palette must be 128x128");
        return palette.clone();
    }

    private final class PaletteRenderer extends MapRenderer {
        private byte[] palette;
        private long revision;
        private final WeakHashMap<MapCanvas, Long> delivered = new WeakHashMap<>();

        PaletteRenderer(byte[] palette) { super(false); this.palette = palette; }

        @Override public void render(MapView map, MapCanvas canvas, Player player) {
            requireMainThread();
            if (closed || Objects.equals(delivered.get(canvas), revision)) return;
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, palette[y * 128 + x]);
            }
            delivered.put(canvas, revision);
        }
    }
}
