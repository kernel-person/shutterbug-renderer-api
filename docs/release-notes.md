# Release notes

## Renderer 1.0.0 Windows delivery — 2026-10-18

The current commercial delivery includes macOS ARM64, Linux x86-64, and Windows x86-64. The exact Windows Java/native pair passed its canary and Paper initial-start, cached-restart, and shutdown acceptance on the [tested runtime](compatibility.md). This does not establish Windows-client visual acceptance of the four examples or genuine MCModels buyer activation; the buyer-flow gate remains separate.

The commercial product remains Renderer 1.0.0, the immutable free SDK coordinate remains `com.github.kernel-person:shutterbug-renderer-api:v1.0.2`, and the published examples remain 1.2.0 with resource pack 1.1.0. No SDK tag or released example asset is changed by this documentation follow-up.

## SDK v1.0.2

This published release adds target-specific entity exclusions to `CaptureRequest`: callers can pass an immutable set of UUIDs to omit before visual capture. The original four-argument constructor remains available with an empty exclusion set. Existing v1 binaries remain compatible, and the frozen v1 baseline is unchanged. Source consumers using record deconstruction or record-component reflection must account for the fifth component; see [upgrading](upgrading.md). The immutable `v1.0.2` tag and JitPack build are published and verified through fresh Maven and Gradle consumers; no previous tag is moved or rewritten. The public JitPack JAR also passes the API boundary and frozen binary compatibility checks. See the [SDK release](https://github.com/kernel-person/shutterbug-renderer-api/releases/tag/v1.0.2).

The guides describe photographable shared POV screens, best-effort 4 Hz default cadence with `/pov demo 1` through `/pov demo 20`, six-direction camera mounting, and captured map pixels. Published [Examples 1.2.0](https://github.com/kernel-person/shutterbug-renderer-examples/releases/tag/v1.2.0) use this SDK and reuse the byte-identical `renderer-examples-resourcepack-1.1.0.zip`. Free-source publication does not establish matching-provider Paper acceptance or genuine buyer activation. At this SDK publication, Windows remained coming soon and unsupported; current provider support is recorded above.

## SDK v1.0.1 (previous release)

The free SDK distribution is already published at JitPack tag `v1.0.1`. Its public packages, signatures, and v1 binary baseline are unchanged. This documentation update adds personalized-download activation guidance and current example controls without changing or retagging the SDK. The existing guides also cover Maven and Gradle consumer builds, public-type Javadocs, compatibility, recovery, and deterministic documentation/export verification.

## Renderer 1.0.0 initial two-platform delivery (historical)

The commercial product remains Renderer 1.0.0. The MCModels personalized download is designed to activate automatically; a configured manual value remains an override and signed cached leases support bounded restarts. One purchase allows three active hardware installations with six-hour leases. macOS ARM64 and Linux x86-64 were the documented launch platforms. At that initial delivery, Windows x86-64 was coming soon and not yet supported. The final personalized buyer flow and exact packaged release artifacts required separate acceptance; current provider acceptance is recorded above, while the buyer-flow gate remains separate.
