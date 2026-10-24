package ke.ric.renderer.api;

/**
 * Measured nanosecond durations for the provider phases represented in a result.
 * {@link #UNAVAILABLE} distinguishes work outside a provider measurement boundary from a measured
 * duration of zero.
 * @param captureNanos measured world-capture time in nanoseconds, or UNAVAILABLE
 * @param serializationNanos scene serialization time in nanoseconds, or UNAVAILABLE
 * @param jniWallNanos native-call wall time in nanoseconds, or UNAVAILABLE
 * @param renderCoreNanos core-render work in nanoseconds, or UNAVAILABLE
 * @param decodeNanos output/diagnostic decode time in nanoseconds, or UNAVAILABLE
 * @param copyNanos immutable result-buffer copy time in nanoseconds, or UNAVAILABLE
 * @param conversionNanos measured conversion time in nanoseconds, or UNAVAILABLE when outside provider measurement
 * @param totalNanos total measured request time in nanoseconds, or UNAVAILABLE
 */
public record RenderTimings(long captureNanos, long serializationNanos, long jniWallNanos,
                            long renderCoreNanos, long decodeNanos, long copyNanos,
                            long conversionNanos, long totalNanos) {
    /** Used when a phase did not occur inside the provider; zero remains a real measured zero. */
    public static final long UNAVAILABLE=-1;
    /** Legacy timing shape; JNI wall, decode and copy phases are marked {@link #UNAVAILABLE}. */
    public RenderTimings(long captureNanos,long serializationNanos,long renderCoreNanos,long conversionNanos,long totalNanos){
        this(captureNanos,serializationNanos,UNAVAILABLE,renderCoreNanos,UNAVAILABLE,UNAVAILABLE,conversionNanos,totalNanos);
    }
}
