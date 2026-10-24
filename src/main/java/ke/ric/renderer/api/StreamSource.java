package ke.ric.renderer.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Copies camera poses only when the stream can do useful work on the server thread. */
final class StreamSource {
    private final Supplier<Location> camera;
    final Entity entity;
    StreamSource(Location location) { Location copy=Objects.requireNonNull(location,"location").clone(); camera=copy::clone; entity=null; }
    StreamSource(Entity entity) {
        this.entity=Objects.requireNonNull(entity,"entity");
        camera=()-> !entity.isValid() || entity.isDead() || entity instanceof Player player && !player.isOnline()
                ? null : entity instanceof LivingEntity living ? living.getEyeLocation() : entity.getLocation();
    }
    StreamSource(Supplier<Location> camera) { this.camera=Objects.requireNonNull(camera,"camera"); entity=null; }
    boolean available() { return entity==null || entity.isValid() && !entity.isDead() && (!(entity instanceof Player player) || player.isOnline()); }
    Location sample() {
        Location value=camera.get();
        if(value==null) return null;
        if(value.getWorld()==null || !Double.isFinite(value.getX()) || !Double.isFinite(value.getY())
                || !Double.isFinite(value.getZ()) || !Float.isFinite(value.getYaw()) || !Float.isFinite(value.getPitch()))
            throw new IllegalArgumentException("stream camera must have a world and finite coordinates");
        return value.clone();
    }
    static boolean reusable(Location original, Location current, long captured, long now, SceneReusePolicy policy) {
        if(original.getWorld()==null || current.getWorld()==null || !Objects.equals(original.getWorld().getUID(),current.getWorld().getUID()))return false;
        long age=now-captured;
        double dx=original.getX()-current.getX(),dy=original.getY()-current.getY(),dz=original.getZ()-current.getZ();
        return age>=0 && age<=policy.maxWorldAge().toNanos()
                && dx*dx+dy*dy+dz*dz<=policy.maxCameraDisplacement()*policy.maxCameraDisplacement()
                && original.getBlockX()>>4==current.getBlockX()>>4 && original.getBlockZ()>>4==current.getBlockZ()>>4;
    }
    static SceneCamera camera(Location value,double fov) {return camera(value,fov,0);}
    static SceneCamera camera(Location value,double fov,double roll) {
        return new SceneCamera(value.getX(),value.getY(),value.getZ(),Math.toRadians(-value.getYaw()),Math.toRadians(value.getPitch()),Math.toRadians(roll),Math.toRadians(fov));
    }
}
