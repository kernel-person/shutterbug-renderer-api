package ke.ric.renderer.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Capture admission shared by all owners in one provider generation. */
final class StreamCaptureQueue<T> {
    private final LinkedHashSet<T> members = new LinkedHashSet<>();
    private T active, previous;
    synchronized void add(T value) { members.add(value); }
    synchronized void remove(T value) { members.remove(value); }
    synchronized T begin(List<T> ready) {
        if (active != null) return null;
        List<T> ordered = new ArrayList<>(members);
        int after = ordered.indexOf(previous);
        for (int n = 1; n <= ordered.size(); n++) {
            T candidate = ordered.get((after + n) % ordered.size());
            if (ready.contains(candidate)) { previous = candidate; return active = candidate; }
        }
        return null;
    }
    synchronized void release(T value) { if (Objects.equals(active,value)) active = null; }
    synchronized boolean idle() { return active==null; }
}
