package ke.ric.renderer.api;

import java.util.Objects;

/** Exact baked RGBA label evidence and placement. */
public final class ResolvedLabel {
    private final int width,height;private final byte[] rgba8;private final double y;private final boolean primary;
    public ResolvedLabel(int width,int height,byte[] rgba8,double y,boolean primary){if(width<1||width>0xffff||height<1||height>0xffff||!Double.isFinite(y))throw new IllegalArgumentException("invalid resolved label");this.width=width;this.height=height;this.rgba8=Objects.requireNonNull(rgba8,"rgba8").clone();if(this.rgba8.length!=Math.multiplyExact(Math.multiplyExact(width,height),4))throw new IllegalArgumentException("label RGBA length does not match dimensions");this.y=y;this.primary=primary;}
    public int width(){return width;}public int height(){return height;}public byte[] rgba8(){return rgba8.clone();}public double y(){return y;}public boolean primary(){return primary;}
}
