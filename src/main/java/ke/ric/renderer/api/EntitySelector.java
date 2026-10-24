package ke.ric.renderer.api;
import java.util.*;
/**
 * Immutable predicate over copied entity metadata. It never reads a live Bukkit entity and does
 * not capture additional entities. Use types, UUIDs and living/player categories, then combine
 * selectors with {@link #and(EntitySelector)}, {@link #or(EntitySelector)} and {@link #not()}.
 * Selectors cannot override request exclusions or viewer privacy.
 */
public final class EntitySelector {
 private interface Match { boolean test(UUID id,String type,boolean living); }
 private static final EntitySelector ALL=new EntitySelector((id,type,living)->true,true);
 private final Match predicate; private final boolean all;
 private EntitySelector(Match predicate,boolean all){this.predicate=predicate;this.all=all;}
 /**
  * Matches every entity present in captured evidence.
  * @return an unrestricted selector
  */
 public static EntitySelector all(){return ALL;}
 /**
  * Matches no entity.
  * @return an empty selector
  */
 public static EntitySelector none(){return new EntitySelector((id,type,living)->false,false);}
 /**
  * Matches metadata marked as living, including players.
  * @return a living-entity selector
  */
 public static EntitySelector living(){return new EntitySelector((id,type,living)->living,false);}
 /**
  * Matches the captured namespaced type minecraft:player.
  * @return a player selector
  */
 public static EntitySelector players(){return types("minecraft:player");}
 /**
  * Matches any supplied lowercase namespaced entity type. The values are defensively copied.
  * @param namespacedTypes type keys such as minecraft:zombie; an empty array matches nothing
  * @return a type selector
  * @throws IllegalArgumentException for a null or malformed type key
  */
 public static EntitySelector types(String... namespacedTypes){
  Set<String> types=new HashSet<>(); for(String type:Objects.requireNonNull(namespacedTypes,"namespacedTypes")){
   if(type==null || !type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("entity type must be a lowercase namespaced key"); types.add(type);
  } Set<String> copy=Set.copyOf(types);return new EntitySelector((id,type,living)->copy.contains(type),false);
 }
 /**
  * Matches any supplied UUID without retaining the caller's collection.
  * @param ids entity identities; an empty collection matches nothing
  * @return an identity selector
  * @throws NullPointerException if the collection or an element is null
  */
 public static EntitySelector ids(Collection<UUID> ids){Set<UUID> copy=Set.copyOf(ids);return new EntitySelector((id,type,living)->copy.contains(id),false);}
 /**
  * Matches any supplied UUID; the array is defensively copied.
  * @param ids entity identities; an empty array matches nothing
  * @return an identity selector
  * @throws NullPointerException if the array or an element is null
  */
 public static EntitySelector ids(UUID... ids){return ids(Arrays.asList(ids.clone()));}
 /**
  * Requires both this selector and the supplied selector to match.
  * @param other predicate to intersect
  * @return an immutable intersection
  */
 public EntitySelector and(EntitySelector other){Objects.requireNonNull(other,"other");if(all)return other;if(other.all)return this;return new EntitySelector((id,type,living)->matches(id,type,living)&&other.matches(id,type,living),false);}
 /**
  * Accepts a match from either this selector or the supplied selector. Capture exclusions and
  * privacy still apply outside the selector.
  * @param other predicate to union
  * @return an immutable union
  */
 public EntitySelector or(EntitySelector other){Objects.requireNonNull(other,"other");if(all||other.all)return ALL;return new EntitySelector((id,type,living)->matches(id,type,living)||other.matches(id,type,living),false);}
 /**
  * Negates this predicate within existing captured evidence.
  * @return an immutable complement selector
  */
 public EntitySelector not(){return new EntitySelector((id,type,living)->!matches(id,type,living),false);}
 /**
  * Tests copied or caller-supplied metadata without reading Bukkit state.
  * @param id entity UUID
  * @param namespacedType captured type key, such as minecraft:player
  * @param living whether the source metadata marks this entity as living
  * @return whether this predicate accepts the metadata
  */
 public boolean matches(UUID id,String namespacedType,boolean living){return predicate.test(Objects.requireNonNull(id,"id"),Objects.requireNonNull(namespacedType,"namespacedType"),living);}
 boolean isAll(){return all;}
}
