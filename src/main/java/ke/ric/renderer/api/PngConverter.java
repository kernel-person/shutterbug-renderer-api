package ke.ric.renderer.api;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Converts immutable RGBA8 render output into a PNG byte stream. */
public final class PngConverter {
    private PngConverter() {}

    /** Encodes the frame without modifying it. */
    public static byte[] encode(RenderResult frame){BufferedImage image=new BufferedImage(frame.width(),frame.height(),BufferedImage.TYPE_INT_ARGB);byte[] p=frame.rgba8();for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++){int i=(y*frame.width()+x)*4;int argb=(p[i+3]&255)<<24|(p[i]&255)<<16|(p[i+1]&255)<<8|(p[i+2]&255);image.setRGB(x,y,argb);}try(ByteArrayOutputStream out=new ByteArrayOutputStream()){if(!ImageIO.write(image,"png",out))throw new IllegalStateException("PNG writer unavailable");return out.toByteArray();}catch(IOException e){throw new IllegalStateException("PNG conversion failed",e);}}
}
