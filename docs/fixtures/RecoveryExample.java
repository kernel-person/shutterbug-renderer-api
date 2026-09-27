package docs.examples;

import ke.ric.renderer.api.AssetHandle;
import ke.ric.renderer.api.AssetPack;
import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RenderJob;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RenderTask;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.Scene;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecoveryExample {
    public enum Action {
        RETRY_LATER,
        RECAPTURE,
        REREGISTER_ASSET_RECAPTURE_RESUBMIT,
        REDUCE_REQUEST,
        REDISCOVER_PROVIDER,
        REPORT
    }

    public static Action action(RendererException failure) {
        return action(failure.code());
    }

    public static Action action(RenderFailureCode code) {
        return switch (code) {
            case QUEUE_FULL -> Action.RETRY_LATER;
            case MISSING_CHUNK, STALE_SCENE -> Action.RECAPTURE;
            case MISSING_ASSET -> Action.REREGISTER_ASSET_RECAPTURE_RESUBMIT;
            case MEMORY_LIMIT, TIMEOUT -> Action.REDUCE_REQUEST;
            case CLIENT_CLOSED, PROVIDER_DISABLED -> Action.REDISCOVER_PROVIDER;
            default -> Action.REPORT;
        };
    }

    public static boolean mayRetryMissingAsset(RenderFailureCode code, int priorAssetRetries) {
        if (priorAssetRetries < 0) throw new IllegalArgumentException("negative retry count");
        return code == RenderFailureCode.MISSING_ASSET && priorAssetRetries == 0;
    }

    @FunctionalInterface
    public interface ReplacementSceneFactory {
        Scene rebuild(String replacementAssetHash);
    }

    public static AssetRecovery recoverMissingAssetOnce(RendererClient client, byte[] validatedZip,
                                                         ReplacementSceneFactory scenes,
                                                         RenderSettings settings,
                                                         int priorAssetRetries) {
        if (!mayRetryMissingAsset(RenderFailureCode.MISSING_ASSET, priorAssetRetries)) {
            throw new IllegalStateException("missing-asset recovery is limited to one attempt");
        }
        AssetHandle replacement = client.registerAsset(
                new AssetPack("consumer-pack.zip", validatedZip));
        try {
            String replacementHash = replacement.contentHash();
            Scene replacementScene = scenes.rebuild(replacementHash);
            RenderTask replacementTask = client.submit(new RenderJob(
                    1, replacementScene, settings, List.of(replacementHash)));
            AssetRecovery recovery = new AssetRecovery(replacement, replacementTask);
            replacementTask.completion().whenComplete((result, failure) -> recovery.close());
            return recovery;
        } catch (RuntimeException failure) {
            replacement.close();
            throw failure;
        }
    }

    public static final class AssetRecovery implements AutoCloseable {
        private final AssetHandle replacement;
        private final RenderTask task;
        private final AtomicBoolean closed = new AtomicBoolean();

        private AssetRecovery(AssetHandle replacement, RenderTask task) {
            this.replacement = replacement;
            this.task = task;
        }

        public AssetHandle replacement() {
            return replacement;
        }

        public RenderTask task() {
            return task;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) replacement.close();
        }
    }
}
