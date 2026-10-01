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

## Four playable Java examples

For smaller starting points, use [shutterbug-renderer-examples](https://github.com/kernel-person/shutterbug-renderer-examples). Each module is independently installable, depends on the public SDK with provided scope, and requires the separately installed commercial provider. None handles activation credentials or includes native binaries.

Build the examples repository with `mvn clean verify` on JDK21 or newer. Copy any module's `target/Renderer*.jar` into your server's plugins folder and restart. The tested server runtime is Java25, Paper26.2 and Minecraft26.2; see [compatibility](compatibility.md).

1. **Postcards:** run `/postcard` with an empty inventory slot. Expect a persistent map of your view. Read its short command handler, then its lifecycle and map helpers.
2. **Redstone Camera:** as an operator, run `/rendercamera`, look toward your subject and place the dispenser. Placement stores your exact yaw and up/down pitch. An accepted redstone-triggered capture emits a brief white flash outside the lens (enable client particles), then the finished map appears in the inventory. The flash means capture started, not that it succeeded; wait for the high success tone before collecting the photo. Sustained power does not repeatedly photograph; busy, unavailable and full-inventory requests do not flash. Break and place a new camera to change its aim.
3. **Painter's Easel:** as an operator, run `/easel`, place the easel, and brush-right-click its canvas for one fixed reference capture. Put cyan, magenta, or yellow dye in your offhand, then hold right-click and drag the brush across the map; punching also paints. The roughly 24-pixel round brush joins nearby input samples into continuous strokes. Sneak-right-click collects the painting. Capture happens once, not on each stroke.

4. **Admin POV Monitor:** as an operator with `rendererexamples.pov`, run `/pov ExactOnlinePlayerName` and hold the supplied map in either hand. `/pov monitor` supplies a desk monitor; place it with two blocks of height and space in front. Watch your own loaded screen within 16 blocks with line of sight. `/pov stop` blanks the feed. Other players cannot watch through your receiver. The view is periodic rendering of the target's eye position and yaw/pitch, not screen sharing: no HUD, chat, inventory, client shaders or audio.

POV uses 128 by 128 CLASSIC frames, a 16-block radius, two capture chunks per tick and one native worker. At most two target feeds and one capture/render pipeline run globally. A one-second scheduling interval is best-effort, not a latency guarantee; slow work skips intervals, failures back off and delayed frames show STALE. No frames are archived. Permission loss, target disappearance, teleport/world changes and provider reload invalidate applicable work. No chunks are generated or synchronously loaded.

For optional native visuals, host the examples' `renderer-examples-resourcepack-1.1.0.zip` over player-reachable HTTPS and configure the server to offer it. Examples 1.1.1 reuse this pack unchanged. Compute the SHA-1 from the exact file served (`shasum -a 1 renderer-examples-resourcepack-1.1.0.zip` on macOS or `sha1sum` on Linux); marketplace repacking may change ZIP bytes. Do not reuse the historical 1.0.0 pack hash. Set `native-models: true` in both camera and POV plugin configurations. In `plugins/RendererPaintersEasel/config.yml`, set `model-item: village_trades:painters_easel` and `brush-model-item: village_trades:paintbrush`, then restart. The pack adds camera optics, a thick opaque linen backing behind the live easel map, the original brush and a desk monitor around a real map. No Nexo or extra plugin is needed, and vanilla items are not globally replaced. Players must accept the pack. It targets Minecraft 26.2; the latest visual acceptance status is recorded separately in the examples repository.

### Record a solo demonstration

Use `/pov YourExactPlayerName` to target yourself. Hold the map in your offhand, slowly turn between scenes and pause for snapshots. Show the desk monitor and finish with `/pov stop`. Label the clip **solo self-view demo**, not another player's POV or real-time screen sharing. For another player's view, use a second legitimate account/client or an invited tester; keep server authentication enabled. A solo clip does not establish multi-user privacy or measured rendering latency.

The [recording guide](https://github.com/kernel-person/shutterbug-renderer-examples/blob/main/docs/recording-guide.md) includes shot lists for painting, camera flash/photo pickup and POV. Final examples acceptance and the separate marketplace buyer-activation gate must pass before treating the product as sales-ready.

The examples repository README contains the full controls, permissions, persistence, and adaptation notes. Its acceptance document separates human client playtesting from automated tests and from final packaged-artifact verification. The examples have no economy dependency.

You may adapt the Apache-2.0 example source for free or paid add-ons, retaining required notices. Tell buyers “Requires ShutterBug Renderer, purchased separately.” Distribute your add-on only, not the commercial provider.
