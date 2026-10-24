package ke.ric.renderer.api;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CaptureRequestExclusionTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void oldConstructorDefaultsToNoExcludedEntities() throws Exception {
        CaptureRequest request = new CaptureRequest(new Location(null, 0, 64, 0), 16,
                RenderSettings.classic(128, 128), 2);
        assertEquals(Set.of(), exclusions(request));
    }

    @Test void exclusionsAreAnImmutableDefensiveCopy() throws Exception {
        Set<UUID> input = new HashSet<>(Set.of(TARGET));
        CaptureRequest request = create(input);
        input.clear();

        assertEquals(Set.of(TARGET), exclusions(request));
        assertThrows(UnsupportedOperationException.class, () -> exclusions(request).clear());
    }

    @Test void nullExclusionSetIsRejected() {
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> create(null));
        assertInstanceOf(NullPointerException.class, failure.getCause());
    }

    @Test void nullExcludedUuidIsRejected() {
        Set<UUID> invalid = new HashSet<>();
        invalid.add(null);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> create(invalid));
        assertInstanceOf(NullPointerException.class, failure.getCause());
    }

    private static CaptureRequest create(Set<UUID> exclusions) throws Exception {
        Constructor<CaptureRequest> constructor = assertDoesNotThrow(() -> CaptureRequest.class.getConstructor(
                Location.class, double.class, RenderSettings.class, int.class, Set.class));
        return constructor.newInstance(new Location(null, 0, 64, 0), 16,
                RenderSettings.classic(128, 128), 2, exclusions);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> exclusions(CaptureRequest request) throws Exception {
        var accessor = assertDoesNotThrow(() -> CaptureRequest.class.getMethod("excludedEntityIds"));
        return (Set<UUID>) accessor.invoke(request);
    }
}
