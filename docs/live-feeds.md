# Live cameras and screens

A feed starts with `renderer.stream(camera)` and ends with `stop()` or `close()` on its `RenderStream`. The [quickstart](index.md) contains a compiled handheld example. Snapshot and stream builders share FOV, roll, quality, radius, exclusions, content, environment, appearance, lighting and recipe controls.

## Choose a camera and output

An entity source follows its eye position if living, otherwise its location, and excludes that entity. A fixed `Location` is copied. A location supplier is evaluated on the server thread when useful work can begin: return `null` to end the feed; a thrown exception fails it. Keep suppliers fast and do not load chunks in them.

`.toPlayer(viewer)` places one owned temporary map in a free inventory slot. It never replaces an existing item and rejects a full inventory. The image fits one map with aspect-preserving letterboxing. Disconnect removes that output.

`.toScreen(anchor, BlockFace.NORTH, 2, 2)` creates a two-by-two map screen. The anchor is a `Location` of the **upper-left backing block as viewed from the requested outward face**. Columns extend right and rows down. NORTH, SOUTH, EAST and WEST are supported. The wall must be solid, loaded and unobstructed. Existing item frames are rejected; the renderer creates temporary frames and rolls back partial setup if placement fails. With no explicit resolution, each grid cell contributes 128×128 pixels. An explicit resolution is fitted to the output while retaining aspect ratio.

Combine output calls to display the same feed in several places. `.onFrame(...)` receives frames without allocating maps and may be combined with outputs. `.toMapSink(...)` updates a receiver your plugin already owns; receiver identity, permissions and persistence remain your responsibility. Existing receivers must follow the public `MapSink` contract.

## Smoothness and freshness

Streams target **20 FPS**, with supported targets from 1 through 20. This is a scheduling target, not a throughput guarantee. Smooth mode can reframe world evidence for up to 500 ms, two blocks of camera movement and within the captured center chunk. Reframing updates camera pose, including camera-relative lights; it does not recapture moving entities or world geometry. `.fresh()` requests new world evidence every frame. `.reuse(Duration, displacement)` exposes explicit reuse bounds.

A fair coordinator shares a four-chunk-per-tick capture budget between convenience streams. Each stream keeps at most one render/conversion in flight. Temporary overload skips work instead of accumulating catch-up frames; obsolete frames cannot rewind a displayed feed. After one second without a usable frame, map output displays “No signal” until recovery.

Measure delivered FPS, camera age, world age and server performance separately. `cameraCapturedNanos()`, `worldCapturedNanos()` and `publishedNanos()` use the monotonic `System.nanoTime()` domain. World age starts at the first captured world read and survives reuse. Captured facts in `provenance()` remain unchanged by environment or camera overrides.

## Ownership, privacy and failure

Call `.visibleTo(viewer)` explicitly when a feed should respect that viewer's Bukkit visibility rules. Output recipients do not implicitly define privacy because a public screen may have many viewers. Visibility is rechecked during reuse and before publication; exclusions cannot be undone by an X-ray layer.

Source death/logout, a null location supplier, owner disable, provider replacement and affected world unload end or fail the relevant work. `.onError(...)` reports both permanent failures and recoverable skipped frames; normal temporary overload uses bounded retries. `status()`, `lastError()`, `lastFrame()` and `completion()` expose feed state. Callback consumers run on the server thread and should remain short.

Stopping removes only owned temporary items and frames. It preserves unrelated items and backing blocks. Losing the last output stops a feed unless a frame consumer remains. Live stream maps do not enter photo storage; [saved photos](capture.md) use explicit `captureMap()` or `toMapItem()` instead.
