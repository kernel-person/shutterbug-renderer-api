# Paper capture

`capture` is asynchronous. Required chunks must already be loaded, and `chunksPerTick` limits main-thread capture work. Do not synchronously load cold chunks or block the server thread for capture or rendering. The completion continuation has no general Paper-thread guarantee; return to the main thread before reading or mutating Bukkit state. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.2/sample-plugin).

```java
--8<-- "fixtures/CaptureExample.java"
```

SDK v1.0.2 adds the five-argument `CaptureRequest` constructor shown above. Its
`excludedEntityIds` set is defensively copied and omits matching entities before
their visual state is captured. Use it to omit a POV target's body without hiding
the shared map or monitor. The original four-argument constructor remains binary
compatible and defaults to an empty exclusion set. Exclusions do not recall pixels
already frozen into earlier scenes or maps.

## Player visibility

Live world captures omit players in **Spectator** mode and players with a true
Bukkit `vanished` metadata value. If multiple plugins provide this flag, any
true value wins. This happens before reading the player's name, equipment or
appearance, and applies to the standalone provider as well as ShutterBug photos.
ShutterBug selfies omit hidden photographers instead of failing the photograph;
its viewfinder refreshes entity visibility on every click.

ShutterBug's player-aware captures also respect
[`Player.canSee`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html#canSee(org.bukkit.entity.Player))
for the photographer. The `vanished` flag is used by
[EssentialsX](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/User.java)
and other vanish plugins. Merely having OP/vanish permissions, or ordinary
invisibility effects, is not treated as active vanish.

`CaptureRequest` is location-based and has no viewer identity. Therefore a vanish
plugin that only changes client packets or per-viewer visibility, without exposing
the `vanished` flag, needs an integration; it cannot be detected reliably by an
automated camera. Do not assume every `/vanish` implementation is supported.

Visibility is frozen **at capture time**. Changing game mode or vanishing later
does not erase earlier photos, frames already captured, or manually supplied
`SceneEntity` scene data. This is not a mechanism for recalling distributed images.

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
