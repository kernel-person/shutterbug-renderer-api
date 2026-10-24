# ShutterBug Renderer API

Photograph the world, stream a camera to a map, or compose a scene with custom lighting. The free Apache-2.0 SDK uses Java 21 and a separately installed ShutterBug Renderer provider. The documented Paper 26.2 / Minecraft 26.2 server requires Java 25.

The current fluent API is **`ke.ric:renderer-api:2.3.0-preview.3`**. Use it with **ShutterBug Renderer 1.1.0** and its matching embedded native library on macOS ARM64, Linux x86-64 or Windows x86-64. The preview.3 coordinate, binary, source and JavaDoc archives remain unchanged. The SDK does not include or unlock the commercial renderer. Historical stable `2.2`, previews and JitPack tags remain immutable; a new SDK dependency cannot add runtime support to an older provider.

```xml
<repositories><repository><id>shutterbug-renderer</id><url>https://shutterbug-maven.pages.dev/</url></repository></repositories>
<dependency>
  <groupId>ke.ric</groupId>
  <artifactId>renderer-api</artifactId>
  <version>2.3.0-preview.3</version>
  <scope>provided</scope>
</dependency>
```

Inside a plugin with `depend: [ShutterBugRenderer]`, create one facade on the server thread:

```java
var renderer = Renderer.forPlugin(this);
renderer.snapshot(player).fov(75).captureMap(this::deliverPhoto, this::reportFailure);
var stream = renderer.stream(cameraEntity).toPlayer(viewer).start();
```

`deliverPhoto` and `reportFailure` are your plugin's callbacks. Callbacks run on the server thread; the photo is saved before delivery. Your plugin decides inventory, offline delivery and permissions. Stop individual streams when no longer needed and close the facade in `onDisable()`. Owner disable also triggers cleanup. Raw `capture()` allocates no maps.

Builders are immutable. Defaults are CLASSIC, 128×128, 70 degrees vertical FOV and a 16-block radius. Set roll, environment, exposure, layers and lighting without changing the server world. Entity layers respect scene occlusion by default; through-wall behaviour is explicit. Advanced session/client access remains available.

Read the [quickstart](https://kernel-person.github.io/shutterbug-renderer-api/), [setup and IDE help](https://kernel-person.github.io/shutterbug-renderer-api/setup/), [creative controls](https://kernel-person.github.io/shutterbug-renderer-api/creative-controls/) and [complete JavaDoc](https://shutterbug-maven.pages.dev/javadocs/2.3.0-preview.3/index.html). The Maven release includes sources, JavaDoc and parameter-name metadata for autocomplete/hover descriptions. Do not shade, relocate or bundle the API.

Optional consumers using `softdepend` need an API-free Bukkit entry point because the API may be absent at class loading. Follow [lifecycle](https://kernel-person.github.io/shutterbug-renderer-api/lifecycle/) for that pattern. Your plugin owns gameplay, albums, filters, item delivery and permissions; the provider owns rendering and explicitly requested saved photos.

## Build and verify

With Java 21, run `mvn -B clean install`, then `mvn -B -f sample-plugin/pom.xml clean verify`. Build the concise runnable example with `mvn -B -f examples/xray-camera/pom.xml package`. The original public constructors and v1 binary baseline remain checked.

For developer documentation, install `requirements-docs.txt`, run `mvn -B package`, then run `python3 tools/verify_public_renderer_docs.py --api-jar target/renderer-api-2.3.0-preview.3.jar --bukkit-jar ~/.m2/repository/org/spigotmc/spigot-api/1.21.10-R0.1-SNAPSHOT/spigot-api-1.21.10-R0.1-SNAPSHOT.jar --javadocs target/apidocs --site-dir target/site`. The verifier compiles every website Java fixture against the public SDK and Bukkit, builds MkDocs strictly, and checks the complete generated JavaDoc link inventory.
