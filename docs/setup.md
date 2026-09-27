# Maven and Gradle setup

Compile against the SDK with Java 21 or newer. Run Paper 26.2 / Minecraft 26.2 with Java 25; the SDK's Java 21 compilation baseline is not the server's runtime requirement. Resolve the SDK from JitPack as a compile-only/provided dependency. Do not shade, relocate, bundle, or extract its classes into your plugin; the installed provider owns the runtime API classes.

## Maven

```xml
--8<-- "consumer/maven/pom.xml"
```

## Gradle

```groovy
--8<-- "consumer/gradle/build.gradle"
```

Both build inputs are compiled from clean consumer directories by release verification. This shared probe is also compile-owned:

```java
--8<-- "fixtures/ConsumerExample.java"
```

Use `depend: [ShutterBugRenderer]` when rendering is mandatory. Use `softdepend: [ShutterBugRenderer]` only when the plugin remains useful without rendering; optional consumers must follow the API-free entry-point pattern in [lifecycle](lifecycle.md). The complete [sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.1/sample-plugin) demonstrates the optional pattern.
