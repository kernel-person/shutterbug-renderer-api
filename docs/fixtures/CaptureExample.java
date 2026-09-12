package docs.examples;

import ke.ric.renderer.api.CaptureRequest;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RendererClient;
import org.bukkit.Location;

import java.util.concurrent.CompletionStage;

public final class CaptureExample {
    public static CompletionStage<RenderResult> render(RendererClient client, Location camera) {
        RenderSettings settings = RenderSettings.classic(128, 128)
                .withProfile(RenderSettings.Profile.NORMAL);
        return client.capture(new CaptureRequest(camera, 32.0, settings, 4))
                .thenCompose(scene -> client.submit(new RenderJob(1, scene, settings)).completion());
    }
}
