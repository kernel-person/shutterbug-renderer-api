# Troubleshooting and support diagnostics

Start at the first failing boundary:

1. Confirm Java 25, Paper 26.2, Minecraft 26.2, and a [supported platform](compatibility.md). Java 21 is the SDK compilation baseline, not this server's runtime requirement. Windows is coming soon and not yet supported.
2. Verify `ShutterBugRenderer.jar` came from the Renderer 1.0.0 archive for this platform and matches its published checksum. The native is embedded and extracted automatically; do not copy, rename, or install a separate `.dll`, `.so`, or `.dylib`.
3. Read the provider startup result. The purchase permits three active hardware installations and signed leases expire after six hours. For activation, lease, seat, trust, hardware-change, or product-mismatch errors, stop retries and follow the supported operator/admin process in [operator activation](operator-guide.md).
4. Confirm `RendererService` appears. Optional consumers should remain usable and attach when the service registers; hard dependencies should fail clearly.
5. Inspect `rendererVersion`, platform, `nativeAvailable`, `packagedForProduction`, supported profiles, and supported attachments. Do not dump object internals or environment variables.
6. Reproduce with the [sample plugin](sample-walkthrough.md), one small Classic render, no optional attachments, and no custom assets. Add one feature at a time.
7. Use the [typed failure recovery table](failures.md). After reload, discard old clients, scenes, asset handles, and tasks.

A support bundle may contain UTC timestamp, product and SDK versions, Paper/Minecraft versions, platform key, sanitized provider startup code, capability snapshot, failure code, task phase/status, artifact SHA-256 values, and the smallest reproduction. Remove player data, world paths, network addresses, environment dumps, license values, bearer tokens, purchase identifiers, and private plugin source. Preserve the original logs locally.
