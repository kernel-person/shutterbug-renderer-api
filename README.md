# ShutterBug Renderer API

This is the free Apache-2.0 compile-time API for an independently installed ShutterBug Renderer provider. Its compilation baseline is Java 21; the Paper 26.2 / Minecraft 26.2 server runs on Java 25. The initial provider delivery targets macOS ARM64 and Linux x86-64. Windows is coming soon and is not yet supported.

Use the `v1.0.1` JitPack release with `provided` scope; do not shade or bundle the API. The commercial provider remains Renderer 1.0.0; this SDK distribution preserves the v1 public API and binary baseline.

```xml
<repositories><repository><id>jitpack.io</id><url>https://jitpack.io</url></repository></repositories>
<dependency>
  <groupId>com.github.kernel-person</groupId>
  <artifactId>shutterbug-renderer-api</artifactId>
  <version>v1.0.1</version>
  <scope>provided</scope>
</dependency>
```

Declare `softdepend: [ShutterBugRenderer]` only when your plugin can work without rendering. Its Bukkit entrypoint must contain no API imports or symbolic API references: keep it API-free, check availability by name, and reflectively load an isolated API runtime after that check. The isolated runtime owns the client and closes it during disable. See the [lifecycle guide](docs/lifecycle.md) and the complete [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.1/sample-plugin).

Run `mvn -B clean install` and then `mvn -B -f sample-plugin/pom.xml clean verify` with Java 21 to compile the API and sample, execute their tests, and verify the exact v1 binary surface.

Build developer documentation with `python3 -m pip install -r requirements-docs.txt`, then run `mvn -B package` followed by `python3 tools/verify_public_renderer_docs.py --api-jar target/shutterbug-renderer-api-1.0.0.jar --bukkit-jar ~/.m2/repository/org/spigotmc/spigot-api/1.21.10-R0.1-SNAPSHOT/spigot-api-1.21.10-R0.1-SNAPSHOT.jar --javadocs target/apidocs --site-dir target/site`. The verifier compiles every displayed Java fixture against this release JAR, builds MkDocs strictly, assembles the generated Javadocs into the deployable `target/site/apidocs/` tree, and rejects any missing link from the [complete type reference](docs/api-reference.md). The source is in `docs/`, every rendered Java example is a compile-owned fixture, and the setup page owns executable Maven and Gradle consumer builds.
