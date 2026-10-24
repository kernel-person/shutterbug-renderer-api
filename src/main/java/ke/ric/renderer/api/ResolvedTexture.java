package ke.ric.renderer.api;

import java.util.Objects;

/** Exact immutable RGBA8 texture evidence used by a resolved render target. */
public final class ResolvedTexture {
    private final int width,height;private final byte[] rgba8;
    /** Creates a copied texture of positive pixel dimensions; bytes must contain exactly width*height*4 row-major RGBA values. */
    public ResolvedTexture(int width,int height,byte[] rgba8){if(width<1||height<1)throw new IllegalArgumentException("texture dimensions must be positive");this.width=width;this.height=height;this.rgba8=Objects.requireNonNull(rgba8,"rgba8").clone();if(this.rgba8.length!=Math.multiplyExact(Math.multiplyExact(width,height),4))throw new IllegalArgumentException("RGBA length does not match dimensions");}
    /** Returns texture width in pixels. */
    public int width(){return width;}
    /** Returns texture height in pixels. */
    public int height(){return height;}
    /** Returns a defensive copy of row-major bytes in red, green, blue, alpha order. */
    public byte[] rgba8(){return rgba8.clone();}
}
