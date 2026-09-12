package ke.ric.renderer.sample;

record ColdCoordinate(long generation, int blockX, int blockZ) {
    static ColdCoordinate forGeneration(long generation) {
        if (generation <= 0 || generation > 100_000_000L) {
            throw new IllegalArgumentException("cold scene generation is out of range");
        }
        long column = generation % 1024L;
        long row = generation / 1024L;
        return new ColdCoordinate(generation,
                Math.toIntExact(16_000L + column * 64L),
                Math.toIntExact(-16_000L - row * 64L));
    }

    int chunkX() {
        return blockX >> 4;
    }

    int chunkZ() {
        return blockZ >> 4;
    }

    int cameraBlockZ() {
        return Math.addExact(blockZ, 4);
    }
}
