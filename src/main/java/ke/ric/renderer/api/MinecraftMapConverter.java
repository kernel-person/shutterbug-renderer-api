package ke.ric.renderer.api;

import org.bukkit.map.MapPalette;
import java.awt.Color;

/** Converts immutable render output to exact Minecraft map-palette bytes. */
public final class MinecraftMapConverter {
    private static final int[][] B={{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}}; private MinecraftMapConverter(){}
    /** Returns one row-major map-palette byte per render pixel. */
    @SuppressWarnings("deprecation") public static byte[] quantize(RenderResult frame,MapDither mode){byte[] rgba=frame.rgba8();int w=frame.width(),h=frame.height();float[] work=new float[w*h*3];for(int i=0;i<w*h;i++){work[i*3]=rgba[i*4]&255;work[i*3+1]=rgba[i*4+1]&255;work[i*3+2]=rgba[i*4+2]&255;}byte[] out=new byte[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x,j=i*3,off=mode==MapDither.BAYER?(B[y&3][x&3]-8)*2:0;int r=clamp(Math.round(work[j])+off),g=clamp(Math.round(work[j+1])+off),b=clamp(Math.round(work[j+2])+off);byte p=MapPalette.matchColor(new Color(r,g,b));out[i]=p;if(mode==MapDither.FLOYD_STEINBERG){Color c=MapPalette.getColor(p);spread(work,w,h,x+1,y,r-c.getRed(),g-c.getGreen(),b-c.getBlue(),7f/16);spread(work,w,h,x-1,y+1,r-c.getRed(),g-c.getGreen(),b-c.getBlue(),3f/16);spread(work,w,h,x,y+1,r-c.getRed(),g-c.getGreen(),b-c.getBlue(),5f/16);spread(work,w,h,x+1,y+1,r-c.getRed(),g-c.getGreen(),b-c.getBlue(),1f/16);}}return out;}
    /** Returns row-major 128x128 tiles; both frame dimensions must be multiples of 128. */
    public static byte[][] tiles(RenderResult frame,MapDither mode){if(frame.width()%128!=0||frame.height()%128!=0)throw new IllegalArgumentException("map output dimensions must be multiples of 128");byte[] all=quantize(frame,mode);int cols=frame.width()/128,rows=frame.height()/128;byte[][] tiles=new byte[cols*rows][128*128];for(int ty=0;ty<rows;ty++)for(int tx=0;tx<cols;tx++)for(int y=0;y<128;y++)System.arraycopy(all,(ty*128+y)*frame.width()+tx*128,tiles[ty*cols+tx],y*128,128);return tiles;}
    private static void spread(float[]a,int w,int h,int x,int y,float r,float g,float b,float f){if(x<0||x>=w||y<0||y>=h)return;int i=(y*w+x)*3;a[i]+=r*f;a[i+1]+=g*f;a[i+2]+=b*f;}private static int clamp(int v){return Math.max(0,Math.min(255,v));}
}
