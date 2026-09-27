# Upgrade and rollback

Read [release notes](release-notes.md), confirm [compatibility](compatibility.md), and back up `plugins/ShutterBugRenderer/config.yml` before changing files. Drain or cancel tasks and stop the server. Replace only `ShutterBugRenderer.jar` with the JAR from the archive for that platform; its native is embedded, verified, and extracted automatically. Preserve the protected `license-key` field, then start and verify product identity, activation, capabilities, and one disposable render.

The free SDK tag `v1.0.1` keeps the existing v1 API/binary baseline. Recompile consumers to catch build configuration drift, but no source migration is required from the earlier v1 SDK distribution. Do not shade the SDK or replace API classes inside a provider.

For rollback, stop the server and restore the previous `ShutterBugRenderer.jar` plus its matching configuration. Do not copy or manage an extracted native cache as a release input. Recapture scenes and register assets again after either upgrade or rollback because clients, scenes, handles, and tasks belong to one provider generation.
