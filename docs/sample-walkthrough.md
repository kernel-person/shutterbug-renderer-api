# Complete sample walkthrough

The sibling [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.1/sample-plugin) is a buildable external consumer, not pseudocode. With Java 21, run `mvn -B clean install`, then `mvn -B -f sample-plugin/pom.xml clean verify` from the exported SDK repository.

Follow its lifecycle in order:

1. The Bukkit entry point remains API-free so `softdepend` can start when the SDK/provider is absent.
2. The isolated runtime attaches immediately when the service exists and listens for asynchronous service registration without double activation.
3. It creates one client owned by the plugin, reads capabilities, and chooses only advertised profiles/attachments.
4. It prepares cold regions without blocking Paper's main thread, captures a complete scene in bounded batches, and rejects stale lifecycle state.
5. It submits a bounded job, polls monotonic progress, observes cancellation/timeout, and handles typed failures.
6. It inspects immutable results, timings, and visibility, then performs PNG/map conversion off-thread.
7. Disable closes polling, tasks, assets, and the client. A provider reload requires discovery, a new client, and fresh capture.

Before adapting the sample, decide whether rendering is optional, bound your queues and retained output, preserve the [safe operator boundary](operator-guide.md), and keep provider-private packages out of your build.
