package docs.examples;

import ke.ric.renderer.api.Renderer;
import ke.ric.renderer.api.RenderRecipe;
import ke.ric.renderer.api.RenderLayer;
import ke.ric.renderer.api.EntitySelector;
import ke.ric.renderer.api.SnapshotBuilder;
import org.bukkit.entity.Player;

/** Both dependency examples compile the fluent API, not just an older capability method. */
public final class ConsumerExample {
    public static SnapshotBuilder photo(Renderer renderer, Player player) {
        return renderer.snapshot(player).fov(75).roll(5).visibleTo(player)
                .recipe(RenderRecipe.layers(RenderLayer.scene(),
                        RenderLayer.entities(EntitySelector.living())));
    }
}
