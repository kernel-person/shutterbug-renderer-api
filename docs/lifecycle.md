# Lifecycle and discovery

For a hard dependency, direct API imports are safe: load `RendererService`, create one client for your plugin, retain it in the lifecycle owner, and call `close()` during disable. After a provider reload, discover a new service and client; do not retain old clients or scenes. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).

```java
--8<-- "fixtures/LifecycleExample.java"
```

For `softdepend`, the Bukkit entry point must contain no API imports or symbolic API references, because the provided API can be absent before discovery. Use an API-free entry point that reflectively loads an isolated API runtime only after checking availability—the same pattern as the complete sample.

```java
--8<-- "fixtures/OptionalProviderEntrypoint.java"
```

```java
--8<-- "fixtures/OptionalRendererRuntime.java"
```
