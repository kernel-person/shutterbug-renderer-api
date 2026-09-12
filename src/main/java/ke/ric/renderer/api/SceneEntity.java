package ke.ric.renderer.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Resolved entity evidence grouped by one stable caller-visible object identity. */
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

    /** Fluent builder for immutable {@link SceneEntity} evidence. */
    public static final class Builder {
        private final UUID uuid;private final String type;private double x,y,z,minX,minY,minZ,maxX=1,maxY=1,maxZ=1;private float yaw;private String displayName,variant,model,skin,material,pose,containedBlock;private boolean living=true,baby;private int cubeSize=1;private int objectId;private String main="minecraft:air",off="minecraft:air",helmet="minecraft:air",chest="minecraft:air",legs="minecraft:air",boots="minecraft:air";private ResolvedRenderTarget legacyTarget;private List<ResolvedRenderTarget> resolvedTargets=List.of();private ResolvedLabel resolvedLabel;
        private Builder(UUID uuid,String type){this.uuid=Objects.requireNonNull(uuid);this.type=type;this.model=type;this.material=type;this.objectId=stableObjectId(uuid);}
        public Builder position(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}public Builder yaw(float value){yaw=value;return this;}public Builder transform(ResolvedTransform v){Objects.requireNonNull(v);x=v.x();y=v.y();z=v.z();yaw=v.yawDegrees();return this;}public Builder bounds(double a,double b,double c,double d,double e,double f){minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;return this;}public Builder displayName(String v){displayName=v;return this;}public Builder living(boolean v){living=v;return this;}public Builder baby(boolean v){baby=v;return this;}public Builder variant(String v){variant=v;return this;}public Builder cubeSize(int v){cubeSize=v;return this;}public Builder model(String v){model=v;return this;}public Builder skin(String v){skin=v;return this;}public Builder material(String v){material=v;return this;}public Builder pose(String v){pose=v;return this;}public Builder containedBlock(String v){containedBlock=v;return this;}public Builder objectId(int v){objectId=v;return this;}public Builder resolvedTarget(ResolvedRenderTarget v){legacyTarget=v;resolvedTargets=List.of();return this;}public Builder resolvedTargets(List<ResolvedRenderTarget> v){resolvedTargets=List.copyOf(v);legacyTarget=null;return this;}public Builder label(ResolvedLabel v){resolvedLabel=v;return this;}public Builder equipment(String a,String b,String c,String d,String e,String f){main=a;off=b;helmet=c;chest=d;legs=e;boots=f;return this;}
        public SceneEntity build(){List<ResolvedRenderTarget> targets=legacyTarget==null?resolvedTargets:List.of(legacyTarget.withPlacement(new ResolvedTransform(x,y,z,yaw),minX,minY,minZ,maxX,maxY,maxZ));return new SceneEntity(uuid,type,x,y,z,yaw,minX,minY,minZ,maxX,maxY,maxZ,displayName,living,main,off,helmet,chest,legs,boots,baby,variant,cubeSize,model,skin,material,pose,containedBlock,objectId,targets,resolvedLabel);}
    }
}
