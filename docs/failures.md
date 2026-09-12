# Typed failures

Handle `RendererException` by `code()`, never exception text. Synchronous calls can report `INVALID_REQUEST`, `QUEUE_FULL`, `CLIENT_CLOSED`, `MISSING_CHUNK`, `MISSING_ASSET`, `MALFORMED_ASSET`, `UNSUPPORTED_CAPABILITY`, `UNSUPPORTED_PLATFORM`, `MEMORY_LIMIT`, `PROVIDER_DISABLED`, or `STALE_SCENE`; capture/task stages can report `INVALID_REQUEST`, `CANCELLED`, `TIMEOUT`, `CLIENT_CLOSED`, `MISSING_CHUNK`, `MALFORMED_ASSET`, `UNSUPPORTED_CAPABILITY`, `NATIVE_FAILURE`, `MEMORY_LIMIT`, or `PROVIDER_DISABLED`. A lifecycle race may replace later work with the failure that wins. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).

```java
--8<-- "fixtures/FailureExample.java"
```
