package ke.ric.renderer.api;

/** Provider failure carrying a stable machine-readable {@link RenderFailureCode}. */
public final class RendererException extends RuntimeException {
    /** Optional narrower reason within the broad failure category. */
    public enum Detail {
        /** No narrower reason was supplied; includes all legacy constructors. */
        UNSPECIFIED,
        /** This request's residency exceeds its effective per-request memory ceiling. */
        REQUEST_MEMORY_LIMIT,
        /** Aggregate provider residency cannot admit more work. */
        PROVIDER_MEMORY_LIMIT
    }
    private final RenderFailureCode code;
    private final Detail detail;
    /** Creates a typed failure with UNSPECIFIED detail; a cause may still be supplied through initCause. */
    public RendererException(RenderFailureCode code, String message) { super(message); this.code = code; this.detail = Detail.UNSPECIFIED; }
    /** Creates a typed failure preserving its cause and using UNSPECIFIED detail. */
    public RendererException(RenderFailureCode code, String message, Throwable cause) { this(code, Detail.UNSPECIFIED, message, cause); }
    /** Creates a failure with a non-null structured detail; message text is diagnostic, not a parsing contract. */
    public RendererException(RenderFailureCode code, Detail detail, String message) {
        super(message); this.code = code; this.detail = java.util.Objects.requireNonNull(detail, "detail");
    }
    /** Creates a failure preserving the broad category, non-null narrower detail and original cause. */
    public RendererException(RenderFailureCode code, Detail detail, String message, Throwable cause) {
        super(message, cause); this.code = code; this.detail = java.util.Objects.requireNonNull(detail, "detail");
    }
    /** Returns the stable failure category; callers should not parse exception text. */
    public RenderFailureCode code() { return code; }
    /** Returns a structured narrower reason without requiring message parsing. */
    public Detail detail() { return detail; }
}
