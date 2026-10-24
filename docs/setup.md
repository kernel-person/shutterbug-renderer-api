# Maven, Gradle and IDE setup

Use **`ke.ric:renderer-api:2.3.0-preview.3`** for the fluent snapshot/stream API, scene recipes and creative lighting. It requires the matching creative-controls development provider and native library. Stable `ke.ric:renderer-api:2.2`, earlier preview versions and the historical JitPack `v1.0.2` release remain unchanged and do not supply these additions. The preview is not a drop-in SDK upgrade for the currently sold provider.

Compile your plugin with Java 21 or newer. The documented Paper 26.2 / Minecraft 26.2 server requires Java 25. Use the API as a `provided` or `compileOnly` dependency; do not shade, relocate or bundle it. The separately installed `ShutterBugRenderer` supplies runtime API classes.

## Maven

```xml
--8<-- "consumer/maven/pom.xml"
```

## Gradle

```groovy
--8<-- "consumer/gradle/build.gradle"
```

The Bukkit dependency below is a Java 21 compile-time surface for these examples, not a promise that the provider runs against every Spigot/Paper version. Match the provider's exact Paper adapter when running it. Both consumer builds compile this fluent API probe:

```java
--8<-- "fixtures/ConsumerExample.java"
```

Use `depend: [ShutterBugRenderer]` when rendering is mandatory. Use `softdepend: [ShutterBugRenderer]` only when your plugin remains useful without rendering; follow the API-free entry-point pattern in [lifecycle](lifecycle.md).

## Descriptions and parameter names in your editor

The SDK has matching **sources and JavaDoc JARs**, plus Java parameter-name metadata. IDEs use these for method descriptions, units, defaults, argument help and source navigation. The same reference is [available in your browser](https://shutterbug-maven.pages.dev/javadocs/2.3.0-preview.3/index.html).

In IntelliJ IDEA, reload the Maven/Gradle project and use the dependency's **Download Sources** and **Download Documentation** actions if automatic downloading is disabled. Open **Quick Documentation** on a method for its description and parameter details.

In VS Code, reload the Maven project after changing versions. Enable **Java › Inlay Hints › Parameter Names: Enabled**, choosing `literals` or `all`. Inlay hints are editor decorations, not Java syntax. If stale missing-source results remain cached, run **Java: Clean Java Language Server Workspace**, then reload. Hover a method to see its documentation.

If autocomplete still lacks `Renderer`, `SnapshotBuilder` or `RenderLighting`, check the resolved dependency version and remove duplicate older SDK dependencies. A provider update cannot change the classes your IDE compiles against, and a newer compile dependency cannot add runtime support to an older provider.

## Keep advanced access

`renderer.session()` exposes lifecycle-aware capture/composition helpers, and `renderer.client()` exposes scene builders, assets, attachments, manual submission and provider capabilities. Start with the [quickstart](index.md); use the advanced pages when your integration needs them.
