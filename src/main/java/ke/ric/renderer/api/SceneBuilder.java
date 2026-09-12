package ke.ric.renderer.api;

/**
 * Builds a complete caller-supplied engine-neutral scene owned by one {@link RendererClient}.
 * Missing world, chunk, block, entity, structure, or resolved visual evidence is rejected rather
 * than guessed by the provider.
 */
public interface SceneBuilder {
    /** Sets the positive output dimensions. */
    SceneBuilder dimensions(int width, int height);
    /** Sets camera position, orientation, and field of view in radians. */
    SceneBuilder camera(double x, double y, double z, double yawRadians, double pitchRadians, double fovRadians);
    /** Sets immutable world identity, time, and build-height evidence. */
    SceneBuilder world(String namespacedEnvironment, String worldName, long time, int minHeight, int maxHeight);
    /** Sets captured rain and snow state. */
    SceneBuilder weather(boolean raining, boolean snowy);
    /** Adds one exact block-state, light, and biome sample. */
    SceneBuilder block(int x, int y, int z, String namespacedBlockState, int blockLight, int skyLight, String biome);
    /** Adds one resolved entity and its stable object identity. */
    SceneBuilder entity(SceneEntity entity);
    /** Adds one structure key and bounds. */
    SceneBuilder structure(SceneStructure structure);
    /** Records whether a 16x16x16 chunk section is present. */
    SceneBuilder chunkSection(int sectionX,int sectionY,int sectionZ,boolean present);
    /** Records the exact evidence state for a 16x16x16 chunk section. */
    default SceneBuilder chunkSection(int sectionX,int sectionY,int sectionZ,ChunkSectionState state){return chunkSection(sectionX,sectionY,sectionZ,switch(state){case MISSING->false;case EMPTY,PALETTED->true;});}
    /** Marks a chunk as required for scene completeness. */
    SceneBuilder requireChunk(int chunkX, int chunkZ);
    /** Validates and returns an immutable provider-owned scene. */
    Scene build();
}
