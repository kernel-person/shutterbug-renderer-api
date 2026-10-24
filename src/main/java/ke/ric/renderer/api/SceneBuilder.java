package ke.ric.renderer.api;

/**
 * Builds a complete caller-supplied engine-neutral scene owned by one {@link RendererClient}.
 * Missing world, chunk, block, entity, structure, or resolved visual evidence is rejected rather
 * than guessed by the provider.
 */
public interface SceneBuilder {
    /** Sets positive output dimensions in pixels; these must match the submitted {@link RenderSettings}. */
    SceneBuilder dimensions(int width, int height);
    /**
     * Sets the camera in world-block coordinates and orientation/field of view in radians.
     * @param x world X coordinate in blocks
     * @param y world Y coordinate in blocks
     * @param z world Z coordinate in blocks
     * @param yawRadians horizontal orientation, in radians (not Bukkit degrees)
     * @param pitchRadians vertical orientation, in radians (not Bukkit degrees)
     * @param fovRadians camera field of view, in radians
     * @return this builder
     */
    SceneBuilder camera(double x, double y, double z, double yawRadians, double pitchRadians, double fovRadians);
    /** Sets a camera including local roll; older providers accept only zero roll. */
    default SceneBuilder camera(SceneCamera camera) {
        java.util.Objects.requireNonNull(camera, "camera");
        if (camera.rollRadians() != 0)
            throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,
                    "camera roll is not supported by this provider");
        return camera(camera.x(), camera.y(), camera.z(), camera.yawRadians(), camera.pitchRadians(), camera.fovRadians());
    }
    /** Sets world/environment names, time in Minecraft ticks, and minimum/maximum build heights in blocks. */
    SceneBuilder world(String namespacedEnvironment, String worldName, long time, int minHeight, int maxHeight);
    /** Sets explicit rain and snow evidence; this does not query or mutate any live world. */
    SceneBuilder weather(boolean raining, boolean snowy);
    /** Adds one world-block sample with namespaced state/biome keys and block/sky light levels in 0..15. */
    SceneBuilder block(int x, int y, int z, String namespacedBlockState, int blockLight, int skyLight, String biome);
    /** Adds resolved immutable entity visuals and their stable nonzero object ID; metadata alone cannot supply missing visuals. */
    SceneBuilder entity(SceneEntity entity);
    /** Adds one structure key and bounds. */
    SceneBuilder structure(SceneStructure structure);
    /** Records legacy section presence at section coordinates (block coordinates divided by 16): false means missing;
     * true permits empty or populated evidence. Use the state overload to distinguish known empty from paletted sections. */
    SceneBuilder chunkSection(int sectionX,int sectionY,int sectionZ,boolean present);
    /** Records MISSING, known EMPTY, or PALETTED evidence for a section; known air must not be recorded as missing. */
    default SceneBuilder chunkSection(int sectionX,int sectionY,int sectionZ,ChunkSectionState state){return chunkSection(sectionX,sectionY,sectionZ,switch(state){case MISSING->false;case EMPTY,PALETTED->true;});}
    /** Marks chunk coordinates (block coordinates divided by 16) as required; every world-height section needs explicit evidence. */
    SceneBuilder requireChunk(int chunkX, int chunkZ);
    /** Returns an immutable scene or throws a typed failure such as MISSING_CHUNK; the provider never invents absent evidence. */
    Scene build();
}
