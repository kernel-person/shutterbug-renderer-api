package ke.ric.renderer.api;

/**
 * Measured nanosecond durations for the provider phases represented in a result.
 * {@link #UNAVAILABLE} distinguishes work outside a provider measurement boundary from a measured
 * duration of zero.
 */
public record RenderTimings(long captureNanos, long serializationNanos, long jniWallNanos,
                            long renderCoreNanos, long decodeNanos, long copyNanos,
                            long conversionNanos, long totalNanos) {
    /** Used when a phase did not occur inside the provider; zero remains a real measured zero. */
    public static final long UNAVAILABLE=-1;
    public RenderTimings(long captureNanos,long serializationNanos,long renderCoreNanos,long conversionNanos,long totalNanos){
        this(captureNanos,serializationNanos,UNAVAILABLE,renderCoreNanos,UNAVAILABLE,UNAVAILABLE,conversionNanos,totalNanos);
    }
}
