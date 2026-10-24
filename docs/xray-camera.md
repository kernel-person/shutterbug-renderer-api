# A small X-ray camera

This example uses the fluent **2.3.0-preview.3** SDK and the matching development provider/native library. The renderer supplies general selection, layering and colour controls; the addon supplies the X-ray recipe and its permission.

## The rendering request

```java
--8<-- "fixtures/XrayPipelineExample.java"
```

The first pass draws the scene at 20% brightness. The second draws living entities over it, explicitly using `Occlusion.SELECTED_ONLY` to see through unselected geometry. Both derive from one captured scene. Selected entities still obscure each other, and the scene remains available to shadow/light rays. Removing `SELECTED_ONLY` restores the default visible-only behaviour.

`visibleTo(viewer)` applies that viewer's Bukkit visibility rules. The camera entity is excluded automatically, and request exclusions/privacy cannot be undone by an X-ray pass. The final image is opaque; object-ID coverage is not a physically accurate transparency matte.

## Complete plugin

The [complete source](https://github.com/kernel-person/shutterbug-renderer-api/blob/main/examples/xray-camera/src/main/java/docs/examples/XrayCamera.java) is one Java class plus its [POM](https://github.com/kernel-person/shutterbug-renderer-api/blob/main/examples/xray-camera/pom.xml) and [plugin.yml](https://github.com/kernel-person/shutterbug-renderer-api/blob/main/examples/xray-camera/src/main/resources/plugin.yml). It calls `captureMap(...)`, so conversion and durable provider storage finish before delivery. It no longer owns capture futures, pixel loops, quantization or retained map renderers.

Build with Java 21 using `mvn -f examples/xray-camera/pom.xml package` in the public source checkout. The resulting `target/XrayCamera.jar` needs the matching development `ShutterBugRenderer` on its documented Paper adapter. The local renderer source tree stores this same example under `renderer-docs/examples/xray-camera/`.

Run `/xrayphoto` as a player with `xraycamera.use` (operator by default). A busy flag limits this small demonstration to one photograph at a time. Its main-thread callback checks online state and drops inventory overflow at the recipient's location. A production plugin should add its own offline delivery/mailbox and permissions/cooldown policy. Saved pixels remain in provider storage if the addon disables; pending callbacks are suppressed after owner shutdown.

This is an example of general render composition, not a special X-ray rendering path. [Creative controls](creative-controls.md) shows ordinary photography, visible-only tinting and a camera-relative studio light using the same primitives. Do not use through-wall output on servers where it is not authorized.
