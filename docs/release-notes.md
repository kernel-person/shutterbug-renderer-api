# Release notes

## Renderer 1.1.0

Renderer 1.1.0 pairs the commercial provider and embedded native library with the existing **`ke.ric:renderer-api:2.3.0-preview.3`** SDK. It adds the fluent snapshot/stream API, persistent photos, scene reuse and creative controls described below. Platform archives are available for macOS ARM64, Linux x86-64 and Windows x86-64; the documented server runtime is Paper 26.2 / Minecraft 26.2 on Java 25. See [compatibility](compatibility.md) and [upgrade steps](upgrading.md).

This provider release does not republish the SDK: preview.3 binary, source and JavaDoc archives, versioned reference pages, checksums and earlier releases remain unchanged. The preview suffix remains part of the immutable SDK coordinate. Personalized buyer activation remains a separate verification gate.

## Examples 1.3.0

[Examples 1.3.0](https://github.com/kernel-person/shutterbug-renderer-examples/releases/tag/v1.3.0) pairs Postcards, Redstone Camera, Painter's Easel and Admin POV Monitor with the public `2.3.0-preview.3` SDK and separately installed Renderer 1.1.0. The free examples include their source and plugin JARs; they do not include the commercial provider. See the [sample walkthrough](sample-walkthrough.md) for controls and setup. Historical Examples 1.2.0 and its SDK v1.0.2 references remain unchanged.

## SDK 2.3.0-preview.3 — fluent and creative controls

This immutable SDK adds `Renderer.forPlugin(...)`, immutable snapshot/stream builders, automatic saved-photo map output, temporary handheld/screens and advanced session/client access. It includes collection exclusions, content/environment/appearance controls, reusable layered recipes, roll, tint/solid colour, independent ambient/sun/block lighting, point/spot lights, stage progress and capture provenance.

Entity layers respect scene occlusion by default. Through-wall rendering is explicit with `Occlusion.SELECTED_ONLY`; request privacy/exclusions remain authoritative. Live feeds target 20 FPS and use bounded scene reuse by default, with `.fresh()` for a capture each frame. Actual delivery depends on resolution, scene, lighting and server load.

The SDK ships matching source and JavaDoc companions and parameter metadata for IDE help. Originally published for the matching development provider, it is now paired with Renderer 1.1.0. Stable Maven `2.2`, earlier previews and JitPack tags remain unchanged.

The original public constructors and v1 binary baseline remain preserved. The entity-layer default is a change within the previously unpublished development API. See [setup](setup.md), [creative controls](creative-controls.md) and [compatibility](compatibility.md).

## SDK v1.0.2

This published release adds target-specific entity exclusions to `CaptureRequest`: callers can pass an immutable set of UUIDs to omit before visual capture. The original four-argument constructor remains available with an empty exclusion set. Existing v1 binaries remain compatible, and the frozen v1 baseline is unchanged. Source consumers using record deconstruction or record-component reflection must account for the fifth component; see [upgrading](upgrading.md). The immutable `v1.0.2` tag and JitPack build are published and verified through fresh Maven and Gradle consumers; no previous tag is moved or rewritten. The public JitPack JAR also passes the API boundary and frozen binary compatibility checks. See the [SDK release](https://github.com/kernel-person/shutterbug-renderer-api/releases/tag/v1.0.2).

The guides describe photographable shared POV screens, best-effort 4 Hz default cadence with `/pov demo 1` through `/pov demo 20`, six-direction camera mounting, and captured map pixels. Published [Examples 1.2.0](https://github.com/kernel-person/shutterbug-renderer-examples/releases/tag/v1.2.0) use this SDK and reuse the byte-identical `renderer-examples-resourcepack-1.1.0.zip`. Free-source publication does not establish matching-provider Paper acceptance or genuine buyer activation. The later Renderer 1.0.0 delivery passed Windows server acceptance on 18 October 2026; this does not change the immutable SDK or example artifacts.

## SDK v1.0.1 (previous release)

The free SDK distribution is already published at JitPack tag `v1.0.1`. Its public packages, signatures, and v1 binary baseline are unchanged. This documentation update adds personalized-download activation guidance and current example controls without changing or retagging the SDK. The existing guides also cover Maven and Gradle consumer builds, public-type Javadocs, compatibility, recovery, and deterministic documentation/export verification.

## Renderer 1.0.0

Renderer 1.0.0 is the previous commercial release. The MCModels personalized download is designed to activate automatically; a configured manual value remains an override and signed cached leases support bounded restarts. One purchase allows three active hardware installations with six-hour leases. Its first delivery supported macOS ARM64 and Linux x86-64. The later three-platform delivery passed Windows x86-64 server acceptance on 18 October 2026, including initial activation, cached restart and clean shutdown on Windows Server 2025, Paper 26.2 build 129 and Java 25. Client visuals and genuine personalized buyer activation were not established by those server checks.
