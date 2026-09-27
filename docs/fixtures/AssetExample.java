package docs.examples;

import ke.ric.renderer.api.AssetHandle;
import ke.ric.renderer.api.AssetPack;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.Scene;

import java.util.List;

public final class AssetExample {
    public record AssetSubmission(AssetHandle asset, RenderTask task) implements AutoCloseable {
        @Override
        public void close() {
            asset.close();
        }
    }

    public static AssetSubmission submit(RendererClient client, Scene scene, byte[] zip) {
        AssetHandle asset = client.registerAsset(new AssetPack("consumer-pack.zip", zip));
        try {
            RenderSettings settings = RenderSettings.classic(scene.width(), scene.height());
            RenderTask task = client.submit(new RenderJob(1, scene, settings, List.of(asset.contentHash())));
            return new AssetSubmission(asset, task);
        } catch (RuntimeException failure) {
            asset.close();
            throw failure;
        }
    }
}
