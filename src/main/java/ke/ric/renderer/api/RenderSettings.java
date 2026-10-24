package ke.ric.renderer.api;

import java.time.Duration;
import java.util.EnumSet;
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
    public enum Profile {
        /** Fast compatibility-oriented rendering; the default from {@link RenderSettings#defaults()}. */
        CLASSIC,
        /** Higher-quality Classic rendering. */
        CLASSIC_HQ,
        /** Balanced general-purpose quality. */
        NORMAL,
        /** Cinematic-quality rendering and effects. */
        CINEMATIC,
        /** Higher-cost production-quality rendering. */
        ULTRA,
        /** Additional quality above Ultra, where advertised. */
        ULTRA_PLUS,
        /** Highest named quality intent; check provider capabilities before submission. */
        EXTREME
    }
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
    /**
     * Creates Classic settings at 128 by 128 pixels, suitable for one Minecraft map.
     * Uses no optional attachments, a 512 MiB memory ceiling, {@link #defaultWorkers()}
     * workers and a two-minute timeout. Provider limits still apply.
     * @return immutable defaults; use {@link #withResolution(int, int)} for another size
     */
    public static RenderSettings defaults() { return classic(128, 128); }
    /**
     * Creates v1 Classic settings with no optional attachments, a 512 MiB memory ceiling,
     * {@link #defaultWorkers()} workers and a two-minute render timeout.
     * @param width positive output width in pixels
     * @param height positive output height in pixels
     * @return immutable settings; use capabilities to check the provider's supported output
     */
    public static RenderSettings classic(int width, int height) { return new RenderSettings(1, Profile.CLASSIC, width, height, Set.of(), DEFAULT_MEMORY_LIMIT, defaultWorkers(), Duration.ofMinutes(2)); }
    /**
     * Returns a copy changing only the output dimensions. Does not resize an already captured scene.
     * Set the resolution before capture, and check the provider's supported dimensions.
     * @param width positive output width in pixels
     * @param height positive output height in pixels
     * @return immutable settings preserving quality, attachments and resource limits
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public RenderSettings withResolution(int width, int height) { return new RenderSettings(version, profile, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
    /**
     * Returns a copy replacing optional attachments. RGBA8 is always included.
     * @param attachments requested outputs advertised by provider capabilities; null means none
     * @return immutable settings with a defensive attachment-set copy
     */
    public RenderSettings withAttachments(Set<Attachment> attachments) { return new RenderSettings(version, profile, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
    /**
     * Returns a copy replacing optional attachments without constructing a set.
     * For example, {@code withAttachments(Attachment.OBJECT_ID, Attachment.DEPTH)}.
     * Duplicate attachments are included only once; RGBA8 is always included.
     * Use {@link #withAttachments(Set)} with an empty set to request none.
     * @param first first requested output advertised by provider capabilities
     * @param additional any further requested outputs; copied, not retained
     * @return immutable settings with exactly these optional attachments
     * @throws NullPointerException if any attachment or the additional array is null
     */
    public RenderSettings withAttachments(Attachment first, Attachment... additional) { return withAttachments(EnumSet.of(first, additional)); }
    /**
     * Returns a copy with a different quality intent.
     * @param profile non-null stable profile advertised by the provider
     * @return copied settings with the new profile
     */
    public RenderSettings withProfile(Profile profile) { return new RenderSettings(version, profile, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
    /**
     * Returns a copy requesting a bounded native worker count.
     * @param workers positive worker count, not exceeding the provider's configured limit
     * @return copied settings with the new worker request
     */
    public RenderSettings withWorkers(int workers) { return new RenderSettings(version, profile, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
    /**
     * Returns a copy with a per-request memory ceiling; the provider's lower limit wins.
     * @param memoryLimitBytes positive native-memory ceiling in bytes (not MiB)
     * @return copied settings with the new memory ceiling
     */
    public RenderSettings withMemoryLimit(long memoryLimitBytes) { return new RenderSettings(version, profile, width, height, attachments, memoryLimitBytes, workers, timeout); }
    /**
     * Returns a copy with a positive timeout that fits in nanoseconds; null restores two minutes.
     * Low-level tasks start this deadline at submission; {@link RendererSession} shares it across capture and render.
     * @param timeout positive duration representable in nanoseconds, or null for two minutes
     * @return copied settings with the new deadline duration
     */
    public RenderSettings withTimeout(Duration timeout) { return new RenderSettings(version, profile, width, height, attachments, nativeMemoryLimitBytes, workers, timeout); }
}
