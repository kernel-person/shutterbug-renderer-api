package ke.ric.renderer.api;

import java.util.Map;

/**
 * Immutable counts of visible semantic evidence and covered pixels in a completed frame.
 * Maps are defensive immutable snapshots.
 */
public record VisibilityStatistics(Map<String,Integer> entities, Map<String,Integer> materials,
                                   Map<String,Integer> structures, Map<String,Integer> biomes,
                                   int skyPixels, int totalPixels) {
    public VisibilityStatistics { entities=Map.copyOf(entities); materials=Map.copyOf(materials); structures=Map.copyOf(structures); biomes=Map.copyOf(biomes); }
    public static VisibilityStatistics empty(int pixels) { return new VisibilityStatistics(Map.of(), Map.of(), Map.of(), Map.of(), pixels, pixels); }
}
