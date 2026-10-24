# Compatibility and platform status

## Renderer 1.1.0 and the fluent SDK

Pair **ShutterBug Renderer 1.1.0** with **`ke.ric:renderer-api:2.3.0-preview.3`** for fluent snapshots, live feeds, persistent photos, reuse and creative controls. Each commercial platform JAR embeds its matching native library. Updating a compile-time dependency alone does not upgrade an installed provider.

| Platform key | Renderer 1.1.0 status | Runtime |
| --- | --- | --- |
| `macos-aarch64` | Supported | macOS ARM64 |
| `linux-x86_64` | Supported | Linux x86-64 |
| `windows-x86_64` | Supported | Windows x86-64 |

Compile the SDK consumer on Java 21 or newer. The documented server runtime is Paper 26.2 build 129 / Minecraft 26.2 on Java 25. Support is for the packaged operating system/architecture pairs; other Paper revisions, server implementations, CPU architectures and operating systems are unverified. Windows support is based on the packaged provider/sample server checks; it does not claim Windows execution of the creative consumer or four-example probe. Server acceptance does not establish game-client visuals or a performance guarantee. Genuine personalized buyer activation remains pending separate verification.

The preview.3 coordinate, binary, sources, JavaDoc archive and versioned reference remain immutable. The stable Maven SDK `2.2`, preview.1, preview.2 and historical JitPack SDK v1.0.2 also remain unchanged. Binary compatibility preserves existing consumers; it does not make a new method or native lighting contract available in an old provider. Unsupported controls fail explicitly. Consumers should check runtime capabilities.

## Historical Renderer 1.0.0

Renderer 1.0.0 used `com.github.kernel-person:shutterbug-renderer-api:v1.0.2` and preserved the v1 public Java API and binary baseline. Its first delivery covered macOS ARM64 and Linux x86-64. A later three-platform delivery passed Windows acceptance on **18 October 2026**, so Windows is no longer a coming-soon platform for that accepted delivery.

That Windows server run used Windows Server 2025 10.0 amd64, Paper 26.2 build 129 / Minecraft 26.2 and Eclipse Adoptium Temurin Java 25.0.4.1+1. The exact packaged Java/native pair passed the canary, initial activation, cached restart and clean shutdown. No Windows game client was launched. Same-release macOS checks ran natively on ARM64; Linux checks used Docker linux/amd64 emulation on the Mac, not a native Linux host. These historical results do not establish acceptance of a different artifact or genuine personalized buyer activation.

The frozen adapter identifier `paper-26.2-minecraft-1.21.10` retains its historical compile-target name for compatibility; it is not an instruction to run Paper 26.2 on Java 21. Upgrading the commercial provider does not grant consumers permission to compile against private implementation packages.
