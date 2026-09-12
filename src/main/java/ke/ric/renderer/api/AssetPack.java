package ke.ric.renderer.api;

import java.util.Objects;

/** Caller-supplied asset archive bytes and their diagnostic identifier. */
public final class AssetPack {
    private final String id;
    private final byte[] bytes;

    /** Creates a pack, defensively copying its archive bytes. */
    public AssetPack(String id, byte[] bytes) {
        this.id = Objects.requireNonNull(id);
        this.bytes = bytes.clone();
    }

    /** Returns the caller-defined identifier used in diagnostics. */
    public String id() { return id; }

    /** Returns the archive size in bytes. */
    public int size() { return bytes.length; }

    /** Returns a defensive copy of the archive bytes. */
    public byte[] bytes() { return bytes.clone(); }
}
