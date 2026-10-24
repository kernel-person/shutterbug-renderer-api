package ke.ric.renderer.api;

import java.util.Set;

/**
 * Immutable snapshot of capabilities advertised by the current provider runtime.
 * {@code packagedForProduction} is true only when the provider artifact and native library have a
 * verified matching commercial product identity; it does not describe whether development use is
 * otherwise functional.
 * @param rendererVersion provider-reported renderer version
 * @param platform provider-reported platform identifier
 * @param nativeAvailable whether the current runtime can perform native rendering
 * @param packagedForProduction whether the provider and native artifact have a matching production identity
 * @param supportedProfiles immutable set of quality intents accepted by this runtime
 * @param supportedAttachments immutable set of optional output attachments accepted by this runtime
 */
public record RendererCapabilities(String rendererVersion, String platform, boolean nativeAvailable,
                                   boolean packagedForProduction, Set<RenderSettings.Profile> supportedProfiles,
                                   Set<Attachment> supportedAttachments) {
    public RendererCapabilities { supportedProfiles = Set.copyOf(supportedProfiles); supportedAttachments = Set.copyOf(supportedAttachments); }
}
