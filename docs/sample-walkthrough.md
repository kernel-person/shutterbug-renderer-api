# Complete sample walkthrough

The sibling [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.1/sample-plugin) is a buildable external consumer, not pseudocode. With Java 21, run `mvn -B clean install`, then `mvn -B -f sample-plugin/pom.xml clean verify` from the exported SDK repository.

Follow its lifecycle in order:

1. The Bukkit entry point remains API-free so `softdepend` can start when the SDK/provider is absent.
2. The isolated runtime attaches immediately when the service exists and listens for asynchronous service registration without double activation.
3. It creates one client owned by the plugin, reads capabilities, and chooses only advertised profiles/attachments.
4. It prepares cold regions without blocking Paper's main thread, captures a complete scene in bounded batches, and rejects stale lifecycle state.
5. It submits a bounded job, polls monotonic progress, observes cancellation/timeout, and handles typed failures.
6. It inspects immutable results, timings, and visibility, then performs PNG/map conversion off-thread.
7. Disable closes polling, tasks, assets, and the client. A provider reload requires discovery, a new client, and fresh capture.

Before adapting the sample, decide whether rendering is optional, bound your queues and retained output, preserve the [safe operator boundary](operator-guide.md), and keep provider-private packages out of your build.

## Three playable Java examples

For smaller starting points, use [shutterbug-renderer-examples](https://github.com/kernel-person/shutterbug-renderer-examples). Each module is independently installable, depends on the public SDK with provided scope, and requires the separately installed commercial provider. None handles activation credentials or includes native binaries.

Build the examples repository with `mvn clean verify` on JDK21 or newer. Copy any module's `target/Renderer*.jar` into your server's plugins folder and restart. The tested server runtime is Java25, Paper26.2 and Minecraft26.2; see [compatibility](compatibility.md).

1. **Postcards:** run `/postcard` with an empty inventory slot. Expect a persistent map of your view. Read its short command handler, then its lifecycle and map helpers.
2. **Redstone Camera:** as an operator, run `/rendercamera`, look toward your subject and place the dispenser. Placement stores your exact yaw and up/down pitch even though the block looks cardinal. Power it for one map inside; sustained power does not repeatedly photograph. Break and place a new camera to change its aim.
3. **Painter's Easel:** as an operator, run `/easel`, place the easel, and brush-right-click its canvas for one fixed reference capture. Put cyan, magenta, or yellow dye in your offhand, then hold right-click and drag the brush across the map; punching also paints. The roughly 24-pixel round brush joins nearby input samples into continuous strokes. Sneak-right-click collects the painting. Capture happens once, not on each stroke.

For the optional native easel and brush visuals, host the [canonical, playtested source pack](https://raw.githubusercontent.com/kernel-person/shutterbug-renderer-examples/2baab5d1b9488a43d85456e6b6c73446ed626eb2/resourcepacks/village-trades-easel-resourcepack.zip) over player-reachable HTTPS and configure the server to offer it. Its server resource-pack SHA-1 is `c8e0b7506490ba0ff9a945dee127ab57befbd32a` for those exact ZIP bytes only. If instead you host the pack extracted from an MCModels examples download, compute the SHA-1 of the exact file you serve (`shasum -a 1 village-trades-easel-resourcepack.zip` on macOS or `sha1sum village-trades-easel-resourcepack.zip` on Linux); marketplace repacking changes ZIP bytes, so do not reuse the source-pack hash. In `plugins/RendererPaintersEasel/config.yml`, set `model-item: village_trades:painters_easel` and `brush-model-item: village_trades:paintbrush`, then restart. No Nexo or extra plugin is needed; players must accept the pack to see the models. Leaving those fields blank keeps the vanilla visuals. The pack targets Minecraft 26.2.

The examples repository README contains the full controls, permissions, persistence, and adaptation notes. Its acceptance document separates human client playtesting from automated tests and from final packaged-artifact verification. The examples have no economy dependency.

You may adapt the Apache-2.0 example source for free or paid add-ons, retaining required notices. Tell buyers “Requires ShutterBug Renderer, purchased separately.” Distribute your add-on only, not the commercial provider.
