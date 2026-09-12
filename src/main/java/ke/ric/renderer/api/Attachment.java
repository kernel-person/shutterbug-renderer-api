package ke.ric.renderer.api;

/** Optional per-pixel data that can accompany the always-present RGBA8 color frame. */
public enum Attachment {
    /** One floating-point depth value per pixel. */
    DEPTH,
    /** Three floating-point normal components per pixel. */
    NORMAL,
    /** One material identifier per pixel. */
    MATERIAL_ID,
    /** One caller-visible object identifier per pixel. */
    OBJECT_ID
}
