package ke.ric.renderer.sample;

import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.RenderSettings;
import ke.ric.renderer.api.RendererCapabilities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Capability-guarded plan for the sample's two public renderer workflows. */
record SampleCapabilities(boolean coldCaptureAvailable, boolean manualExtremeAvailable,
                          Set<Attachment> optionalAttachments, List<String> messages) {
    SampleCapabilities {
        optionalAttachments = Set.copyOf(optionalAttachments);
        messages = List.copyOf(messages);
    }

    static SampleCapabilities inspect(RendererCapabilities advertised) {
        Objects.requireNonNull(advertised, "advertised");
        boolean nativeAvailable = advertised.nativeAvailable();
        boolean normal = advertised.supportedProfiles().contains(RenderSettings.Profile.NORMAL);
        boolean extreme = advertised.supportedProfiles().contains(RenderSettings.Profile.EXTREME);
        boolean depth = advertised.supportedAttachments().contains(Attachment.DEPTH);
        boolean objectId = advertised.supportedAttachments().contains(Attachment.OBJECT_ID);

        List<String> messages = new ArrayList<>();
        if (!nativeAvailable) {
            messages.add("renderer native runtime unavailable; workflows skipped safely");
        }
        if (!normal) messages.add("NORMAL profile unsupported; cold capture skipped");
        if (!extreme) messages.add("EXTREME (HQ4) profile unsupported; manual scene skipped");
        if (!depth) messages.add("DEPTH attachment unsupported; color output remains available");
        if (!objectId) {
            messages.add("OBJECT_ID attachment unsupported; color output remains available");
        }

        EnumSet<Attachment> attachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : Attachment.values()) {
            if (advertised.supportedAttachments().contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return new SampleCapabilities(nativeAvailable && normal, nativeAvailable && extreme,
                attachments, messages);
    }
}
