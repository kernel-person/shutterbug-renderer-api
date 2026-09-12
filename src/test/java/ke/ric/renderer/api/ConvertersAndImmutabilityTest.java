package ke.ric.renderer.api;

import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ConvertersAndImmutabilityTest {
    @Test void resultAndNestedDataAreDefensivelyCopied() {
        byte[] rgba={1,2,3,4}; float[] depth={5}; int[] ids={6};
        RenderResult result=new RenderResult(1,1,rgba,depth,null,ids,ids,
                new VisibilityStatistics(Map.of("minecraft:pig",1),Map.of(),Map.of(),Map.of(),0,1),new RenderTimings(1,2,3,4,10));
        rgba[0]=99;depth[0]=99;ids[0]=99; assertEquals(1,result.rgba8()[0]);assertEquals(5,result.depth()[0]);assertEquals(6,result.objectIds()[0]);
        result.rgba8()[0]=88;assertEquals(1,result.rgba8()[0]);assertTrue(result.rgbaBuffer().isReadOnly());
    }
    @Test @SuppressWarnings("deprecation") void noneMapConversionUsesExactBukkitPaletteAndPngKeepsAlpha() {
        RenderResult result=new RenderResult(1,1,new byte[]{(byte)247,(byte)233,(byte)163,17},null,null,null,null,VisibilityStatistics.empty(1),new RenderTimings(0,0,0,0,0));
        assertArrayEquals(new byte[]{MapPalette.matchColor(new Color(247,233,163))},MinecraftMapConverter.quantize(result,MapDither.NONE));
        byte[] png=PngConverter.encode(result);assertEquals((byte)0x89,png[0]);assertEquals('P',png[1]);
    }
    @Test void renderJobRequiresSceneAndSettingsAndKeepsAssetOrderImmutable() {
        Scene scene=new TestScene(1,3,2);
        RenderSettings settings=RenderSettings.classic(3,2);
        assertThrows(NullPointerException.class,()->new RenderJob(1,null,settings));
        assertThrows(NullPointerException.class,()->new RenderJob(1,scene,null));
        RenderJob job=new RenderJob(1,scene,settings,List.of("top","base"));
        assertEquals(List.of("top","base"),job.assetHashes());
        assertThrows(UnsupportedOperationException.class,()->job.assetHashes().add("late"));
    }
    @Test void settingsRejectNonPositiveTimeoutsAndCapabilitiesNameProfilesTruthfully() {
        assertThrows(IllegalArgumentException.class,()->new RenderSettings(1,RenderSettings.Profile.CLASSIC,1,1,Set.of(),1,1,Duration.ZERO));
        RendererCapabilities capabilities=new RendererCapabilities("v","test",true,false,
                Set.of(RenderSettings.Profile.CLASSIC),Set.of(Attachment.DEPTH));
        assertEquals(Set.of(RenderSettings.Profile.CLASSIC),capabilities.supportedProfiles());
        assertFalse(capabilities.supportedAttachments().contains(Attachment.NORMAL));
    }

    private record TestScene(int version,int width,int height) implements Scene {}
}
