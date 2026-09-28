# Operator installation and activation

ShutterBug Renderer 1.0.0 is a separately installed commercial provider by Kernel Person. Stop the server and copy only the personalized `ShutterBugRenderer.jar` from your MCModels download for the server platform into `plugins/`. The matching native `.so` or `.dylib` is embedded in that JAR, checksum-verified, and extracted automatically to the provider's managed cache. Do not install a separate native file. Start the server and read the provider startup result before enabling dependent plugins. The supported launch platforms are macOS ARM64 and Linux x86-64; Windows is coming soon, not included yet.

## Activate safely

For a personalized MCModels download, leave `plugins/ShutterBugRenderer/config.yml`'s `license-key` field blank. The JAR carries the purchase information needed for automatic activation; no manual entry is normally required. Start Paper on Java 25 with Paper 26.2 / Minecraft 26.2 and wait for the provider startup result. A non-personalized or incomplete JAR cannot activate merely because it was copied into `plugins/`.

If support supplies a separate manual activation value, start Paper once to generate `plugins/ShutterBugRenderer/config.yml`, stop the server, and set that value in `license-key`. An explicitly configured manual value takes precedence over the personalized-download information for fresh activation. Restrict the config and its backups so only the Paper server account can read them. Never print or copy activation values into logs, shell history, tickets, screenshots, startup arguments, or public issues. Restart after changing the value; do not hot-edit provider files while jobs are running.

Successful activation means the provider reports the expected product identity, the service becomes available, and capabilities report both native availability and production packaging. If any check fails, leave dependent plugins in optional/fallback mode and follow [troubleshooting](troubleshooting.md). Never weaken a check or copy a license between customers to make startup continue.

## Leases and seats

One purchase permits three active hardware installations. Each signed lease has a six-hour validity window; renewal normally happens while the provider is running, and expired evidence fails closed. A valid signed cached lease can support a restart during a short activation-service outage until it expires; the cache is time-limited and is not a permanent activation bypass.

A clean shutdown closes the provider and clears local runtime state, but it does not send a backend deactivation request and therefore must not be presented as immediately freeing a remote hardware seat. For a permanent host change or a stale seat, stop the old server, retain its sanitized logs, and follow the supported operator/admin process for a hardware-seat change or reset. There is no customer self-service endpoint documented by this provider.

Do not automate repeated activation attempts. Treat `not entitled`, `seat limit`, `lease unavailable`, clock/trust, and product mismatch as operator actions, not renderer retries. Support may ask for product version, platform key, provider startup code, sanitized capability output, and artifact checksums; never include the license value or full environment dumps.

## Disable or remove

Stop submissions, disable dependent plugins, and perform a clean server shutdown before removing the provider. Keep the release archive and its checksums for rollback. Removing the provider does not remove the free API from developer builds, but hard-dependent plugins will no longer start.
