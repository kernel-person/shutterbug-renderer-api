# Threading and cancellation

Create/close the plugin-owned `Renderer`, start streams, and perform Bukkit operations on the server thread. Capture reads loaded world evidence in bounded server-thread batches. Entity pose, location suppliers and environment suppliers are sampled there. Native rendering, substantial conversion and provider photo writes run off-thread.

The fluent success/error overloads of `capture(...)`, `captureMap(...)` and `toMapItem(...)`, plus stream frame/error and progress consumers, dispatch on the server thread. Progress is coalesced to at most one callback per tick. Keep every callback short. Exceptions thrown by consumers fail pending work or an active stream. A final coalesced progress notification may arrive after successful completion; an exception in that notification cannot revoke the completed result.

Ordinary `CompletableFuture` continuations have **no Bukkit thread guarantee**. A continuation registered after completion can run immediately on the attaching thread; cancellation and failure may occur on a worker. Use the callback overloads for Bukkit changes, or an explicit main-thread executor. Never use `join()` or `get()` on the server thread.

The facade owns one bounded conversion worker and a queue of 32 tasks. Saturation reports `QUEUE_FULL`; do not create an unlimited retry queue. Large pure `image()` conversions run on the caller thread, so use an appropriate bounded executor. An already completed frame's pure image conversion remains usable after facade close.

Cancellation propagates to the active capture, render, conversion and saving stage. Owner disable, provider replacement and affected world unload settle pending work and suppress late consumers. Streams never queue catch-up frames or publish stale frames over a newer output. A map already allocated or a file already atomically replaced cannot be revoked by cancellation.

Low-level clients retain their existing threading contract. When bypassing the facade, explicitly own executors, tasks and lifecycle guards, and check the current provider generation before publishing output.
