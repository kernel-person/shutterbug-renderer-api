package ke.ric.renderer.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Immutable rendered frame, optional attachments, visibility statistics, and measured timings.
 * Constructor inputs are defensively copied, array accessors return copies, and
 * {@link #rgbaBuffer()} returns a read-only copy.
 */
public final class RenderResult {
    private final int width,height; private final byte[] rgba; private final float[] depth,normals; private final int[] material,object;
    private final VisibilityStatistics visibility; private final RenderTimings timings;
    public RenderResult(int width,int height,byte[] rgba,float[] depth,float[] normals,int[] material,int[] object,VisibilityStatistics visibility,RenderTimings timings) {
        this(width,height,rgba,depth,normals,material,object,visibility,timings,()->{});
    }
    /**
     * Creates an immutable result while invoking a cooperative checkpoint during large copies.
     * The checkpoint may throw to stop construction; no partially copied result is published.
     */
    public RenderResult(int width,int height,byte[] rgba,float[] depth,float[] normals,int[] material,int[] object,VisibilityStatistics visibility,RenderTimings timings,Runnable checkpoint) {
        this(width,height,copied(width,height,rgba,depth,normals,material,object,checkpoint),visibility,timings);
    }
    /**
     * Copies all retained buffers, then finalizes timings so construction work remains inside
     * copy and total accounting.
     */
    public static RenderResult measuredCopy(int width,int height,byte[] rgba,float[] depth,float[] normals,int[] material,int[] object,VisibilityStatistics visibility,Runnable checkpoint,long copyStartedNanos,LongSupplier nanoTime,BiFunction<Long,Long,RenderTimings> timingFinalizer) {
        Objects.requireNonNull(nanoTime,"nanoTime");Objects.requireNonNull(timingFinalizer,"timingFinalizer");Buffers buffers=copied(width,height,rgba,depth,normals,material,object,checkpoint);long completed=nanoTime.getAsLong();long copyNanos=completed-copyStartedNanos;if(copyNanos<0)throw new IllegalArgumentException("clock moved backwards");return new RenderResult(width,height,buffers,visibility,Objects.requireNonNull(timingFinalizer.apply(copyNanos,completed),"timings"));
    }
    private RenderResult(int width,int height,Buffers buffers,VisibilityStatistics visibility,RenderTimings timings){this.width=width;this.height=height;this.rgba=buffers.rgba;this.depth=buffers.depth;this.normals=buffers.normals;this.material=buffers.material;this.object=buffers.object;this.visibility=Objects.requireNonNull(visibility);this.timings=Objects.requireNonNull(timings);}
    private static Buffers copied(int width,int height,byte[] rgba,float[] depth,float[] normals,int[] material,int[] object,Runnable checkpoint){int pixels=Math.multiplyExact(width,height);if(width<=0||height<=0||rgba==null||rgba.length!=Math.multiplyExact(pixels,4))throw new IllegalArgumentException("invalid RGBA8 frame");requireLength(depth,pixels,"depth");requireLength(normals,Math.multiplyExact(pixels,3),"normals");requireLength(material,pixels,"material");requireLength(object,pixels,"object");Objects.requireNonNull(checkpoint,"checkpoint");return new Buffers(copy(rgba,checkpoint),copy(depth,checkpoint),copy(normals,checkpoint),copy(material,checkpoint),copy(object,checkpoint));}
    private record Buffers(byte[] rgba,float[] depth,float[] normals,int[] material,int[] object){}
    private static void requireLength(float[] a,int n,String name){if(a!=null&&a.length!=n)throw new IllegalArgumentException("invalid "+name+" length");}
    private static void requireLength(int[] a,int n,String name){if(a!=null&&a.length!=n)throw new IllegalArgumentException("invalid "+name+" length");}
    private static float[] copy(float[] a){return a==null?null:a.clone();} private static int[] copy(int[] a){return a==null?null:a.clone();}
    private static byte[] copy(byte[] value,Runnable checkpoint){byte[] out=new byte[value.length];for(int at=0;at<value.length;at+=64*1024){checkpoint.run();int length=Math.min(64*1024,value.length-at);System.arraycopy(value,at,out,at,length);}checkpoint.run();return out;}
    private static float[] copy(float[] value,Runnable checkpoint){if(value==null)return null;float[] out=new float[value.length];for(int at=0;at<value.length;at+=16*1024){checkpoint.run();int length=Math.min(16*1024,value.length-at);System.arraycopy(value,at,out,at,length);}checkpoint.run();return out;}
    private static int[] copy(int[] value,Runnable checkpoint){if(value==null)return null;int[] out=new int[value.length];for(int at=0;at<value.length;at+=16*1024){checkpoint.run();int length=Math.min(16*1024,value.length-at);System.arraycopy(value,at,out,at,length);}checkpoint.run();return out;}
    /** Returns the frame width in pixels. */
    public int width(){return width;}
    /** Returns the frame height in pixels. */
    public int height(){return height;}
    /** Returns a copy of row-major RGBA8 color, four bytes per pixel. */
    public byte[] rgba8(){return rgba.clone();}
    /** Returns a read-only buffer over a defensive copy of row-major RGBA8 color. */
    public ByteBuffer rgbaBuffer(){return ByteBuffer.wrap(rgba.clone()).asReadOnlyBuffer();}
    /** Returns copied depth values, or null when not requested. */
    public float[] depth(){return copy(depth);}
    /** Returns copied XYZ normal values, or null when not requested. */
    public float[] normals(){return copy(normals);}
    /** Returns copied material identifiers, or null when not requested. */
    public int[] materialIds(){return copy(material);}
    /** Returns copied caller-visible object identifiers, or null when not requested. */
    public int[] objectIds(){return copy(object);}
    /** Returns immutable visibility statistics for the frame. */
    public VisibilityStatistics visibility(){return visibility;}
    /** Returns measured provider timings for the frame. */
    public RenderTimings timings(){return timings;}
}
