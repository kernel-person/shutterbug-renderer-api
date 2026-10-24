# Results, attachments, timings, and visibility

## Frames and provenance

`RenderedFrame.image()` / `toBufferedImage()` returns a new image of the final output. `savePng(...)` and `toMapItem(...)` convert asynchronously. For one pass, `result()` exposes native pixels, attachments, visibility and timings. `getVisiblePlayers()` and `getVisibleEntities()` return immutable identity records with UUIDs and visible-pixel counts; display names are not identities. Older results without native identity evidence report `UNSUPPORTED_CAPABILITY`, rather than inventing an empty list.

For layered recipes, inspect each `RenderPassResult` through `passes()`. The final composition has no single native depth/object/visibility buffer, so ambiguous aggregate evidence access is rejected. Conversion uses the final composed image.

`provenance()` separates captured facts from rendering overrides: source world, camera, capture interval, time/weather and camera biome. `effectiveCamera()` gives the actual rendering camera, including roll. These are optional where older/manual evidence cannot supply them. Source facts survive reframing and environment overrides. See [creative controls](creative-controls.md) and [feed freshness](live-feeds.md).

## Native attachments

RGBA8 color is always present. Request optional `DEPTH`, `NORMAL`, `MATERIAL_ID`, or `OBJECT_ID` only when `supportedAttachments()` advertises it. An unrequested accessor returns `null`; result arrays and buffers are caller-owned defensive copies.

`RenderTimings` contains measured nanosecond phases. `UNAVAILABLE` means a phase did not occur inside the provider measurement boundary; zero is a real measurement. Use timings for diagnostics and comparison, not billing or guaranteed performance. `VisibilityStatistics` provides immutable semantic counts and pixel coverage for the completed frame; it is evidence from that render, not a world query.

```java
--8<-- "fixtures/ResultInspectionExample.java"
```
