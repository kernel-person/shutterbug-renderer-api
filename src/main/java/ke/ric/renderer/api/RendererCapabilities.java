package ke.ric.renderer.api;

import java.util.Set;

/**
 * Immutable snapshot of capabilities advertised by the current provider runtime.
 * {@code packagedForProduction} is true only when the provider artifact and native library have a
 * verified matching commercial product identity; it does not describe whether development use is
 * otherwise functional.
 */
public record RendererCapabilities(String rendererVersion, String platform, boolean nativeAvailable,
                                   boolean packagedForProduction, Set<RenderSettings.Profile> supportedProfiles,
                                   Set<Attachment> supportedAttachments) {
    public RendererCapabilities { supportedProfiles = Set.copyOf(supportedProfiles); supportedAttachments = Set.copyOf(supportedAttachments); }
}
