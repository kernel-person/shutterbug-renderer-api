package docs.examples;

import ke.ric.renderer.api.*;
import org.bukkit.entity.Player;

/** One capture, two ordered passes; captureMap() also saves the resulting photo. */
public final class XrayPipelineExample {
    public static SnapshotBuilder photo(Renderer renderer, Player viewer) {
        return renderer.snapshot(viewer).visibleTo(viewer).recipe(RenderRecipe.layers(
                RenderLayer.scene().brightness(0.2),
                RenderLayer.entities(EntitySelector.living()).occlusion(Occlusion.SELECTED_ONLY)));
    }
}
