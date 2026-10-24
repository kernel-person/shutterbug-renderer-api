package ke.ric.renderer.api;
/** Provider-generation composition memory reservation, released on client/provider shutdown. */
public interface CompositionLease extends AutoCloseable {
 /** Releases this reservation idempotently; safe to invoke off the server thread. */ @Override void close();
}
