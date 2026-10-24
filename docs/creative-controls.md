# Camera controls, layers and lighting

The same immutable controls work on snapshots and streams. These examples use only the public SDK and Bukkit types; each method returns a request you can finish with `capture()` or `captureMap()`.

```java
--8<-- "fixtures/CreativeControlsExample.java"
```

## Camera, environment and exposure

A `Location` supplies position, yaw and pitch. Living entity sources sample eye position and exclude themselves. `.fov(degrees)` changes vertical field of view and must be finite, greater than zero and less than 180. `.roll(degrees)` rotates around the viewing direction; it defaults to zero and must be finite. Advanced `SceneCamera` uses radians for roll.

`RenderEnvironment.captured()` inherits source time and weather. `.timeOfDay(6000)` or `.weather(Weather.CLEAR)` overrides rendering without changing the server world. With no weather override, rain/snow state follows captured local evidence. `.weather(Weather.RAIN)` or `.weather(Weather.SNOW)` explicitly chooses rendered precipitation without editing captured biomes.

Exposure changes lighting and tone mapping inside the renderer. Layer brightness multiplies the resulting RGB values. FOV, exposure and brightness are separate controls. Colour treatment runs first, then brightness, then opacity. `LayerColor.tint(0xRRGGBB)` multiplies colour; `solid(0xRRGGBB)` replaces it; `original()` preserves rendered colour. These treatments leave alpha unchanged until composition.

## Selection, occlusion and privacy

`SceneContent.all()`, `blocksOnly()` and `entitiesOnly()` select geometry. `EntitySelector` selects captured entities by UUID, type, living classification or players, and composes with `and`, `or` and `not`. `.excluding(entities)` accepts an entity collection, including lists and sets. `.excludingIds(ids)` accepts a UUID collection. Collection inputs are copied.

Entity layers default to **`Occlusion.RESPECT_SCENE`**. Blocks and nonselected entities can hide a selected entity, making visible-only highlighting a natural default. Explicit **`SELECTED_ONLY`** enables through-wall output; selected entities still obscure each other. It changes camera visibility while retaining the surrounding geometry for lighting, reflections and shadows.

Request-level content removal is stronger: `SceneContent.entitiesOnly()` removes blocks from all rays, including geometric shadows. Hiding a wall from output with an entity layer is therefore different from deleting its captured evidence. Use `.visibleTo(viewer)` for per-viewer Bukkit visibility rules. Request exclusions and privacy remain authoritative: selection and X-ray cannot bring excluded evidence back.

## Independent layers

Use `RenderRecipe.layers(...)` to compose ordered passes from one capture. Each layer can override content, environment, appearance and lighting; unspecified values inherit the request. Layer content intersects request content. Every pass derives independently from the same original capture, so a night setting or studio light rig in one layer does not leak into the next.

The final image, PNG and map outputs contain the composition. Read `frame.passes()` for native results and visibility of each pass. A multi-pass image has no single unambiguous object/depth buffer: `result()` and combined visible-entity getters reject that ambiguity. For a single pass, `getVisiblePlayers()` and `getVisibleEntities()` return immutable captured identities with UUIDs and visible-pixel coverage.

Advanced `SceneTransform(content, environment, appearance)` inherits existing lighting and primary visibility. In its six-argument overload, null lighting inherits, as does a null occlusion/selector pair. Explicit `RenderLighting.world()`, `Occlusion.RESPECT_SCENE` and `EntitySelector.all()` reset those controls.

## Build a light rig

`RenderLighting.world()` inherits existing lighting and preserves its original rendering path when unchanged. `studio()` starts with zero inherited ambient, sunlight and block light. Set `.ambient(factor)`, `.sun(factor)` and `.block(factor)` independently; block control affects captured block illumination and coloured emitters. Sun direction follows rendered time unless `.sunDirection(x, y, z)` overrides the normalized direction from the surface toward the sun.

`PointLight` has a position. `SpotLight` adds direction and full inner/outer cone angles in degrees, fading smoothly between them. Both use RGB colour, artistic intensity, range in blocks and optional hard shadows. `LightSpace.WORLD` uses absolute world coordinates. `CAMERA` means right/up/forward and follows camera position, yaw, pitch and roll, including during scene reuse. The studio example therefore creates a camera-mounted flash without a Bukkit entity or a world mutation.

Initial per-pass limits are **16 virtual lights**, **eight shadow-casting lights**, **64 blocks maximum range**, and intensity **0–64**. Ambient, sun and block factors also accept 0–64. Range must be positive. Cone angles require an inner angle at least zero, less than the outer angle, with the outer at most 180 degrees. Query `renderer.client().lightingCapabilities()` for provider limits. Invalid/excess requests and unsupported providers fail explicitly before rendering; requested virtual lights and shadows work in every supported quality profile.

Lighting uses actual hit normals and artistic diffuse response, including transparent surfaces and supported reflections. Shadow rays respect alpha cutouts; nonzero blended surfaces block hard shadows. Soft shadows, coloured transmission, arbitrary shaders, editable fog/biomes and transparent-background matting are outside this preview.

## Progress and captured facts

`.onProgress(...)` delivers `FrameProgress` on the server thread at most once per tick. Stages are `CAPTURE`, `RENDER`, `CONVERSION` and `SAVING`; `basisPoints()` measures work within that stage, with 10,000 meaning complete. Render progress identifies the zero-based pass and pass count. There is no estimated overall percentage, and fast transitions may be coalesced. Cancellation and lifecycle loss suppress late callbacks. Keep handlers short; normal futures retain Java continuation semantics.

`frame.provenance()` contains captured world UUID/name/environment, source camera, wall-clock capture instant, monotonic capture interval, full/day time, local precipitation, thunder and camera biome. `effectiveCamera()` describes the actual rendering camera, including roll. Overrides and reframing preserve source facts. Optional provenance may be absent for older providers or manually constructed evidence.

Batched capture retains and deduplicates generated-structure evidence. It requires loaded chunks and never silently loads missing scenery. Image filters, multi-map grouping, inventory delivery, albums, collections, posing and other gameplay remain consumer responsibilities.
