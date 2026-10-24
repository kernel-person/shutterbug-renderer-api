package ke.ric.renderer.api;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdeParameterMetadataTest {
    @Test void fluentMethodsRetainMeaningfulNamesWithoutSourceAttachments() throws Exception {
        names(CaptureRequest.class.getMethod("withRadius", double.class), "radiusBlocks");
        names(CaptureRequest.class.getMethod("withChunksPerTick", int.class), "chunksPerTick");
        names(CaptureRequest.class.getMethod("excluding", Set.class), "excludedEntityIds");
        names(RenderSettings.class.getMethod("withWorkers", int.class), "workers");
        names(RenderSettings.class.getMethod("withMemoryLimit", long.class), "memoryLimitBytes");
        names(RenderSettings.class.getMethod("withTimeout", Duration.class), "timeout");
        names(RenderSettings.class.getMethod("withAttachments", Set.class), "attachments");
        names(RenderSettings.class.getMethod("withAttachments", Attachment.class, Attachment[].class), "first", "additional");
        names(RenderSettings.class.getMethod("withResolution", int.class, int.class), "width", "height");
        names(CaptureRequest.class.getMethod("at", Location.class), "camera");
        names(RenderSettings.class.getMethod("withProfile", RenderSettings.Profile.class), "profile");
    }

    @Test void abstractInterfaceMethodsRetainNamesWithoutDebugCodeTables() throws Exception {
        names(RendererClient.class.getMethod("capture", CaptureRequest.class), "request");
        names(SceneBuilder.class.getMethod("camera", double.class, double.class, double.class,
                double.class, double.class, double.class), "x", "y", "z", "yawRadians", "pitchRadians", "fovRadians");
    }

    @Test void overloadsRetainNamesAsWellAsRecordCanonicalConstructors() throws Exception {
        names(CaptureRequest.class.getConstructor(Location.class, double.class, RenderSettings.class, int.class),
                "camera", "radius", "settings", "chunksPerTick");
    }

    private static void names(Executable executable, String... expected) {
        assertTrue(Arrays.stream(executable.getParameters()).allMatch(Parameter::isNamePresent),
                () -> "MethodParameters missing from " + executable);
        assertArrayEquals(expected, Arrays.stream(executable.getParameters()).map(Parameter::getName).toArray(String[]::new));
    }
}
