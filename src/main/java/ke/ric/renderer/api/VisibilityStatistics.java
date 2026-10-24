package ke.ric.renderer.api;

import java.util.Map;

/**
 * Immutable counts of visible semantic evidence and covered pixels in a completed frame.
 * Maps are defensive immutable snapshots.
 * @param entities visible entity-key counts produced by this render
 * @param materials visible material-key counts produced by this render
 * @param structures visible structure-key counts produced by this render
 * @param biomes visible biome-key counts produced by this render
 * @param skyPixels number of pixels classified as sky
 * @param totalPixels total number of frame pixels
 */
public record VisibilityStatistics(Map<String,Integer> entities, Map<String,Integer> materials,
                                   Map<String,Integer> structures, Map<String,Integer> biomes,
                                   int skyPixels, int totalPixels) {
    public VisibilityStatistics { entities=Map.copyOf(entities); materials=Map.copyOf(materials); structures=Map.copyOf(structures); biomes=Map.copyOf(biomes); }
    /** Returns empty semantic maps and classifies all supplied frame pixels as sky. */
    public static VisibilityStatistics empty(int pixels) { return new VisibilityStatistics(Map.of(), Map.of(), Map.of(), Map.of(), pixels, pixels); }
}
