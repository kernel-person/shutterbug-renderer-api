# ShutterBug Renderer API

The free compile-time API lets Java plugins request rendering from an independently installed provider. Version 1.0.0 supports Java 21 with the Paper 26.2 adapter for Minecraft 1.21.10 on macOS ARM64 and Linux x86-64. Windows is not supported in v1, and other Paper revisions are outside this adapter contract. Start with [setup](setup.md), then follow the [sample walkthrough](sample-walkthrough.md).

The API contains consumer contracts and converters. It does not bundle a renderer runtime.
