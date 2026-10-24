package ke.ric.renderer.api;
import java.util.*;
/** Frozen identity/classification used for selector masks, never a live Bukkit entity. */
public record CapturedEntity(UUID uuid,String type,boolean living,int objectId) {
 public CapturedEntity {Objects.requireNonNull(uuid,"uuid");Objects.requireNonNull(type,"type");if(objectId==0)throw new IllegalArgumentException("object ID must be nonzero");}
}
