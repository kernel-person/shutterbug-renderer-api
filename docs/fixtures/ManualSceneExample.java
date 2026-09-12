package docs.examples;

import ke.ric.renderer.api.ChunkSectionState;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.Scene;

public final class ManualSceneExample {
    public static Scene create(RendererClient client) {
        return client.newSceneBuilder().dimensions(64, 64)
                .camera(0.5, 2.0, 5.0, 0.0, 0.0, Math.toRadians(70.0))
                .world("minecraft:overworld", "manual", 6_000L, 0, 16)
                .weather(false, false).block(0, 0, 0, "minecraft:stone", 0, 15, "minecraft:plains")
                .requireChunk(0, 0).chunkSection(0, 0, 0, ChunkSectionState.PALETTED).build();
    }
}
