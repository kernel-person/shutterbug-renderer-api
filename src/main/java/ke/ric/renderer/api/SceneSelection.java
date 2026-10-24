package ke.ric.renderer.api;

import java.util.Objects;

/**
 * Selects evidence retained in an immutable scene derivative. Selection only removes evidence;
 * deriving again cannot restore blocks or entities removed from an earlier scene.
 * Requires a matching development provider implementing {@link RendererClient#deriveScene}; older providers reject it.
 * @param includeBlocks whether existing block geometry is retained (false retains known-air evidence)
 * @param entities non-null captured/caller-supplied entity category to retain
 */
public record SceneSelection(boolean includeBlocks, EntityMode entities) {
    public SceneSelection {
        Objects.requireNonNull(entities, "entities");
    }

    /** Entity evidence to retain, using the captured or caller-supplied living flag. */
    public enum EntityMode {
        /** Retains every existing entity, without recapturing or adding any. */
        ALL,
        /** Retains entities whose immutable evidence has the living flag set. */
        LIVING,
        /** Removes all existing entity visuals and labels. */
        NONE
    }

    /** Retains all block and entity evidence already present in the source. */
    public static SceneSelection all() { return new SceneSelection(true, EntityMode.ALL); }
    /** Removes block geometry and retains all existing entities, including nonliving ones. */
    public static SceneSelection entitiesOnly() { return new SceneSelection(false, EntityMode.ALL); }
    /** Removes block geometry and retains only entities already marked living. */
    public static SceneSelection livingEntitiesOnly() { return new SceneSelection(false, EntityMode.LIVING); }
}
