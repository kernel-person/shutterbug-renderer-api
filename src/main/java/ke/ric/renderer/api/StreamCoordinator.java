package ke.ric.renderer.api;

import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** One fair four-chunk capture budget for a discovered provider generation, across plugins. */
final class StreamCoordinator {
    private static final Map<RendererService,StreamCoordinator> GENERATIONS=new IdentityHashMap<>();
    final RendererService service;
    final List<RenderStream> streams=new ArrayList<>();
    final StreamCaptureQueue<RenderStream> captures=new StreamCaptureQueue<>();
    private BukkitTask timer;
    private RenderStream timerOwner;
    private StreamCoordinator(RendererService service) { this.service=service; }
    static StreamCoordinator join(RenderStream stream) {
        RendererService service=stream.renderer.service();
        StreamCoordinator coordinator=GENERATIONS.computeIfAbsent(service,StreamCoordinator::new);
        coordinator.streams.add(stream);coordinator.captures.add(stream);
        try { coordinator.ensureTimer();return coordinator; }
        catch(Throwable failure) { coordinator.streams.remove(stream);coordinator.captures.remove(stream);coordinator.timerOwner=null;coordinator.discardIfIdle();throw failure; }
    }
    private void ensureTimer() {
        if(timer!=null || streams.isEmpty())return;
        timerOwner=streams.getFirst();
        timer=timerOwner.renderer.owner().getServer().getScheduler().runTaskTimer(timerOwner.renderer.owner(),()->tick(System.nanoTime()),1,1);
    }
    void tick(long now) {
        for(RenderStream stream:List.copyOf(streams))stream.prepare(now);
        List<RenderStream> ready=streams.stream().filter(s->s.active() && s.waiting!=null && !s.busy).toList();
        RenderStream next=captures.begin(ready);
        if(next!=null)next.capture(now);
    }
    void captured(RenderStream stream) { captures.release(stream);discardIfIdle(); }
    void remove(RenderStream stream) {
        streams.remove(stream);captures.remove(stream);
        if(stream==timerOwner || streams.isEmpty()) {
            if(timer!=null)timer.cancel();timer=null;timerOwner=null;
            try { ensureTimer(); }
            catch(Throwable failure) { timerOwner=null;for(RenderStream remaining:List.copyOf(streams))remaining.schedulingFailed(failure); }
        }
        discardIfIdle();
    }
    private void discardIfIdle() { if(streams.isEmpty() && captures.idle())GENERATIONS.remove(service,this); }
}
