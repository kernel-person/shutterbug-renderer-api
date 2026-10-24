package ke.ric.renderer.api;

import java.util.Objects;

/** Exact baked RGBA label evidence and placement. */
public final class ResolvedLabel {
    private final int width,height;private final byte[] rgba8;private final double y;private final boolean primary;
    /**
     * Creates immutable copied label pixels and placement.
     * @param width pixel width in 1..65535
     * @param height pixel height in 1..65535
     * @param rgba8 exactly width*height*4 row-major RGBA bytes, copied
     * @param y finite label placement Y coordinate in world blocks
     * @param primary whether this is the named object's primary label
     */
    public ResolvedLabel(int width,int height,byte[] rgba8,double y,boolean primary){if(width<1||width>0xffff||height<1||height>0xffff||!Double.isFinite(y))throw new IllegalArgumentException("invalid resolved label");this.width=width;this.height=height;this.rgba8=Objects.requireNonNull(rgba8,"rgba8").clone();if(this.rgba8.length!=Math.multiplyExact(Math.multiplyExact(width,height),4))throw new IllegalArgumentException("label RGBA length does not match dimensions");this.y=y;this.primary=primary;}
    /** Returns label width in pixels. */
    public int width(){return width;}
    /** Returns label height in pixels. */
    public int height(){return height;}
    /** Returns a defensive copy of the row-major RGBA label image. */
    public byte[] rgba8(){return rgba8.clone();}
    /** Returns label placement Y in world blocks. */
    public double y(){return y;}
    /** Returns whether this is the named object's primary label. */
    public boolean primary(){return primary;}
}
