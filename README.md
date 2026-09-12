# ShutterBug Renderer API

This is the free Apache-2.0 compile-time API for an independently installed ShutterBug Renderer provider. It targets Java 21, Paper 26.2, and Minecraft 1.21.10.

Use the `v1.0.0` JitPack release with `provided` scope; do not shade or bundle the API.

```xml
<repositories><repository><id>jitpack.io</id><url>https://jitpack.io</url></repository></repositories>
<dependency>
  <groupId>com.github.kernel-person</groupId>
  <artifactId>shutterbug-renderer-api</artifactId>
  <version>v1.0.0</version>
  <scope>provided</scope>
</dependency>
```

Declare `softdepend: [ShutterBugRenderer]` only when your plugin can work without rendering. Its Bukkit entrypoint must contain no API imports or symbolic API references: keep it API-free, check availability by name, and reflectively load an isolated API runtime after that check. The isolated runtime owns the client and closes it during disable. See the [lifecycle guide](docs/lifecycle.md) and the complete [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).

Run `mvn -B clean install` and then `mvn -B -f sample-plugin/pom.xml clean verify` with Java 21 to compile the API and sample, execute their tests, and verify the exact v1 binary surface.

Build developer documentation with `python3 -m pip install -r requirements-docs.txt` and `mkdocs build --strict`; the source is in `docs/` and every rendered Java example is a compile-owned fixture. With Java 21, run `python3 tools/verify_public_renderer_docs.py --api-jar target/shutterbug-renderer-api-1.0.0.jar --bukkit-jar ~/.m2/repository/org/spigotmc/spigot-api/1.21.10-R0.1-SNAPSHOT/spigot-api-1.21.10-R0.1-SNAPSHOT.jar` after `mvn -B package` to compile every displayed Java fixture against this release JAR.
