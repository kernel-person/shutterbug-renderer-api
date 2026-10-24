# ShutterBug Renderer

Take a photograph, stream a camera to a map, or compose a scene with your own lighting. Start with `Renderer.forPlugin(this)`; the same API keeps manual scenes, assets, attachments and low-level rendering available when you need them.

These developer guides use **`ke.ric:renderer-api:2.3.0-preview.3`**, the free Apache-2.0 SDK with the new fluent API and creative controls. It requires the matching development provider and native library. Installing this SDK alone does not upgrade an older provider. The stable `2.2` SDK, historical previews and JitPack `v1.0.2` remain available unchanged. See [setup and IDE documentation](setup.md) and [compatibility](compatibility.md) before choosing a build.

## A live camera in four lines

Create one `Renderer` with `Renderer.forPlugin(this)` in `onEnable()`. Then start a feed on the server thread, using an entity as `camera` and a player as `viewer`:

```java
--8<-- "fixtures/FluentExample.java:live-feed"
```

The viewer needs a free inventory slot for the live map. Keep `feed` and call `feed.stop()` when the monitor ends. To apply that viewer's visibility rules, also chain `.visibleTo(viewer)`.

## Your first photo and live feed

This complete class adds a saved photograph and plugin cleanup:

```java
--8<-- "fixtures/FluentExample.java"
```

A photo is durably saved before the callback receives its map item. A stream reuses temporary map output and cleans up when stopped or when your plugin disables. Call `stop()` on the returned stream when that particular monitor ends. Raw `capture()` returns an image frame without allocating maps. Your plugin decides permissions, cooldowns and delivery; a production photo workflow should retain delivery for a player who disconnects while capture is running.

Defaults are CLASSIC, 128×128 pixels, a 70-degree vertical field of view and a 16-block radius. Builders are immutable: keep the value returned by each setting. Living entity cameras use eye position and exclude themselves. Callback overloads run on the server thread; ordinary futures retain ordinary Java threading rules.

Continue with [capture and saved photos](capture.md), [live feeds](live-feeds.md), [creative controls](creative-controls.md), or the [small X-ray example](xray-camera.md). The [API reference](api-reference.md) links every public type and the JavaDoc that also powers IDE hover descriptions.

## For server owners

Begin with [installation and activation](operator-guide.md). The commercial provider is installed separately and owns the renderer runtime. The free SDK does not contain or unlock it. The SDK compiles on Java 21; the documented Paper 26.2 / Minecraft 26.2 server requires Java 25. Development-preview documentation does not announce a new commercial provider release or add platform support.

## Build free or paid add-ons

Both are welcome. You may sell plugins that depend on ShutterBug Renderer. Tell buyers that the provider is required and purchased separately: the server owner needs a valid provider license within its seat limits, not every player or a separate purchase per add-on on the same server. Do not redistribute the commercial provider, native binaries or activation credentials.

The [published four-example release](https://github.com/kernel-person/shutterbug-renderer-examples/releases/tag/v1.2.0) contains Postcards, Redstone Camera, Painter's Easel and Admin POV Monitor. That historical release uses SDK v1.0.2; its source is not evidence that an older provider supports the new fluent API. This guide's compiled examples use the current preview. Albums, collections, posing, item delivery and other gameplay belong to the consuming plugin.
