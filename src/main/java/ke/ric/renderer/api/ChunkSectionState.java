package ke.ric.renderer.api;

/** Exact evidence state for one 16x16x16 chunk section. */
public enum ChunkSectionState {
    /** Evidence is unavailable; required missing sections cause MISSING_CHUNK instead of being treated as air. */
    MISSING,
    /** Evidence is present and the section is known to contain only air. */
    EMPTY,
    /** Evidence is present and block states are supplied by a palette. */
    PALETTED
}
