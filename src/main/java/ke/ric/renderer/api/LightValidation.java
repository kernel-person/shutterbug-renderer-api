package ke.ric.renderer.api;
final class LightValidation {
 private LightValidation() {}
 static void check(LightSpace space,int rgb,double x,double y,double z,double intensity,double range) {
  java.util.Objects.requireNonNull(space,"space");if(rgb<0||rgb>0xffffff)throw new IllegalArgumentException("RGB must be 0..0xffffff");
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("position must be finite");
  factor(intensity);if(!Double.isFinite(range)||range<=0||range>64)throw new IllegalArgumentException("range must be in (0,64]");
 }
 static void factor(double value){if(!Double.isFinite(value)||value<0||value>64)throw new IllegalArgumentException("lighting factor or intensity must be 0..64");}
}
