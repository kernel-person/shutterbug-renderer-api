package ke.ric.renderer.sample;

import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.RenderResult;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

final class LiveGateEvidence {
    private LiveGateEvidence() {}

    static String verify(RenderResult first, RenderResult second, String entityName) {
        return verify(first, second, entityName, Set.of(Attachment.values()));
    }

    static String verify(RenderResult first, RenderResult second, String entityName,
                         Set<Attachment> requestedAttachments) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        requestedAttachments = Set.copyOf(requestedAttachments);
        if (first.width() != second.width() || first.height() != second.height()) {
            throw new IllegalStateException("repeated render dimensions differ");
        }
        byte[] rgba = first.rgba8();
        if (!Arrays.equals(rgba, second.rgba8())
                || !Arrays.equals(first.depth(), second.depth())
                || !Arrays.equals(first.normals(), second.normals())
                || !Arrays.equals(first.materialIds(), second.materialIds())
                || !Arrays.equals(first.objectIds(), second.objectIds())) {
            throw new IllegalStateException("repeated render output differs");
        }
        int pixels = Math.multiplyExact(first.width(), first.height());
        if (invalid(first.depth(), pixels, requestedAttachments.contains(Attachment.DEPTH))
                || invalid(first.normals(), pixels * 3,
                requestedAttachments.contains(Attachment.NORMAL))
                || invalid(first.materialIds(), pixels,
                requestedAttachments.contains(Attachment.MATERIAL_ID))
                || invalid(first.objectIds(), pixels,
                requestedAttachments.contains(Attachment.OBJECT_ID))) {
            throw new IllegalStateException("required render attachment is missing");
        }
        if (first.visibility().entities().getOrDefault(entityName, 0) <= 0
                || second.visibility().entities().getOrDefault(entityName, 0) <= 0) {
            throw new IllegalStateException("resolved entity is not visible in repeated output");
        }
        verifyDefensiveCopies(first, rgba, requestedAttachments);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean invalid(float[] values, int expected, boolean requested) {
        return requested ? values == null || values.length != expected : values != null;
    }

    private static boolean invalid(int[] values, int expected, boolean requested) {
        return requested ? values == null || values.length != expected : values != null;
    }

    private static void verifyDefensiveCopies(RenderResult result, byte[] expectedRgba,
                                              Set<Attachment> requestedAttachments) {
        byte[] changedRgba = result.rgba8();
        changedRgba[0] ^= 0x7f;
        if (!Arrays.equals(expectedRgba, result.rgba8()) || !result.rgbaBuffer().isReadOnly()) {
            throw new IllegalStateException("RGBA output is mutable");
        }
        if (requestedAttachments.contains(Attachment.DEPTH)) {
            float[] expectedDepth = result.depth();
            float[] changedDepth = result.depth();
            changedDepth[0] = Float.NaN;
            if (!Arrays.equals(expectedDepth, result.depth())) {
                throw new IllegalStateException("depth output is mutable");
            }
        }
        if (requestedAttachments.contains(Attachment.OBJECT_ID)) {
            int[] expectedObjects = result.objectIds();
            int[] changedObjects = result.objectIds();
            changedObjects[0] ^= 1;
            if (!Arrays.equals(expectedObjects, result.objectIds())) {
                throw new IllegalStateException("object output is mutable");
            }
        }
    }
}
