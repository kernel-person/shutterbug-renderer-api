# Setup

Use Java 21, Paper 26.2, and Minecraft 1.21.10. Add JitPack and use the API as `provided`; do not shade or bundle it.

```xml
<repositories><repository><id>jitpack.io</id><url>https://jitpack.io</url></repository></repositories>
<dependency><groupId>com.github.kernel-person</groupId><artifactId>shutterbug-renderer-api</artifactId><version>v1.0.0</version><scope>provided</scope></dependency>
```

Use `depend: [ShutterBugRenderer]` when rendering is required, or `softdepend: [ShutterBugRenderer]` when it is optional; optional consumers must follow the API-free entry-point pattern in [lifecycle](lifecycle.md). See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).
