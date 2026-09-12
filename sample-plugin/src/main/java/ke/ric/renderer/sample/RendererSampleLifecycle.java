package ke.ric.renderer.sample;

import ke.ric.renderer.api.RenderFailureCode;
import ke.ric.renderer.api.RendererCapabilities;
import ke.ric.renderer.api.RendererClient;
import ke.ric.renderer.api.RendererException;
import ke.ric.renderer.api.RendererService;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns discovery and exactly one replaceable external renderer client. */
final class RendererSampleLifecycle implements AutoCloseable {
    private final Supplier<RendererService> discovery;
    private final Function<RendererService, RendererClient> activation;
    private final Consumer<String> unavailable;
    private final Consumer<RendererCapabilities> available;
    private RendererClient client;
    private boolean closed;

    RendererSampleLifecycle(
            Supplier<RendererService> discovery,
            Function<RendererService, RendererClient> activation,
            Consumer<String> unavailable,
            Consumer<RendererCapabilities> available) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
        this.available = Objects.requireNonNull(available, "available");
    }

    RendererClient activate() {
        if (closed) throw new IllegalStateException("renderer sample lifecycle is closed");
        RendererService service = discovery.get();
        if (service == null) {
            discardClient();
            unavailable.accept("renderer service unavailable; sample remains enabled");
            return null;
        }

        RendererClient candidate = null;
        try {
            candidate = Objects.requireNonNull(activation.apply(service), "renderer client");
            if (!candidate.active()) {
                throw new RendererException(RenderFailureCode.PROVIDER_DISABLED,
                        "renderer client activation unavailable");
            }
            RendererCapabilities capabilities = Objects.requireNonNull(
                    candidate.capabilities(), "renderer capabilities");
            RendererClient previous = client;
            client = candidate;
            if (previous != null && previous != candidate) previous.close();
            available.accept(capabilities);
            return candidate;
        } catch (RuntimeException failure) {
            if (candidate != null && candidate != client) candidate.close();
            discardClient();
            unavailable.accept("renderer activation unavailable ("
                    + failure.getClass().getSimpleName() + "): "
                    + Objects.toString(failure.getMessage(), "no detail")
                    + "; sample remains enabled");
            return null;
        }
    }

    RendererClient client() {
        return client;
    }

    private void discardClient() {
        RendererClient previous = client;
        client = null;
        if (previous != null) previous.close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        discardClient();
    }
}
