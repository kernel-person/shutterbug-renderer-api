# Upgrade and rollback

## Moving to Renderer 1.1.0 and the fluent API

Pair the immutable `2.3.0-preview.3` SDK with ShutterBug Renderer 1.1.0 and the native library embedded in its platform JAR. Published SDK, sources and JavaDoc archives are unchanged. Update your provided/compile-only dependency and reload your IDE. A new SDK does not add support to an older provider. Existing constructors and the v1 binary baseline remain; you can migrate individual workflows to the facade without giving up manual scenes or attachments.

Start with one plugin-owned `Renderer`; replace ordinary photo pipelines with `snapshot(...).captureMap(...)` and monitors with `stream(...).start()`. Keep your permissions, cooldowns, recipient delivery and gameplay policies. `captureMap()` now saves provider-owned photo data, while raw captures and streams allocate no durable photos. Back up provider photo storage, world map data/counter and inventories together before changing deployments.

If adapting earlier development recipes, entity layers now respect scene occlusion by default. Add `Occlusion.SELECTED_ONLY` only where through-wall visibility is intentional. Keep exposure, final-layer brightness and FOV separate; check light limits before offering a rig to users.

## Commercial provider upgrades

Read [release notes](release-notes.md), confirm [compatibility](compatibility.md), and back up `plugins/ShutterBugRenderer/config.yml` before changing files. Drain or cancel tasks and stop the server. Replace only `ShutterBugRenderer.jar` with the personalized JAR from your download for that platform; its native is embedded, verified, and extracted automatically. Leave `license-key` blank only for a separately verified and enabled automatic MCModels buyer flow; otherwise preserve a protected support-supplied manual override. See the current [activation gate](operator-guide.md). Start and verify product identity, activation, capabilities, and one disposable render.

The published free SDK `v1.0.2` adds an immutable `excludedEntityIds` set to `CaptureRequest`, with a five-argument constructor and accessor. The original four-argument constructor still includes all otherwise renderable entities, and the existing v1 binary baseline is preserved. Constructor-based consumers need no source migration, but record patterns and record-component reflection must account for the fifth component when recompiling. New exclusion-aware consumers require the matching provider API. Do not shade the SDK or replace API classes inside a provider. Fresh Maven and Gradle consumers verified this release. Keep both `v1.0.1` and `v1.0.2` immutable; later documentation updates must not move or rewrite either tag.

For rollback, stop the server and restore the previous verified `ShutterBugRenderer.jar` plus its matching configuration. Do not copy or manage an extracted native cache as a release input. Recapture scenes and register assets again after either upgrade or rollback because clients, scenes, handles, and tasks belong to one provider generation.
