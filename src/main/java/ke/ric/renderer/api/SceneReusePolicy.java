package ke.ric.renderer.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable limits for reusing unchanged world evidence with a different camera pose. Neither
 * limit is renewed by scene derivation or reuse. The provider also requires the original centre
 * chunk and captured build-height coverage. Reuse does not update captured entities or environment.
 *
 * @param maxWorldAge positive duration measured from original world capture; must fit in nanoseconds
 * @param maxCameraDisplacement finite nonnegative distance from the original camera position, in blocks
 */
public record SceneReusePolicy(Duration maxWorldAge, double maxCameraDisplacement) {
    /**
     * Constructs a validated immutable value.
     * @throws IllegalArgumentException if either bound is invalid
     * @throws NullPointerException if maxWorldAge is null
     */
    public SceneReusePolicy {
        Objects.requireNonNull(maxWorldAge, "maxWorldAge");
        try {
            if (maxWorldAge.isNegative() || maxWorldAge.isZero() || maxWorldAge.toNanos() <= 0)
                throw new IllegalArgumentException("maximum world age must be positive");
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("maximum world age must fit in nanoseconds", overflow);
        }
        if (!Double.isFinite(maxCameraDisplacement) || maxCameraDisplacement < 0)
            throw new IllegalArgumentException("maximum camera displacement must be finite and nonnegative");
    }
}
