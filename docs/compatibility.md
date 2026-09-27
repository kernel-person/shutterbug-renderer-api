# Compatibility and platform status

The commercial product is ShutterBug Renderer 1.0.0. The free SDK distribution coordinate is `com.github.kernel-person:shutterbug-renderer-api:v1.0.1`; it preserves the v1 public Java API and binary baseline.

| Platform key | Launch status | Runtime |
| --- | --- | --- |
| `macos-aarch64` | Supported | macOS ARM64 |
| `linux-x86_64` | Supported | Linux x86-64 |
| `windows-x86_64` | Coming soon; not yet supported or included | Windows x86-64 future target |

Windows x86-64 must remain pending until the exact packaged Java/native pair passes the Windows canary and Paper acceptance gate. A Windows archive or workflow entry is not evidence of runtime support. Do not advertise or deploy Windows production support before the release evidence changes this matrix.

The initial delivery contains macOS ARM64 and Linux x86-64 only. Windows acceptance does not block this two-platform release, but Windows support must not be claimed until its own runtime tests pass.

The SDK compilation baseline is Java 21. The server runtime is Paper 26.2 / Minecraft 26.2 on Java 25. The frozen adapter identifier `paper-26.2-minecraft-1.21.10` retains its historical compile-target name for compatibility; it is not an instruction to run Paper 26.2 on Java 21. Other Paper revisions, server implementations, CPU architectures, and operating systems are unverified. Consumers should check runtime capabilities rather than infer them from the host name.

Patch releases of the SDK may improve docs, samples, and packaging while retaining the v1 binary baseline. A future incompatible API requires a new major contract and coordinate; upgrading the commercial provider does not grant consumers permission to compile against private implementation packages.
