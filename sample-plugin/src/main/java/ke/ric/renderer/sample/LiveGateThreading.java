package ke.ric.renderer.sample;

final class LiveGateThreading {
    private LiveGateThreading() {}

    static void requireAsyncCompletion(boolean primaryThread) {
        if (primaryThread) {
            throw new IllegalStateException("renderer completion ran on the Minecraft main thread");
        }
    }
}
