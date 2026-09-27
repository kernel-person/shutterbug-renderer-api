# Assets

Register immutable ZIP bytes with the client before submission. Use the returned content hash in each job that needs the pack, retain the `AssetHandle` until all referencing tasks finish, and close it when the consumer no longer needs the registration. Handles and hashes are scoped to the owning client/provider generation; after reload, register the bytes again.

Treat `MALFORMED_ASSET` as a content error and `MISSING_ASSET` as a lifecycle/ownership error. On `MISSING_ASSET`, do not retry an inactive hash: re-register retained validated bytes with the current client, retain the replacement handle, recapture, rebuild the job with the replacement hash, and resubmit at most once. The returned recovery owner keeps that handle alive while the replacement task runs and closes it automatically when the task succeeds, fails, or is cancelled. Validate archive size and provenance before registration, never pass untrusted unbounded archives, and do not assume a file name is a content identity.

```java
--8<-- "fixtures/AssetExample.java"
```

The returned `AssetSubmission` owns the registration. Keep it while the task is in flight, then close it after completion or cancellation has reached a terminal state.

Manual scenes carry engine-neutral evidence and may select registered assets, but they do not let consumers import provider-private meshes, native handles, or implementation classes. See [manual scenes](manual-scenes.md) for completeness rules.
