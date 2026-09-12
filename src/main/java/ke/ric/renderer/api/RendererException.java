package ke.ric.renderer.api;

/** Provider failure carrying a stable machine-readable {@link RenderFailureCode}. */
public final class RendererException extends RuntimeException {
    private final RenderFailureCode code;
    public RendererException(RenderFailureCode code, String message) { super(message); this.code = code; }
    public RendererException(RenderFailureCode code, String message, Throwable cause) { super(message, cause); this.code = code; }
    /** Returns the stable failure category; callers should not parse exception text. */
    public RenderFailureCode code() { return code; }
}
