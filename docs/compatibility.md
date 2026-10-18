# Compatibility and platform status

The commercial product is ShutterBug Renderer 1.0.0. The free SDK distribution coordinate is `com.github.kernel-person:shutterbug-renderer-api:v1.0.2`; it preserves the v1 public Java API and binary baseline.

| Platform key | Launch status | Runtime |
| --- | --- | --- |
| `macos-aarch64` | Supported | macOS ARM64 |
| `linux-x86_64` | Supported | Linux x86-64 |
| `windows-x86_64` | Supported on the tested runtime below | Windows x86-64 |

The current Renderer 1.0.0 delivery includes macOS ARM64, Linux x86-64, and Windows x86-64. Windows acceptance on 2026-10-18 used Windows Server 2025 10.0 (amd64), Paper 26.2 build 129 / Minecraft 26.2, and Java 25 (Eclipse Adoptium Temurin 25.0.4.1+1). The exact packaged Java/native pair passed its Windows canary and Paper initial-start, cached-restart, and shutdown checks. This is server-runtime acceptance, not Windows-client visual acceptance or a performance guarantee.

Other Windows versions and ARM Windows remain unverified. A Windows archive, workflow entry, or linked DLL alone is not evidence of runtime support. The same release also passed macOS ARM64 native server checks and Linux x86-64 server checks under Docker amd64 emulation; this is not a claim of native Linux-host testing.

The SDK compilation baseline is Java 21. The server runtime is Paper 26.2 / Minecraft 26.2 on Java 25. The frozen adapter identifier `paper-26.2-minecraft-1.21.10` retains its historical compile-target name for compatibility; it is not an instruction to run Paper 26.2 on Java 21. Other Paper revisions, server implementations, CPU architectures, and operating systems are unverified. Consumers should check runtime capabilities rather than infer them from the host name.

Patch releases of the SDK may improve docs, samples, and packaging while retaining the v1 binary baseline. A future incompatible API requires a new major contract and coordinate; upgrading the commercial provider does not grant consumers permission to compile against private implementation packages.
