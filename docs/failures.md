# Typed failures

Handle `RendererException` by `code()`, never exception text. A lifecycle race may replace later work with the failure that wins. Unwrap completion wrappers first, then map stable codes to bounded actions:

- Retry later with backoff after `QUEUE_FULL`; never spin.
- Load required chunks and recapture after `MISSING_CHUNK`; recapture after `STALE_SCENE`.
- Reduce dimensions, attachments, workers, or registered asset pressure after `MEMORY_LIMIT`; use a realistic deadline after `TIMEOUT`.
- Close local state, rediscover the service, create a new client, and recapture after `CLIENT_CLOSED` or `PROVIDER_DISABLED`.
- Select only advertised profiles/attachments after `UNSUPPORTED_CAPABILITY`; treat `UNSUPPORTED_PLATFORM` as an operator compatibility issue.
- Fix request input after `INVALID_REQUEST`; fix and revalidate archive bytes after `MALFORMED_ASSET`.
- After `MISSING_ASSET`, never resubmit the same inactive hash. If the owner still has the bounded, validated ZIP bytes, register them with the current client, retain the replacement `AssetHandle`, recapture a scene for the current provider generation, rebuild the job with the replacement hash, and resubmit at most once. Keep the returned recovery owner until the replacement task terminates; it closes the replacement handle automatically after success, failure, or cancellation. If that recovery fails again, stop and report the lifecycle/ownership fault.
- Report repeatable `NATIVE_FAILURE` with sanitized diagnostics and exact artifact checksums.
- Treat `CANCELLED` as terminal when cancellation was requested.

```java
--8<-- "fixtures/FailureExample.java"
```

```java
--8<-- "fixtures/RecoveryExample.java"
```
