# Operator installation and activation

Renderer 1.1.0 supplies the fluent/creative API used by the immutable `2.3.0-preview.3` SDK. Upgrade the installed provider and its matching platform JAR together with any dependent plugin changes; an SDK dependency alone does not upgrade Renderer 1.0.0. See [compatibility](compatibility.md).

ShutterBug Renderer 1.1.0 is a separately installed commercial provider by Kernel Person. Stop the server and copy only the personalized `ShutterBugRenderer.jar` from your MCModels download for the server platform into `plugins/`. The matching native `.so`, `.dylib` or `.dll` is embedded in that JAR, checksum-verified, and extracted automatically to the provider's managed cache. Do not install a separate native file. Start the server and read the provider startup result before enabling dependent plugins. The supported platforms are macOS ARM64, Linux x86-64 and Windows x86-64. Use the archive matching the server operating system and CPU architecture.

## Activate safely

Automatic activation is designed for a personalized MCModels download: when the Renderer-specific buyer flow is verified and enabled, leave `plugins/ShutterBugRenderer/config.yml`'s `license-key` field blank and the JAR's purchase information supplies activation. That production buyer flow is currently pending separate verification; do not treat SDK publication or a generic download as evidence that automatic issuance is enabled. Start Paper on Java 25 with Paper 26.2 / Minecraft 26.2 and wait for the provider startup result. A non-personalized or incomplete JAR cannot activate merely because it was copied into `plugins/`.

If support supplies a separate manual activation value, start Paper once to generate `plugins/ShutterBugRenderer/config.yml`, stop the server, and set that value in `license-key`. An explicitly configured manual value takes precedence over the personalized-download information for fresh activation. Restrict the config and its backups so only the Paper server account can read them. Never print or copy activation values into logs, shell history, tickets, screenshots, startup arguments, or public issues. Restart after changing the value; do not hot-edit provider files while jobs are running.

Successful activation means the provider reports the expected product identity, the service becomes available, and capabilities report both native availability and production packaging. If any check fails, leave dependent plugins in optional/fallback mode and follow [troubleshooting](troubleshooting.md). Never weaken a check or copy a license between customers to make startup continue.

## Leases and seats

One purchase permits three active hardware installations. Each signed lease has a six-hour validity window; renewal normally happens while the provider is running, and expired evidence fails closed. A valid signed cached lease can support a restart during a short activation-service outage until it expires; the cache is time-limited and is not a permanent activation bypass.

A clean shutdown closes the provider and clears local runtime state, but it does not send a backend deactivation request and therefore must not be presented as immediately freeing a remote hardware seat. For a permanent host change or a stale seat, stop the old server, retain its sanitized logs, and follow the supported operator/admin process for a hardware-seat change or reset. There is no customer self-service endpoint documented by this provider.

Do not automate repeated activation attempts. Treat `not entitled`, `seat limit`, `lease unavailable`, clock/trust, and product mismatch as operator actions, not renderer retries. Support may ask for product version, platform key, provider startup code, sanitized capability output, and artifact checksums; never include the license value or full environment dumps.

## Disable or remove

Stop submissions, disable dependent plugins, and perform a clean server shutdown before removing the provider. Keep the release archive and its checksums for rollback. Removing the provider does not remove the free API from developer builds, but hard-dependent plugins will no longer start.

## Saved photos in Renderer 1.1.0

Renderer 1.1.0 saves photo palettes and stable identities in `plugins/ShutterBugRenderer/photos/`. A successful `captureMap()` finishes persistence before returning the item. Streams use temporary maps and never enter this store. Normal startup indexes records asynchronously, restores valid map bindings on the server thread and waits for worlds that load later. Startup status distinguishes indexed photos from maps actually restored.

Administrators with `shutterbugrenderer.admin` can use:

| Command | Purpose |
| --- | --- |
| `/renderer status` | Check render/photo readiness, indexed and restored totals, storage issues and unavailable worlds. |
| `/renderer storage` | Inspect saved-map counts and actual disk usage grouped by creator plugin. |
| `/renderer repair` | Repair the tagged renderer photo in your main hand, retaining photo identity and item metadata. |

Storage notices default to 10,000 saved maps or 256 MiB. Configure `photos.warn-maps` and `photos.warn-bytes`; zero disables that notice. They report at startup when already exceeded and when crossing a threshold. Notices neither delete photos nor prevent saving. `photos.palette-cache-maps` bounds the palette cache, defaults to 64, and accepts 1–4096.

Back up the **world save, vanilla map data and global map counter, inventories/item metadata, provider photo storage and consumer-owned data together**. A photo palette alone is not a matching vanilla map backup. Keep original records and backups while investigating corruption; one bad record does not discard the library.

If saving fails, check the reported storage path, free space and filesystem permissions, correct the problem and retry the photograph. An unhealthy photo store does not imply raw rendering is unavailable. If the source world is unavailable, load that world before restoration/repair can complete.

If map identity differs after restoring mismatched backups, the provider preserves the photo bytes and refuses to overwrite an unrelated map. Use a consistent backup, or hold a tagged photo and run `/renderer repair` to reuse a valid binding or allocate a safe replacement. Repair rechecks the held item before delivery. Missing or corrupt source records require recovery from backup.

If allocation reports a rolled-back global map counter, restore matching vanilla map data/counter and restart. The provider does not rewrite world files automatically or overwrite occupied IDs. Repair cannot bypass an unsafe allocator.
