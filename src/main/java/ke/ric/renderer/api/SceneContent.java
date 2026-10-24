package ke.ric.renderer.api;
import java.util.Objects;
/**
 * Immutable selection of geometry retained in a derived scene. Content selection can only narrow
 * available evidence; repeated entity selections intersect. Removed geometry is unavailable to
 * camera, reflection and shadow rays. To hide geometry only from camera output while retaining
 * its lighting and shadows, use {@link RenderLayer#entities(EntitySelector)} and
 * {@link RenderLayer#occlusion(Occlusion)} instead.
 *
 * <p>World environment is separate from block geometry: {@link #entitiesOnly()} removes block
 * geometry while retaining captured time, weather, sampled light levels and other environment
 * facts. Capture exclusions and viewer privacy remain authoritative in every derived scene. Use
 * {@link RenderEnvironment} for supported environment overrides.</p>
 */
public final class SceneContent {
 private final boolean includeBlocks;private final EntitySelector entities;
 private SceneContent(boolean includeBlocks,EntitySelector entities){this.includeBlocks=includeBlocks;this.entities=Objects.requireNonNull(entities,"entities");}
 /**
  * Keeps all available block and entity geometry. It cannot restore evidence removed before capture
  * or by earlier authoritative exclusions.
  * @return an unrestricted content selection
  */
 public static SceneContent all(){return new SceneContent(true,EntitySelector.all());}
 /**
  * Keeps captured block geometry and removes all entity geometry from the derived scene.
  * @return block-only scene content
  */
 public static SceneContent blocksOnly(){return new SceneContent(true,EntitySelector.none());}
 /**
  * Removes block geometry from the derived scene while retaining captured entities, environment
  * facts and sampled light levels. Removed blocks cannot occlude entities or cast geometric shadows.
  * @return entity-only scene content
  */
 public static SceneContent entitiesOnly(){return new SceneContent(false,EntitySelector.all());}
 /**
  * Intersects the current entity selection with another selector. Chaining selectors never
  * restores entities excluded by the previous selection or capture.
  * @param selector predicate over copied entity metadata
  * @return a new narrowed content selection
  */
 public SceneContent entities(EntitySelector selector){return new SceneContent(includeBlocks,entities.and(selector));}
 /**
  * Reports whether captured block geometry is retained in the derived scene.
  * @return true when block geometry is retained
  */
 public boolean includeBlocks(){return includeBlocks;}
 /**
  * Returns the entity selection evaluated against copied metadata, without live Bukkit reads.
  * @return immutable entity selector
  */
 public EntitySelector entities(){return entities;}
 SceneContent intersect(SceneContent other){return new SceneContent(includeBlocks&&other.includeBlocks,entities.and(other.entities));}
 boolean isAll(){return includeBlocks&&entities.isAll();}
}
