# Jobs and progress

## Snapshot and stream progress

`.onProgress(...)` receives measured `FrameProgress` for capture, each render pass, conversion and saving. Callbacks run on the server thread at most once per tick. `basisPoints()` ranges from zero to 10,000 **within the current stage**, and rendering identifies its pass index/count. A new stage or pass can restart at zero; this is not an estimated total duration or overall percentage. Updates are coalesced, so callers must not depend on receiving every transition.

Cancel a snapshot's returned future to cancel its current stage. Stop a stream to end future frames and remove owned temporary output. Cancellation and lifecycle loss suppress late callbacks. A completed map allocation or atomic file write cannot be undone by cancelling a later step.

## Low-level jobs

`submit` performs bounded admission and returns a `RenderTask`; queue admission can fail synchronously with `QUEUE_FULL`. The provider defaults to one concurrent native render, queue capacity 16 and a 512 MiB aggregate native-memory budget. An operator can configure concurrency; consumers must still respect admission and memory failures. A job's effective memory ceiling is the lower of its requested limit and the provider limit. Admission reserves scene, selected assets, render, attachment and copy memory, and releases the reservation on every terminal path. Manual scene construction and asset registration are bounded too.

`RenderSettings.timeout()` begins at task creation and covers queueing, preparation, native work, diagnostics, and output copy. Poll `progress()` without blocking: phases are `QUEUED`, `PREPARING`, `DECODING`, `RENDERING`, `POST_PROCESSING`, `DIAGNOSTICS`, `COPYING_OUTPUT`, and terminal `COMPLETE`. Progress is monotonic actual work, not a wall-clock estimate; successful completion is `COMPLETE/10_000`.

`cancel()` returns true only when it wins a nonterminal transition; completion then fails with `CANCELLED`, while a terminal race returns false. `status()` is the coarse task state. Bound concurrent submissions in the consumer, back off after `QUEUE_FULL`, and retain a task only until its completion/cancellation handling finishes. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.2/sample-plugin).
