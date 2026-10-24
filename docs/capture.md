# Capture images and saved photos

Start with the compiled [quickstart](index.md). `renderer.snapshot(player).capture()` returns a future of `RenderedFrame`; `captureMap()` returns a future of a durably saved map item. Their success/error callback overloads run on the server thread. Raw captures allocate no Minecraft maps.

Defaults are CLASSIC, 128×128 pixels, 70 degrees vertical FOV and a 16-block radius. Living entities use their eye pose at capture time and exclude themselves; nonliving entities use their location. A location source is copied when the builder is created. Set `.resolution(width, height)`, `.profile(...)`, `.fov(...)`, `.roll(...)`, `.radius(...)` or `.settings(...)` as needed. Builders are immutable.

Required chunks must already be loaded. Snapshots default to at most two chunks per tick. `.chunksPerTick(...)` adjusts the capture budget; it does not authorize loading cold chunks. Cancelling the returned future propagates to active capture/render/conversion work. Do not block the server thread with `join()` or `get()`.

## Privacy and exclusions

`.excluding(entities)` accepts a collection of entities, including a list or set. Use `.excludingIds(ids)` for UUID collections. Inputs are copied, entity identities are sampled on the server thread, and exclusions apply before visual capture. `.visibleTo(viewer)` adds that viewer's Bukkit visibility rules. Output recipients alone do not define privacy.

Live capture also omits Spectator players and players with a true Bukkit `vanished` metadata value. If several plugins provide the flag, any true value wins. Ordinary invisibility effects or having vanish permissions are not equivalent to active vanish. Packet-only visibility cannot be inferred reliably without integration.

Visibility is frozen into a snapshot. Changing game mode or vanishing later cannot recall pixels in previously distributed photos. Live streams recheck viewer visibility during reuse and before publication. Filters and layered recipes can only select permitted captured evidence.

## Durable map output

`captureMap()` captures, converts and saves before publishing an item. `frame.toMapItem()` does the same output step for an existing frame. By default, one photo fits a 128×128 map with black aspect-preserving letterboxing. `MapOutputOptions` selects palette dithering. Conversion and atomic photo-record writes run off-thread; map attachment/allocation happens on the server thread.

The provider owns the original palette and stable photo identity, creator and creation time. Photo items are tagged so an administrator can identify and repair them. Normal restart restoration checks the world, map ID, flags and persisted random marker before attaching pixels. Unavailable worlds, missing bindings and mismatched identities preserve the photo data for recovery. A raw capture, PNG save or live stream does not enter this store.

A successful save does not guarantee inventory delivery: the consuming plugin owns offline players, full inventories, item metadata and mailboxes. Cancellation cannot undo a map allocation or atomic file replacement that has already completed. Photo storage and vanilla map saves are separate stores.

Back up **provider photo storage, world map data/counter, inventories and consumer data together**. See [operator recovery](operator-guide.md) before repairing a backup mismatch.

## Low-level capture remains available

Use `renderer.client()` or `renderer.session()` when composing exact entities, managing assets or submitting manual scenes. The existing constructors and capture methods remain available. This compiled fixture demonstrates the direct capture path:

```java
--8<-- "fixtures/CaptureExample.java"
```

The original four-argument `CaptureRequest` constructor still uses an empty exclusion set. The five-argument constructor defensively copies excluded UUIDs. The FOV-aware capture overload rejects unsupported values on older providers instead of pretending they were applied. Do not reuse scenes or assets across provider generations.

## Maps inside photographs

Empty visible item frames use the installed Minecraft/resource-pack frame model:
wooden border, inset backing, normal/glow textures, and all six mounting directions.
Invisible empty frames stay absent. This does not add rendering of arbitrary items
placed inside frames. Filled-map pixels, rotation and photographic feedback are unchanged.

Filled maps in ordinary or glow item frames use their last available map pixels as
frozen scene textures. This includes the POV example's handheld map and desk screen:
`/pov ExactOnlinePlayerName` and `/pov demo` use the same shareable, photographable screen. There is
no private-screen mode or privacy-based gray replacement. Admin permissions still
control who can start or change a feed; copied maps and other viewers can see its image.

Capturing a screen does not start another render. Looking into your own POV monitor
therefore produces feedback across successive completed frames, not recursive work
inside a single capture.

For third-party contextual maps (different pixels per viewer), the provider copies
one existing complete cached view, using the same viewer for all contextual layers.
It prefers an existing shared/null context; otherwise it deterministically selects
the lowest player UUID with complete cached layers. This is not guaranteed to match
the photographer's particular UI. Renderer callbacks and cursors are not captured.

A map whose custom renderer has not drawn yet, whose cached layers are incomplete,
or whose server layout is unsupported still has no usable image and gets the normal
unavailable-image placeholder. Let the map display normally first. This technical
missing-data fallback does not censor available pixels.
