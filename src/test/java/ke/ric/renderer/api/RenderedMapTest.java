package ke.ric.renderer.api;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static ke.ric.renderer.api.SdkBukkitTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderedMapTest {
    final SdkBukkitTestSupport bukkit = new SdkBukkitTestSupport();
    final List<MapRenderer> renderers = new ArrayList<>();
    final World world = proxy(World.class, (p, m, a) -> defaultValue(m.getReturnType()));
    final MapView view = proxy(MapView.class, (p, m, a) -> switch (m.getName()) {
        case "getRenderers" -> new ArrayList<>(renderers);
        case "addRenderer" -> { renderers.add((MapRenderer) a[0]); yield null; }
        case "removeRenderer" -> renderers.remove(a[0]);
        default -> defaultValue(m.getReturnType());
    });
    Object oldServer;
    RenderedMap map;

    @BeforeEach void installServer() throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        oldServer = field.get(null);
        field.set(null, bukkit.server);
        bukkit.createdMap = view;
    }

    @AfterEach void restoreServer() throws Exception {
        if (map != null) map.close();
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, oldServer);
    }

    @Test void invalidDimensionsDoNotAllocateMap() {
        assertThrows(IllegalArgumentException.class, () -> RenderedMap.create(world, new byte[16383]));
        assertThrows(IllegalArgumentException.class, () -> RenderedMap.create(world, new byte[16385]));
        assertThrows(NullPointerException.class, () -> RenderedMap.create(world, null));
        assertEquals(0, bukkit.mapsCreated);
    }

    @Test void createAndUpdateCopyPaletteAndDeliverToEveryCanvas() {
        byte[] palette = new byte[16384];
        palette[0] = 10;
        map = RenderedMap.create(world, palette);
        palette[0] = 99;
        assertSame(view, map.view());
        assertEquals(1, bukkit.mapsCreated);
        MapRenderer renderer = renderers.getFirst();
        byte[] first = new byte[16384], second = new byte[16384];
        MapCanvas one = canvas(first), two = canvas(second);
        renderer.render(view, one, null);
        renderer.render(view, two, null);
        assertEquals(10, first[0]);
        assertEquals(10, second[0]);
        palette[0] = 23;
        palette[16383] = 42;
        map.update(palette);
        palette[0] = 99;
        renderer.render(view, one, null);
        renderer.render(view, two, null);
        assertEquals(23, first[0]);
        assertEquals(23, second[0]);
        assertEquals(42, second[16383]);
        assertThrows(IllegalArgumentException.class, () -> map.update(new byte[2]));
        renderer.render(view, two, null);
        assertEquals(23, second[0]);
    }

    @Test void closeRemovesOnlyOwnedRendererAndIsIdempotent() {
        map = RenderedMap.create(world, new byte[16384]);
        MapRenderer other = new MapRenderer() { public void render(MapView map, MapCanvas canvas, Player player) {} };
        renderers.add(other);
        map.close(); map.close();
        assertEquals(List.of(other), renderers);
        assertThrows(IllegalStateException.class, () -> map.update(new byte[16384]));
        assertThrows(IllegalStateException.class, () -> map.item("closed"));
    }

    @Test void itemUsesOwnedMapAndNameWithoutInventoryWrites() {
        AtomicReference<MapView> linked = new AtomicReference<>();
        AtomicReference<String> name = new AtomicReference<>();
        MapMeta meta = proxy(MapMeta.class, (p, m, a) -> switch (m.getName()) {
            case "setMapView" -> { linked.set((MapView) a[0]); yield null; }
            case "setDisplayName" -> { name.set((String) a[0]); yield null; }
            case "clone" -> p;
            default -> defaultValue(m.getReturnType());
        });
        bukkit.itemFactory = proxy(ItemFactory.class, (p, m, a) -> switch (m.getName()) {
            case "getItemMeta", "asMetaFor" -> meta;
            case "isApplicable" -> true;
            default -> defaultValue(m.getReturnType());
        });
        map = RenderedMap.create(world, new byte[16384]);
        assertEquals(Material.FILLED_MAP, map.item("Test map").getType());
        assertSame(view, linked.get());
        assertEquals("Test map", name.get());
    }

    @Test void bukkitOperationsRejectWorkerThread() throws Exception {
        map = RenderedMap.create(world, new byte[16384]);
        for (Runnable operation : new Runnable[]{() -> RenderedMap.create(world, new byte[16384]),
                () -> map.update(new byte[16384]), () -> map.item("name"), () -> map.view(), () -> map.close()}) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            RendererSessionTest.worker(() -> { try { operation.run(); } catch (Throwable caught) { failure.set(caught); } });
            assertInstanceOf(IllegalStateException.class, failure.get());
        }
    }

    static MapCanvas canvas(byte[] pixels) {
        return proxy(MapCanvas.class, (p, m, a) -> {
            if (m.getName().equals("setPixel")) pixels[(int) a[1] * 128 + (int) a[0]] = (byte) a[2];
            return defaultValue(m.getReturnType());
        });
    }
}
