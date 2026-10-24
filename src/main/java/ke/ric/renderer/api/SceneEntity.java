package ke.ric.renderer.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable entity evidence grouped by one stable nonzero object ID. Positions/bounds use world
 * blocks; yaw uses degrees. Resolved target/label evidence is copied, never looked up in live entities.
 * Prefer {@link #builder(UUID, String)} to the legacy metadata-only constructors.
 * @param uuid stable entity UUID
 * @param type namespaced entity type
 * @param x world X position in blocks
 * @param y world Y position in blocks
 * @param z world Z position in blocks
 * @param yaw horizontal orientation in degrees
 * @param minX minimum world X bound in blocks
 * @param minY minimum world Y bound in blocks
 * @param minZ minimum world Z bound in blocks
 * @param maxX maximum world X bound in blocks
 * @param maxY maximum world Y bound in blocks
 * @param maxZ maximum world Z bound in blocks
 * @param displayName optional entity display name
 * @param living immutable living-entity classification used by scene selection
 * @param mainHand namespaced main-hand item key
 * @param offHand namespaced off-hand item key
 * @param helmet namespaced helmet item key
 * @param chestplate namespaced chestplate item key
 * @param leggings namespaced leggings item key
 * @param boots namespaced boots item key
 * @param baby whether this entity has baby proportions
 * @param variant optional variant metadata
 * @param cubeSize positive cube-entity size
 * @param model optional model metadata
 * @param skin optional skin metadata
 * @param material optional material metadata
 * @param pose optional pose metadata
 * @param containedBlock optional contained-block state metadata
 * @param objectId nonzero caller-visible identity retained in OBJECT_ID output
 * @param resolvedTargets copied independently placed visual-target list; null means empty
 * @param resolvedLabel optional primary label; requires a display name and resolved target
 */
public record SceneEntity(UUID uuid,String type,double x,double y,double z,float yaw,
                          double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                          String displayName,boolean living,String mainHand,String offHand,
                          String helmet,String chestplate,String leggings,String boots,
                          boolean baby,String variant,int cubeSize,String model,String skin,
                          String material,String pose,String containedBlock,int objectId,
                          List<ResolvedRenderTarget> resolvedTargets,ResolvedLabel resolvedLabel) {
    public SceneEntity {
        uuid=Objects.requireNonNull(uuid,"uuid");type=Objects.requireNonNull(type,"type");
        if(cubeSize<1)throw new IllegalArgumentException("cube size must be positive");
        if(objectId==0)throw new IllegalArgumentException("object ID must be non-zero");
        resolvedTargets=List.copyOf(resolvedTargets==null?List.of():resolvedTargets);
        if(resolvedLabel!=null&&(!resolvedLabel.primary()||displayName==null||resolvedTargets.isEmpty()))
            throw new IllegalArgumentException("a resolved label must be the named object's primary wire label");
    }

    /**
     * Creates entity metadata without resolved visual targets.
     * Prefer {@link #builder(UUID, String)} when supplying complete manual-scene evidence.
     */
    @Deprecated(forRemoval=false)
    public SceneEntity(UUID uuid,String type,double x,double y,double z,float yaw,
                       double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                       String displayName,boolean living,String mainHand,String offHand,String helmet,
                       String chestplate,String leggings,String boots,boolean baby,String variant,int cubeSize,
                       String model,String skin,String material,String pose,String containedBlock) {
        this(uuid,type,x,y,z,yaw,minX,minY,minZ,maxX,maxY,maxZ,displayName,living,mainHand,offHand,
                helmet,chestplate,leggings,boots,baby,variant,cubeSize,model,skin,material,pose,containedBlock,
                stableObjectId(uuid),List.of(),null);
    }

    /**
     * Creates an entity with one target placed at the entity transform and bounds.
     * Prefer the builder's target list for entities composed from multiple independent targets.
     */
    @Deprecated(forRemoval=false)
    public SceneEntity(UUID uuid,String type,double x,double y,double z,float yaw,
                       double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                       String displayName,boolean living,String mainHand,String offHand,String helmet,
                       String chestplate,String leggings,String boots,boolean baby,String variant,int cubeSize,
                       String model,String skin,String material,String pose,String containedBlock,
                       ResolvedRenderTarget resolvedTarget,ResolvedLabel resolvedLabel) {
        this(uuid,type,x,y,z,yaw,minX,minY,minZ,maxX,maxY,maxZ,displayName,living,mainHand,offHand,
                helmet,chestplate,leggings,boots,baby,variant,cubeSize,model,skin,material,pose,containedBlock,
                stableObjectId(uuid),resolvedTarget==null?List.of():List.of(resolvedTarget.withPlacement(
                        new ResolvedTransform(x,y,z,yaw),minX,minY,minZ,maxX,maxY,maxZ)),resolvedLabel);
    }

    /** Legacy metadata-only convenience constructor; defaults model/material to the type and omits resolved visuals. */
    public SceneEntity(UUID uuid,String type,double x,double y,double z,float yaw,double minX,double minY,
                       double minZ,double maxX,double maxY,double maxZ,String displayName,boolean living,
                       String mainHand,String offHand,String helmet,String chestplate,String leggings,String boots,
                       boolean baby,String variant,int cubeSize) {
        this(uuid,type,x,y,z,yaw,minX,minY,minZ,maxX,maxY,maxZ,displayName,living,mainHand,offHand,helmet,
                chestplate,leggings,boots,baby,variant,cubeSize,type,null,type,null,null);
    }

    /** Returns the first resolved target, or null when no target is present. */
    @Deprecated(forRemoval=false)
    public ResolvedRenderTarget resolvedTarget(){return resolvedTargets.isEmpty()?null:resolvedTargets.getFirst();}

    /** Starts a resolved entity with a stable UUID and namespaced type. */
    public static Builder builder(UUID uuid,String type){return new Builder(uuid,type);}
    private static int stableObjectId(UUID uuid){long mixed=uuid.getMostSignificantBits()^uuid.getLeastSignificantBits();int value=(int)(mixed^(mixed>>>32));return value==0?1:value;}

    /**
     * Mutable builder for immutable entity evidence; do not share it concurrently. Defaults to origin,
     * zero yaw, unit bounds, living/adult, cube size 1, air equipment and a UUID-derived nonzero ID.
     * Callers still supply complete resolved targets when the scene requires entity visuals.
     */
    public static final class Builder {
        private final UUID uuid;private final String type;private double x,y,z,minX,minY,minZ,maxX=1,maxY=1,maxZ=1;private float yaw;private String displayName,variant,model,skin,material,pose,containedBlock;private boolean living=true,baby;private int cubeSize=1;private int objectId;private String main="minecraft:air",off="minecraft:air",helmet="minecraft:air",chest="minecraft:air",legs="minecraft:air",boots="minecraft:air";private ResolvedRenderTarget legacyTarget;private List<ResolvedRenderTarget> resolvedTargets=List.of();private ResolvedLabel resolvedLabel;
        private Builder(UUID uuid,String type){this.uuid=Objects.requireNonNull(uuid);this.type=type;this.model=type;this.material=type;this.objectId=stableObjectId(uuid);}
        /** Sets entity position in world blocks. */
        public Builder position(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        /** Sets horizontal orientation in degrees, not radians. */
        public Builder yaw(float yawDegrees){yaw=yawDegrees;return this;}
        /** Copies position and degree-based yaw from this transform. */
        public Builder transform(ResolvedTransform transform){Objects.requireNonNull(transform);x=transform.x();y=transform.y();z=transform.z();yaw=transform.yawDegrees();return this;}
        /** Sets finite world-space minimum/maximum bounds, in blocks. */
        public Builder bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;return this;}
        /** Sets the optional display name used with a primary resolved label. */
        public Builder displayName(String displayName){this.displayName=displayName;return this;}
        /** Sets the immutable classification used by living-only scene selection. */
        public Builder living(boolean living){this.living=living;return this;}
        /** Sets baby/adult metadata. */
        public Builder baby(boolean baby){this.baby=baby;return this;}
        /** Sets optional variant metadata; does not resolve missing visual evidence. */
        public Builder variant(String variant){this.variant=variant;return this;}
        /** Sets a positive cube-entity size; default 1. */
        public Builder cubeSize(int cubeSize){this.cubeSize=cubeSize;return this;}
        /** Sets model metadata; complete resolved visual targets remain the caller's responsibility. */
        public Builder model(String model){this.model=model;return this;}
        /** Sets optional skin metadata without performing a network lookup. */
        public Builder skin(String skin){this.skin=skin;return this;}
        /** Sets optional material metadata. */
        public Builder material(String material){this.material=material;return this;}
        /** Sets optional pose metadata. */
        public Builder pose(String pose){this.pose=pose;return this;}
        /** Sets optional contained-block state metadata. */
        public Builder containedBlock(String containedBlock){this.containedBlock=containedBlock;return this;}
        /** Sets a nonzero stable output object ID, replacing the UUID-derived default. */
        public Builder objectId(int objectId){this.objectId=objectId;return this;}
        /** Selects one legacy target, placed at this entity's transform/bounds when built; clears any target list. */
        public Builder resolvedTarget(ResolvedRenderTarget target){legacyTarget=target;resolvedTargets=List.of();return this;}
        /** Copies independently placed targets, replacing the legacy single target. */
        public Builder resolvedTargets(List<ResolvedRenderTarget> targets){resolvedTargets=List.copyOf(targets);legacyTarget=null;return this;}
        /** Sets optional primary label evidence; build requires a display name and at least one target. */
        public Builder label(ResolvedLabel label){resolvedLabel=label;return this;}
        /** Sets namespaced equipment item keys in main-hand, off-hand, helmet, chestplate, leggings, boots order. */
        public Builder equipment(String mainHand,String offHand,String helmet,String chestplate,String leggings,String boots){main=mainHand;off=offHand;this.helmet=helmet;chest=chestplate;legs=leggings;this.boots=boots;return this;}
        /** Validates and builds immutable evidence without reading any live Bukkit object. */
        public SceneEntity build(){List<ResolvedRenderTarget> targets=legacyTarget==null?resolvedTargets:List.of(legacyTarget.withPlacement(new ResolvedTransform(x,y,z,yaw),minX,minY,minZ,maxX,maxY,maxZ));return new SceneEntity(uuid,type,x,y,z,yaw,minX,minY,minZ,maxX,maxY,maxZ,displayName,living,main,off,helmet,chest,legs,boots,baby,variant,cubeSize,model,skin,material,pose,containedBlock,objectId,targets,resolvedLabel);}
    }
}
