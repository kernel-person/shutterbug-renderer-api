package ke.ric.renderer.api;
import java.util.*;
import java.util.function.Supplier;
import org.bukkit.entity.*;
record FrameOptions(SceneContent content,Supplier<RenderEnvironment> environment,RenderAppearance appearance,
 RenderRecipe recipe,Player viewer,MapOutputOptions maps,List<Entity> entities,int chunks,List<MapSink> sinks,RenderLighting lighting,double roll,java.util.function.Consumer<FrameProgress> progress) {
 static FrameOptions defaults(){return new FrameOptions(SceneContent.all(),RenderEnvironment::captured,RenderAppearance.defaults(),RenderRecipe.layers(RenderLayer.scene()),null,MapOutputOptions.defaults(),List.of(),4,List.of(),RenderLighting.world(),0,null);}
 FrameOptions { Objects.requireNonNull(lighting);if(!Double.isFinite(roll))throw new IllegalArgumentException("roll must be finite");Objects.requireNonNull(content);Objects.requireNonNull(environment);Objects.requireNonNull(appearance);Objects.requireNonNull(recipe);Objects.requireNonNull(maps);entities=List.copyOf(entities);sinks=List.copyOf(sinks);if(chunks<1)throw new IllegalArgumentException("chunk budget must be positive"); }
 FrameOptions with(SceneContent c,Supplier<RenderEnvironment> e,RenderAppearance a,RenderRecipe r,Player v,MapOutputOptions m,List<Entity> x,int n,List<MapSink> s){return new FrameOptions(c,e,a,r,v,m,x,n,s,lighting,roll,progress);}
 FrameOptions controls(RenderLighting l,double r,java.util.function.Consumer<FrameProgress> p){return new FrameOptions(content,environment,appearance,recipe,viewer,maps,entities,chunks,sinks,l,r,p);}
 Set<UUID> entityIds(){Set<UUID> ids=new HashSet<>();for(Entity entity:entities)ids.add(entity.getUniqueId());return ids;}
 Set<UUID> hidden(org.bukkit.World world){Set<UUID> hidden=new HashSet<>();if(viewer!=null)for(Entity e:world.getEntities())if(!viewer.canSee(e))hidden.add(e.getUniqueId());return hidden;}
}
