package docs.examples;

import ke.ric.renderer.api.Attachment;
import ke.ric.renderer.api.RenderResult;
import ke.ric.renderer.api.RenderTimings;

public final class ResultInspectionExample {
    public static String summary(RenderResult result) {
        float[] depth = result.depth();
        String depthState = depth == null ? Attachment.DEPTH + " not requested" : depth.length + " depth pixels";
        RenderTimings timings = result.timings();
        return depthState + ", total=" + timings.totalNanos()
                + "ns, visible entities=" + result.visibility().entities();
    }
}
