package ke.ric.renderer.sample;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Fail-closed reflection boundary for Paper's public asynchronous generating chunk load. */
final class PaperAsyncChunkLoader {
    private static final String METHOD = "getChunkAtAsync";

    private PaperAsyncChunkLoader() {}

    static CompletionStage<Chunk> load(World world, int chunkX, int chunkZ) {
        return invoke(world, Chunk.class, chunkX, chunkZ);
    }

    static <T> CompletionStage<T> invoke(
            Object world, Class<T> chunkType, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(chunkType, "chunkType");
        try {
            Method method = world.getClass().getMethod(
                    METHOD, int.class, int.class, boolean.class);
            if (!Modifier.isPublic(method.getModifiers())) {
                throw new IllegalStateException("Paper getChunkAtAsync is not public");
            }
            Object returned = method.invoke(world, chunkX, chunkZ, true);
            if (!(returned instanceof CompletionStage<?> stage)) {
                throw new IllegalStateException(
                        "Paper getChunkAtAsync did not return CompletionStage");
            }
            return stage.thenApply(chunkType::cast);
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw new IllegalStateException(
                    "Paper public getChunkAtAsync(int,int,boolean) is unavailable", failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Paper getChunkAtAsync invocation failed",
                    failure.getCause());
        }
    }
}
