package ke.ric.renderer.api;

/**
 * Immutable camera pose in world blocks and radians. The native basis applies local roll, then
 * pitch, then yaw. At zero yaw/pitch the camera faces +Z and screen-left is +X; positive roll
 * rotates screen-left toward +Y. All rotations, including full rolls and inverted views, are valid.
 * Bukkit yaw is negated before conversion to radians; Bukkit pitch retains its sign.
 * Fluent snapshot/stream FOV and roll methods instead accept degrees.
 *
 * @param x world X position in blocks
 * @param y world Y position in blocks
 * @param z world Z position in blocks
 * @param yawRadians finite native yaw in radians (negated Bukkit yaw)
 * @param pitchRadians finite pitch in radians (same sign as Bukkit pitch)
 * @param rollRadians finite local camera roll in radians
 * @param fovRadians finite vertical field of view, strictly between 0 and pi
 */
public record SceneCamera(double x, double y, double z, double yawRadians,
                          double pitchRadians, double rollRadians, double fovRadians) {
    /**
     * Constructs a validated immutable value.
     * @throws IllegalArgumentException if a coordinate or angle is nonfinite, or FOV is invalid
     */
    public SceneCamera {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(yawRadians) || !Double.isFinite(pitchRadians)
                || !Double.isFinite(rollRadians) || !Double.isFinite(fovRadians)
                || fovRadians <= 0 || fovRadians >= Math.PI)
            throw new IllegalArgumentException("camera coordinates and angles must be finite; field of view must be in (0, pi)");
    }
}
