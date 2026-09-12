package ke.ric.renderer.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable output, quality, attachment, memory, worker, and deadline settings for a render.
 *
 * @param version contract version; v1 requires {@code 1}
 * @param profile stable quality intent that must be advertised by capabilities
 * @param width output width in pixels
 * @param height output height in pixels
 * @param attachments requested optional per-pixel attachments
 * @param nativeMemoryLimitBytes caller ceiling for native memory used by this job
 * @param workers requested bounded native worker count
 * @param timeout end-to-end render deadline starting at task creation
 */
public record RenderSettings(int version, Profile profile, int width, int height,
                             Set<Attachment> attachments, long nativeMemoryLimitBytes,
                             int workers, Duration timeout) {
    /** Stable consumer-facing quality intents; implementations may choose their internal settings. */
    public enum Profile { CLASSIC, CLASSIC_HQ, NORMAL, CINEMATIC, ULTRA, ULTRA_PLUS, EXTREME }
    /** Default per-job native-memory ceiling: 512 MiB. */
    public static final long DEFAULT_MEMORY_LIMIT = 512L * 1024 * 1024;
    /** Returns a conservative worker count based on the current runtime. */
    public static int defaultWorkers() { return Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)); }
    public RenderSettings {
        if (version != 1 || width <= 0 || height <= 0 || nativeMemoryLimitBytes <= 0 || workers <= 0) throw new IllegalArgumentException("invalid render settings");
        profile = Objects.requireNonNull(profile, "profile");
        attachments = Set.copyOf(attachments == null ? Set.of() : attachments);
        timeout = Objects.requireNonNullElse(timeout, Duration.ofMinutes(2));
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        try {
            timeout.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("timeout must fit in nanoseconds", failure);
        }
    }
    /** Creates v1 Classic settings with no optional attachments and conservative defaults. */
    public static RenderSettings classic(int width, int height) { return new RenderSettings(1, Profile.CLASSIC, width, height, Set.of(), DEFAULT_MEMORY_LIMIT, defaultWorkers(), Duration.ofMinutes(2)); }
    /** Returns a copy with the requested attachments. */
    public RenderSettings withAttachments(Set<Attachment> values) { return new RenderSettings(version, profile, width, height, values, nativeMemoryLimitBytes, workers, timeout); }
    /** Returns a copy with a different stable quality profile. */
    public RenderSettings withProfile(Profile value) { return new RenderSettings(version, value, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
}
