# Complete sample walkthrough

## Start with the new fluent examples

For a new integration, begin with the compiled [photo/stream quickstart](index.md), [small X-ray camera](xray-camera.md) or [creative lighting examples](creative-controls.md). They use `ke.ric:renderer-api:2.3.0-preview.3` and ShutterBug Renderer 1.1.0 with its matching embedded native library. Most ordinary capture/render/conversion ownership now belongs to the facade.

Use [Examples 1.3.0](https://github.com/kernel-person/shutterbug-renderer-examples/releases/tag/v1.3.0) with Renderer 1.1.0 and the public preview.3 SDK for Postcards, Redstone Camera, Painter's Easel and Admin POV Monitor. The provider is purchased and installed separately.

The lower-level v1.0.2 sample and Examples 1.2.0 references below are retained as historical release references. Their versioned source demonstrates explicit orchestration and consumer gameplay; it does not imply that an older provider supports new controls.

## Advanced low-level sample

The sibling [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.2/sample-plugin) is a buildable external consumer, not pseudocode. With Java 21, run `mvn -B clean install`, then `mvn -B -f sample-plugin/pom.xml clean verify` from the exported SDK repository.

Follow its lifecycle in order:

1. The Bukkit entry point remains API-free so `softdepend` can start when the SDK/provider is absent.
2. The isolated runtime attaches immediately when the service exists and listens for asynchronous service registration without double activation.
3. It creates one client owned by the plugin, reads capabilities, and chooses only advertised profiles/attachments.
4. The sample explicitly prepares cold regions asynchronously, then passes loaded evidence to bounded capture and rejects stale lifecycle state. The renderer API itself never silently loads missing chunks.
5. It submits a bounded job, polls monotonic progress, observes cancellation/timeout, and handles typed failures.
6. It inspects immutable results, timings, and visibility, then performs PNG/map conversion off-thread.
7. Disable closes polling, tasks, assets, and the client. A provider reload requires discovery, a new client, and fresh capture.

Before adapting the sample, decide whether rendering is optional, bound your queues and retained output, preserve the [safe operator boundary](operator-guide.md), and keep provider-private packages out of your build.

## Four playable Java examples

For smaller starting points, use [shutterbug-renderer-examples](https://github.com/kernel-person/shutterbug-renderer-examples). Each module is independently installable, depends on the public SDK with provided scope, and requires the separately installed commercial provider. None handles activation credentials or includes native binaries.

Build the examples repository with `mvn clean verify` on JDK21 or newer. Copy any module's `target/Renderer*.jar` into your server's plugins folder and restart. The tested server runtime is Java25, Paper26.2 and Minecraft26.2; see [compatibility](compatibility.md).

1. **Postcards:** run `/postcard` with an empty inventory slot. Expect a persistent map of your view. Read its short command handler, then its lifecycle and map helpers.
2. **Redstone Camera:** as an operator, run `/rendercamera` and place the dispenser. Its lens faces toward the placer in one of six orthogonal directions: north, south, east, west, up or down. Aim through placement; free-angle yaw/pitch is not supported. An accepted redstone-triggered capture emits a brief white flash outside the lens (enable client particles), then the finished map appears in the inventory. The flash means capture started, not that it succeeded; wait for the high success tone before collecting the photo. Sustained power does not repeatedly photograph; busy, unavailable and full-inventory requests do not flash. Break and place a new camera to change its aim.
3. **Painter's Easel:** as an operator, run `/easel`, place the easel, and brush-right-click its canvas for one fixed reference capture. Put cyan, magenta, or yellow dye in your offhand, then hold right-click and drag the brush across the map; punching also paints. The roughly 24-pixel round brush joins nearby input samples into continuous strokes. Sneak-right-click collects the painting. Capture happens once, not on each stroke.

4. **Admin POV Monitor:** as an operator with `rendererexamples.pov`, run `/pov ExactOnlinePlayerName` and hold the supplied map in either hand. `/pov monitor` supplies a desk monitor; place it with two blocks of height and space in front. Watch your own loaded screen within 16 blocks with line of sight to keep the feed active. `/pov stop` blanks the feed. Anyone can see or photograph the receiver or monitor, including copied maps; there is no private mode. Command access and monitor placement/removal remain owner-controlled. The view is periodic rendering of the target's eye position and yaw/pitch, not screen sharing: no HUD, chat, inventory, client shaders or audio.

POV uses 128 by 128 CLASSIC frames, a 16-block radius, two capture chunks per tick and one native worker. At most two target feeds and one capture/render pipeline run globally. The default target is four updates per second per feed. `/pov demo 4` selects your self-view and sets the shared server-run target rate; integers 1–20 are accepted, and a restart restores 4. Plain `/pov demo` keeps the current rate. This is best-effort, not a latency guarantee: slow work skips intervals, failures back off and delayed frames show STALE. Raising the target never adds parallel jobs or queued frames. No frames are archived. Permission loss, target disappearance, teleport/world changes and provider reload invalidate applicable work. No chunks are generated or synchronously loaded.

For optional native visuals, host the examples' `renderer-examples-resourcepack-1.1.0.zip` over player-reachable HTTPS and configure the server to offer it. Examples 1.2.0 include this byte-identical pack unchanged. Compute the SHA-1 from the exact file served (`shasum -a 1 renderer-examples-resourcepack-1.1.0.zip` on macOS or `sha1sum` on Linux); marketplace repacking may change ZIP bytes. Do not reuse the historical 1.0.0 pack hash. Set `native-models: true` in both camera and POV plugin configurations. In `plugins/RendererPaintersEasel/config.yml`, set `model-item: village_trades:painters_easel` and `brush-model-item: village_trades:paintbrush`, then restart. The pack adds camera optics, a thick opaque linen backing behind the live easel map, the original brush and a desk monitor around a real map. No Nexo or extra plugin is needed, and vanilla items are not globally replaced. Players must accept the pack. It targets Minecraft 26.2; the latest visual acceptance status is recorded separately in the examples repository.

### Record a solo demonstration

Use `/pov YourExactPlayerName` or `/pov demo` to target yourself; both use the same photographable receiver. Hold the map in your offhand, slowly turn between scenes and pause for snapshots. Face your desk monitor for several updates to capture the previous completed frame inside the next one. Finish with `/pov stop`. Label the clip **solo self-view demo**, not another player's POV or real-time screen sharing. For another player's view, use a second legitimate account/client or an invited tester; keep server authentication enabled. A solo clip does not establish multi-user command authorization or measured rendering latency.

The [recording guide](https://github.com/kernel-person/shutterbug-renderer-examples/blob/main/docs/recording-guide.md) includes shot lists for painting, camera flash/photo pickup and POV. Final examples acceptance and the separate marketplace buyer-activation gate must pass before treating the product as sales-ready.

The examples repository README contains the full controls, permissions, persistence, and adaptation notes. Its acceptance document separates human client playtesting from automated tests and from final packaged-artifact verification. The examples have no economy dependency.

You may adapt the Apache-2.0 example source for free or paid add-ons, retaining required notices. Tell buyers “Requires ShutterBug Renderer, purchased separately.” Distribute your add-on only, not the commercial provider.
