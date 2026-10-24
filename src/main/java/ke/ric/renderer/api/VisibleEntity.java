package ke.ric.renderer.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity and primary-ray pixel coverage of an entity in a rendered image.
 * This describes captured evidence, not a live Bukkit entity. Transparent contributors that
 * do not own the primary ray are not counted; names are labels and never identity keys.
 *
 * @param uuid stable captured entity UUID
 * @param objectId nonzero native object identity within this render result
 * @param type nonblank namespaced entity type, such as minecraft:player
 * @param name captured display label, which may be null
 * @param visiblePixels number of image pixels whose primary ray is owned by this entity
 */
public record VisibleEntity(UUID uuid, int objectId, String type, String name, int visiblePixels) {
    /**
     * Constructs a validated immutable value.
     * @throws IllegalArgumentException for blank type, zero object ID or negative coverage
     * @throws NullPointerException if uuid or type is null
     */
    public VisibleEntity {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(type, "type");
        if (type.isBlank() || objectId == 0 || visiblePixels < 0)
            throw new IllegalArgumentException("visible entity requires a type, nonzero object ID and nonnegative pixels");
    }

    /**
     * Tests the captured type without consulting a live Bukkit entity.
     * @return true exactly when type is minecraft:player
     */
    public boolean player() { return type.equals("minecraft:player"); }

    /**
     * Returns this entity's fraction of all native-pass image pixels. This is not reconstructed
     * from a display name or from final composited colours.
     * @param totalPixels positive width×height for the corresponding render result
     * @return primary-ray coverage in 0..1
     * @throws IllegalArgumentException if totalPixels is not positive or smaller than visiblePixels
     */
    public double coverage(int totalPixels) {
        if (totalPixels <= 0 || visiblePixels > totalPixels)
            throw new IllegalArgumentException("total pixels must include all visible entity pixels");
        return (double) visiblePixels / totalPixels;
    }
}
