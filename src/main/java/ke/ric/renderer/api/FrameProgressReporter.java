package ke.ric.renderer.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;

/** Independent operation lifetimes sharing one bounded latest-value mailbox per operation and one ticker. */
final class FrameProgressReporter {
    private final Hub hub;
    private final int passCount;
    private CompletableFuture<?> lifetime;
    private World sourceWorld;
    private FrameProgress latest,delivered;
    private RenderTask rendering;
    private int passIndex;
    private long revision;

    FrameProgressReporter(Renderer renderer,Consumer<FrameProgress> observer,int passCount) {
        this(new Hub(renderer,observer),passCount);
    }
    private FrameProgressReporter(Hub hub,int passCount){this.hub=hub;this.passCount=passCount;}
    void bind(CompletableFuture<?> lifetime) {
        synchronized(hub) {
            if(hub.observer==null||hub.stopped)return;
            if(this.lifetime!=null&&this.lifetime!=lifetime)throw new IllegalStateException("progress lifetime already bound");
            this.lifetime=Objects.requireNonNull(lifetime);
            if(!hub.operations.contains(this))hub.operations.add(this);
            hub.start();
        }
    }
    void bindStream(CompletableFuture<?> lifetime,BooleanSupplier available){synchronized(hub){hub.streamLifetime=lifetime;hub.streamAvailable=Objects.requireNonNull(available);bind(lifetime);}}
    void sourceWorld(World world){synchronized(hub){sourceWorld=Objects.requireNonNull(world);}}
    FrameProgressReporter fork(CompletableFuture<?> lifetime){synchronized(hub){return fork(lifetime,sourceWorld);}}
    FrameProgressReporter fork(CompletableFuture<?> lifetime,World world){synchronized(hub){var child=new FrameProgressReporter(hub,passCount);child.sourceWorld=world;child.bind(lifetime);return child;}}
    private boolean worldAvailable(){return sourceWorld==null||hub.renderer.worldAvailable(sourceWorld);}
    void offer(FrameProgress progress) {
        synchronized(hub) {
            if(hub.observer==null||hub.stopped)return;
            if(!Objects.requireNonNull(progress).equals(latest)){latest=progress;revision=++hub.revision;}
        }
    }
    void stage(FrameProgress.Stage stage,int basisPoints) {synchronized(hub){rendering=null;offer(new FrameProgress(stage,-1,passCount,basisPoints));}}
    void watch(RenderTask task,int index) {
        synchronized(hub){if(hub.observer==null||hub.stopped)return;rendering=task;passIndex=index;offer(new FrameProgress(FrameProgress.Stage.RENDER,index,passCount,task.progress().basisPoints()));}
    }
    void rendered(int index) {synchronized(hub){rendering=null;offer(new FrameProgress(FrameProgress.Stage.RENDER,index,passCount,10000));}}
    void capture(FrameProgress value) {
        synchronized(hub){if(value.stage()!=FrameProgress.Stage.CAPTURE||latest!=null&&latest.stage()!=FrameProgress.Stage.CAPTURE)return;offer(new FrameProgress(FrameProgress.Stage.CAPTURE,-1,passCount,value.basisPoints()));}
    }
    void beginFrame(){synchronized(hub){latest=null;delivered=null;rendering=null;}}
    void stop(){synchronized(hub){hub.stop();}}

    /** Serializes all callbacks for a request/stream, including its independent output operations. */
    private static final class Hub {
        final Renderer renderer;
        final RendererService generation;
        final Consumer<FrameProgress> observer;
        final List<FrameProgressReporter> operations=new ArrayList<>();
        CompletableFuture<?> streamLifetime;
        BooleanSupplier streamAvailable;
        BukkitTask timer;
        long revision;
        boolean stopped;
        Hub(Renderer renderer,Consumer<FrameProgress> observer){this.renderer=renderer;generation=renderer.generation();this.observer=observer;}
        void start(){if(timer==null)timer=renderer.owner().getServer().getScheduler().runTaskTimer(renderer.owner(),this::tick,1,1);}
        void stop(){stopped=true;operations.clear();cancelTimer();}
        void cancelTimer(){if(timer!=null){timer.cancel();timer=null;}}
        boolean streamAvailable(){return streamLifetime==null||!streamLifetime.isDone()&&streamAvailable.getAsBoolean();}
        private synchronized void tick() {
            if(stopped)return;
            try {
                if(!streamAvailable()){stop();return;}
                operations.removeIf(p->p.lifetime.isCompletedExceptionally());
                renderer.requireGeneration(generation);
                FrameProgressReporter next=null;
                for(FrameProgressReporter operation:operations) {
                    if(!operation.worldAvailable())continue;
                    if(operation.rendering!=null&&!operation.rendering.completion().toCompletableFuture().isDone())
                        operation.offer(new FrameProgress(FrameProgress.Stage.RENDER,operation.passIndex,operation.passCount,operation.rendering.progress().basisPoints()));
                    if(operation.latest!=null&&!operation.latest.equals(operation.delivered)&&(next==null||operation.revision>next.revision))next=operation;
                }
                if(!streamAvailable()){stop();return;}
                if(next!=null&&!next.lifetime.isCompletedExceptionally()&&next.worldAvailable()) {
                    next.delivered=next.latest;
                    try{observer.accept(next.latest);}catch(Throwable failure){next.lifetime.completeExceptionally(failure);}
                }
                operations.removeIf(p->p.lifetime.isDone());
                if(operations.isEmpty())cancelTimer();
            } catch(Throwable failure) {
                for(FrameProgressReporter operation:List.copyOf(operations))operation.lifetime.completeExceptionally(failure);
                stop();
            }
        }
    }
}
