# Lifecycle and discovery

## Plugin-owned facade

For the new API, create one `Renderer.forPlugin(this)` in `onEnable()` and close it from `onDisable()`, on the server thread. The [quickstart](index.md) is a complete compiled example. The facade owns capture/render work, listeners, temporary streams and a bounded conversion worker. Owner disable also triggers cleanup automatically. Saved provider photos survive this cleanup.

Service discovery is lazy: when no provider is ready, a new operation fails with `PROVIDER_DISABLED`. Provider replacement invalidates existing work; later requests can discover the new provider. Do not carry captured scenes or assets across generations. Affected world unload also invalidates pending work. Use the facade's callback overloads for Bukkit delivery and keep callbacks short.

`renderer.session()` and `renderer.client()` retain the advanced API. If you manage a separate low-level client, you own its lifecycle as shown below.

## Direct clients and optional providers

For a hard dependency, direct API imports are safe: load `RendererService`, create one client for your plugin, retain it in the lifecycle owner, and call `close()` during disable. After a provider reload, discover a new service and client; do not retain old clients, assets, scenes, or tasks. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.2/sample-plugin).

```java
--8<-- "fixtures/LifecycleExample.java"
```

For `softdepend`, the Bukkit entry point must contain no API imports or symbolic API references, because the provided API can be absent before discovery. Use an API-free entry point that reflectively loads an isolated API runtime only after checking availability. Retry discovery when the asynchronously starting provider registers `RendererService`; do not busy-loop or enable twice.

```java
--8<-- "fixtures/OptionalProviderEntrypoint.java"
```

```java
--8<-- "fixtures/OptionalRendererRuntime.java"
```
