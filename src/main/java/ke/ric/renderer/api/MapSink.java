package ke.ric.renderer.api;
/**
 * Caller-owned palette destination for a stream. Every callback runs on the server thread and
 * should finish quickly. The SDK allocates no map and saves no photograph for this sink; the caller
 * chooses how to display or consume bytes. Cleanup calls {@link #close()} once per registration when the stream ends.
 */
@FunctionalInterface
public interface MapSink {
 /**
  * Consumes the next immutable palette frame after freshness and lifecycle validation.
  * Throwing terminates the stream and triggers cleanup.
  * @param frame aspect-fitted 128×128 palette with original source evidence
  */
 void accept(MapFrame frame);
 /**
  * Observes availability and terminal lifecycle transitions. NO_SIGNAL may recover.
  * @param status current stream state
  */
 default void status(RenderStream.Status status) {}
 /**
  * Releases caller-side resources on the server thread, once per registration during terminal cleanup.
  * Do not remove output owned by other callers.
  */
 default void close() {}
}
