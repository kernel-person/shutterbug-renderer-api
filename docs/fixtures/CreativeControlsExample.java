package docs.examples;

import ke.ric.renderer.api.*;
import org.bukkit.entity.Player;

/** Reusable requests: choose capture(), captureMap(), or further fluent settings. */
public final class CreativeControlsExample {
    public static SnapshotBuilder photograph(Renderer renderer, Player camera) {
        return renderer.snapshot(camera).fov(75).roll(12)
                .appearance(RenderAppearance.defaults().exposure(1.2))
                .environment(RenderEnvironment.captured().timeOfDay(6000).weather(Weather.CLEAR));
    }

    public static SnapshotBuilder highlight(Renderer renderer, Player viewer) {
        return renderer.snapshot(viewer).visibleTo(viewer).recipe(RenderRecipe.layers(
                RenderLayer.scene(),
                RenderLayer.entities(EntitySelector.living()).color(LayerColor.tint(0x80ffb0))));
    }

    public static SnapshotBuilder xray(Renderer renderer, Player viewer) {
        return renderer.snapshot(viewer).visibleTo(viewer).recipe(RenderRecipe.layers(
                RenderLayer.scene().brightness(0.2),
                RenderLayer.entities(EntitySelector.living())
                        .occlusion(Occlusion.SELECTED_ONLY).color(LayerColor.solid(0x80ffb0))));
    }

    public static SnapshotBuilder studio(Renderer renderer, Player camera) {
        var flash = new SpotLight(LightSpace.CAMERA, 0xffe0c0,
                0, 0.2, 0, new LightDirection(0, 0, 1), 3, 16, 45, 90, true);
        return renderer.snapshot(camera).lighting(RenderLighting.studio().ambient(0.1).add(flash));
    }
}
