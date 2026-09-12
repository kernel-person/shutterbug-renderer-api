package ke.ric.renderer.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ColdCoordinateTest {
    @Test
    void persistedGenerationMapsToDeterministicNonOverlappingChunkRegions() {
        ColdCoordinate first = ColdCoordinate.forGeneration(1);
        ColdCoordinate second = ColdCoordinate.forGeneration(2);
        ColdCoordinate wrappedRow = ColdCoordinate.forGeneration(1025);

        assertEquals(new ColdCoordinate(1, 16064, -16000), first);
        assertEquals(new ColdCoordinate(2, 16128, -16000), second);
        assertEquals(new ColdCoordinate(1025, 16064, -16064), wrappedRow);
        assertNotEquals(first.chunkX(), second.chunkX());
        assertNotEquals(first.chunkZ(), wrappedRow.chunkZ());
        assertEquals(first.chunkZ(), first.cameraBlockZ() >> 4,
                "camera must remain in the center chunk on negative coordinates");
    }
}
