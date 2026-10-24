package ke.ric.renderer.api;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle handle for a live camera feed. All configured callbacks and map/entity mutations run
 * on the server thread. Use {@link #stop()} or {@link #close()} when the feed is no longer needed;
 * owner disable also stops it. Stop removes only owned temporary output, never saved photos.
 *
 * <p>Transient queue pressure, stale evidence or unloaded required chunks can skip frames. A feed
 * without usable evidence for one second enters NO_SIGNAL and can recover. Permanent errors end
 * the feed and complete {@link #completion()} exceptionally. Frame rate is a target, not a guarantee.</p>
 */
public final class RenderStream implements AutoCloseable {
    /** Current externally useful state; absence of recent evidence produces NO_SIGNAL. */
    public enum Status {
 /** Output setup has begun but no usable frame has been delivered. */ STARTING,
 /** A usable frame has been delivered recently. */ LIVE,
 /** No usable frame for at least one second; built-in outputs show No signal until recovery. */ NO_SIGNAL,
 /** Normal terminal stop or source end. */ STOPPED,
 /** Terminal failure; inspect lastError() and completion(). */ FAILED
}
    final Renderer renderer;
    final StreamBuilder config;
    final List<StreamOutput> outputs=new ArrayList<>();
    final String token;
    private final AtomicBoolean active=new AtomicBoolean(true);
    private final AtomicBoolean stopRequested=new AtomicBoolean();
    private final CompletableFuture<Void> completion=new CompletableFuture<>();
    private StreamCoordinator coordinator;
    private volatile Status status=Status.STARTING;
    private volatile Throwable lastError;
    private volatile RenderedFrame lastFrame;
    private CompletableFuture<?> captureFuture,conversionFuture;
    private RecipePipeline pipeline;
    private FrameProgressReporter progress;
    private RecipePipeline retainedFramePipeline;
    private RenderEnvironment environment;
    private Set<UUID> hidden=Set.of();
    private final List<MapSink> sinks=new ArrayList<>();
    private Scene scene;
    private Location scenePose;
    private long worldCaptured,started,lastUsable,nextDue,sequence,published;
    boolean busy;
    private boolean preparing;
    Location waiting;

    RenderStream(StreamBuilder config) {
        this.config=config;renderer=config.renderer;
        if(!renderer.owner().getServer().isPrimaryThread())throw new IllegalStateException("stream start requires the server thread");
        if(config.frames.isEmpty() && config.outputs.isEmpty() && config.frameOptions().sinks().isEmpty())throw new IllegalStateException("stream needs a frame consumer or output");
        token=renderer.owner().getName()+"/"+UUID.randomUUID();started=System.nanoTime();
        try {
            renderer.client();
            progress=new FrameProgressReporter(renderer,config.frameOptions().progress(),config.frameOptions().recipe().orderedLayers().size());progress.bindStream(completion,()->active()&&config.source.available());
            for(MapSink sink:config.frameOptions().sinks()){sinks.add(sink);sink.status(Status.STARTING);}
            StreamOutputCleanup.install(renderer);
            StreamOutputCleanup.live(token);
            for(StreamBuilder.OutputSpec spec:config.outputs) {
                StreamOutput output=spec.create(renderer,token);outputs.add(output);output.open();
            }
            coordinator=StreamCoordinator.join(this);
            java.lang.ref.WeakReference<RenderStream> reference=new java.lang.ref.WeakReference<>(this);
            renderer.onClose(()-> { RenderStream stream=reference.get();if(stream!=null)stream.stop(); });
            renderer.track(completion);
            renderer.bind(completion,coordinator.service);
            completion.whenComplete((v,error)-> { if(active())dispatch(()->finish(error instanceof CancellationException ? null : error)); });
            bindWorlds(null);
        } catch(Throwable failure) { finish(failure);throw failure; }
    }
    /**
     * Reports whether a stop or terminal failure has been requested. This is a snapshot, not a lease
     * that guarantees a subsequent operation will succeed.
     * @return true while the feed can still produce frames
     */
    public boolean active() { return active.get() && !stopRequested.get(); }
    /**
     * Returns the current output/lifecycle state. NO_SIGNAL can recover; STOPPED and FAILED are terminal.
     * @return current stream status
     */
    public Status status() { return status; }
    /**
     * Returns the latest observed error, including errors from which the stream can recover.
     * A nonnull error alone does not imply terminal failure; inspect status() or completion().
     * @return the most recent error, or null if none has occurred
     */
    public Throwable lastError() { return lastError; }
    /**
     * Returns the most recently published frame while active. Composite frames retain their
     * composition reservation until replacement or stop. The frame is immutable.
     * @return the latest frame, or null before first delivery and after stop
     */
    public RenderedFrame lastFrame() { return lastFrame; }
    /**
     * Observes stream termination. Normal stop/source end completes normally; permanent failures
     * complete exceptionally. Cancelling this future stops the stream. Ordinary future continuations
     * have normal Java threading semantics; never wait for termination on the server thread.
     * @return the stream's completion future
     */
    public CompletableFuture<Void> completion() { return completion; }
    /**
     * Requests stop, cancels owned work and removes only this stream's temporary outputs.
     * Idempotent and callable from any thread; Bukkit cleanup is dispatched to the server thread.
     */
    public void stop() { stopRequested.set(true);dispatch(()->finish(null)); }
    /** Equivalent to {@link #stop()}. */
    @Override public void close() { stop(); }

    void prepare(long now) {
        if(!active() || preparing)return;
        preparing=true;
        try {
            if(!renderer.owner().isEnabled() || renderer.service()!=coordinator.service || !renderer.client().active()) {
                finish(new RendererException(RenderFailureCode.PROVIDER_DISABLED,"stream owner or renderer provider is no longer active"));return;
            }
            if(!config.source.available()) { finish(null);return; }
            for(StreamOutput output:List.copyOf(outputs))if(!output.usable()) { output.close();outputs.remove(output); }
            if(outputs.isEmpty() && config.frames.isEmpty() && sinks.isEmpty()) { finish(null);return; }
            if(now-(lastUsable==0?started:lastUsable)>=1_000_000_000L && status!=Status.NO_SIGNAL) {
                outputs.forEach(StreamOutput::noSignal);status=Status.NO_SIGNAL;for(MapSink sink:sinks)sink.status(status);
            }
            if(busy || now<nextDue)return;
            Location current=config.source.sample();
            if(current==null) { finish(null);return; }
            bindWorlds(current.getWorld());if(!active())return;
            if(scene!=null && !config.fresh && StreamSource.reusable(scenePose,current,worldCaptured,now,config.reuse)) {
                waiting=null;
                sampleOptions(current);
                render(reframe(scene,current),current,now,worldCaptured);
            } else { scene=null;waiting=current; }
        } catch(Throwable failure) { failure(failure); }
        finally { preparing=false; }
    }
    void capture(long now) {
        if(!active() || waiting==null) { coordinator.captured(this);return; }
        Location pose=waiting;waiting=null;busy=true;
        try {
            sampleOptions(pose);
            Set<UUID> excluded=new HashSet<>(config.exclusions);excluded.addAll(hidden);excluded.addAll(config.frameOptions().entityIds());
            if(config.source.entity!=null)excluded.add(config.source.entity.getUniqueId());
            progress.beginFrame();
            pipeline=new RecipePipeline(renderer,config.settings,config.frameOptions(),environment,pose,hidden,progress);
            pipeline.conversionBytes=conversionBytes();
            pipeline.validate();
            progress.stage(FrameProgress.Stage.CAPTURE,0);
            CompletableFuture<Scene> future=renderer.client().capture(new CaptureRequest(pose,config.radius,config.settings,config.frameOptions().chunks(),excluded),config.fov,config.frameOptions().roll(),progress::capture).toCompletableFuture();
            pipeline.link(future);
            captureFuture=future;
            future.whenComplete((captured,error)->dispatch(()-> {
                coordinator.captured(this);captureFuture=null;
                if(!active())return;
                busy=false;
                if(pipeline!=null&&pipeline.result.isCompletedExceptionally()){
                    try{pipeline.result.join();}catch(Throwable original){failure(original);return;}
                }
                if(error!=null) { failure(error);return; }
                long evidence=captured.worldCapturedNanos()==0?now:captured.worldCapturedNanos();
                scene=captured;scenePose=pose;worldCaptured=evidence;
                try {
                    long sampled=System.nanoTime();Location current=config.source.sample();
                    if(current==null) { finish(null);return; }
                    bindWorlds(current.getWorld());if(!active())return;
                    if(!config.fresh && !StreamSource.reusable(pose,current,evidence,sampled,config.reuse)) { scene=null;if(pipeline!=null)pipeline.result.cancel(true);return; }
                    // A world switch requires new evidence even when using fresh mode.
                    if(!pose.getWorld().getUID().equals(current.getWorld().getUID())) { scene=null;if(pipeline!=null)pipeline.result.cancel(true);return; }
                    if(config.fresh) {
                        // A fresh capture renders its own camera, even when capture was slow or
                        // the tracked camera has since left the smooth reuse coverage.
                        render(captured,pose,now,evidence);
                    } else {
                        Scene target=samePose(pose,current)?captured:reframe(captured,current);
                        render(target,current,sampled,evidence);
                    }
                } catch(Throwable failure) { failure(failure); }
            }));
            if(!active())future.cancel(true);
        } catch(Throwable failure) { coordinator.captured(this);busy=false;failure(failure); }
    }
    private void sampleOptions(Location pose){
        environment=java.util.Objects.requireNonNull(config.frameOptions().environment().get(),"environment supplier returned null");
        hidden=config.frameOptions().hidden(pose.getWorld());
    }
    private Scene reframe(Scene source,Location current) { return renderer.client().reframeScene(source,StreamSource.camera(current,config.fov,config.frameOptions().roll()),config.reuse); }
    private void render(Scene target,Location camera,long cameraNanos,long evidenceNanos) {
        busy=true;long ticket=++sequence;
        nextDue=cameraNanos+1_000_000_000L/config.fps;
        try {
            List<StreamOutput> targets=List.copyOf(outputs);
            List<MapSink> sinkTargets=List.copyOf(sinks);
            Set<UUID> frameHidden=Set.copyOf(hidden);
            RendererService generation=coordinator.service;
            if(pipeline==null){progress.beginFrame();pipeline=new RecipePipeline(renderer,config.settings,config.frameOptions(),environment,camera,frameHidden,progress);}
            RecipePipeline current=pipeline;current.conversionBytes=conversionBytes();
            CompletableFuture<RecipePipeline.Pixels> rendering=current.render(target);
            CompletableFuture<List<byte[][]>> prepared=new CompletableFuture<>();conversionFuture=prepared;current.finishWith(prepared,false);
            Renderer.follow(prepared,rendering);
            AtomicReference<RecipePipeline.Pixels> rendered=new AtomicReference<>();
            prepared.whenComplete((palettes,error)->dispatch(()-> {
                pipeline=null;conversionFuture=null;
                if(!active()){current.release();return;}
                if(error!=null){busy=false;failure(error);return;}
                try{publish(ticket,rendered.get(),camera,cameraNanos,evidenceNanos,targets,palettes,sinkTargets,frameHidden,current);}
                finally{if(retainedFramePipeline!=current)current.release();}
            }));
            rendering.whenComplete((pixels,error)-> {
                if(prepared.isDone()||!active())return;
                if(error!=null){prepared.completeExceptionally(error);return;}
                rendered.set(pixels);
                if(targets.isEmpty()&&sinkTargets.isEmpty()){prepared.complete(List.of());return;}
                try {
                    progress.stage(FrameProgress.Stage.CONVERSION,0);
                    CompletableFuture<List<byte[][]>> conversion=renderer.convert(generation,()-> {
                        List<byte[][]> palettes=new ArrayList<>();java.util.Map<String,byte[][]> converted=new java.util.HashMap<>();
                        for(StreamOutput output:targets){if(!active())throw new CancellationException();String key=output.columns+"x"+output.rows;palettes.add(converted.computeIfAbsent(key,k->MinecraftMapConverter.tiles(RendererImages.fit(pixels.image(),output.columns*128,output.rows*128),config.frameOptions().maps().dither())));}
                        if(!sinkTargets.isEmpty())palettes.add(converted.computeIfAbsent("1x1",k->new byte[][]{RendererImages.palette(pixels.image(),128,128,config.frameOptions().maps())}));
                        return palettes;
                    });
                    Renderer.follow(prepared,conversion);
                    conversion.whenComplete((palettes,failure)->{if(failure==null){progress.stage(FrameProgress.Stage.CONVERSION,10000);prepared.complete(palettes);}else prepared.completeExceptionally(failure);});
                }catch(Throwable failure){prepared.completeExceptionally(failure);}
            });
        }catch(Throwable failure){busy=false;failure(failure);}
    }
    private long conversionBytes(){
        long bytes=0;Set<String> dimensions=new HashSet<>();
        try{for(StreamOutput output:outputs){String key=output.columns+"x"+output.rows;if(dimensions.add(key))bytes=Math.addExact(bytes,Math.multiplyExact((long)output.columns*output.rows*128*128,32));}if(!sinks.isEmpty()&&dimensions.add("1x1"))bytes=Math.addExact(bytes,128L*128*32);return bytes;}
        catch(ArithmeticException error){throw new RendererException(RenderFailureCode.MEMORY_LIMIT,"stream output memory estimate overflow",error);}
    }
    private boolean publishable(long ticket,long evidence,long now) {
        return active() && ticket>published && (!config.fresh && now-evidence>=0 && now-evidence<=config.reuse.maxWorldAge().toNanos() || config.fresh);
    }
    void schedulingFailed(Throwable failure) { failure(failure); }
    private void publish(long ticket,RecipePipeline.Pixels result,Location camera,long cameraNanos,long evidenceNanos,List<StreamOutput> targets,List<byte[][]> palettes,List<MapSink> sinkTargets,Set<UUID> frameHidden,RecipePipeline current) {
        busy=false;
        if(!config.source.available()) { finish(null);return; }
        try {
            // Provider generation is checked again after off-thread conversion.
            if(renderer.service()!=coordinator.service || !renderer.client().active()) { finish(new RendererException(RenderFailureCode.PROVIDER_DISABLED,"renderer provider changed before stream publication"));return; }
            if(!RecipePipeline.visible(config.frameOptions(),camera.getWorld(),result.passes(),frameHidden)){scene=null;return;}
            long admitted=System.nanoTime();
            if(!publishable(ticket,evidenceNanos,admitted)) { scene=null;return; }
            for(int i=0;i<targets.size();i++)if(outputs.contains(targets.get(i)) && targets.get(i).usable())targets.get(i).update(palettes.get(i));
            RenderedFrame frame=new RenderedFrame(renderer,result,camera.getWorld(),cameraNanos,evidenceNanos,admitted);
            if(retainedFramePipeline!=null)retainedFramePipeline.release();retainedFramePipeline=current;
            published=ticket;lastUsable=admitted;boolean changed=status!=Status.LIVE;status=Status.LIVE;lastFrame=frame;
            for(MapSink sink:sinkTargets){if(!active())break;if(changed)sink.status(status);if(!active())break;sink.accept(new MapFrame(palettes.get(targets.size())[0],frame));}
            for(var consumer:config.frames) { if(!active())break;consumer.accept(frame); }
            if(config.fresh)scene=null;
            // A completed frame may already be overdue for its next period. Reuse can render
            // immediately; fresh/moved cameras only mark waiting for fair coordinator capture.
            if(active())prepare(System.nanoTime());
        } catch(Throwable failure) { failure(failure); }
    }
    private void failure(Throwable failure) {
        Throwable cause=unwrap(failure);lastError=cause;busy=false;
        if(pipeline!=null){pipeline.result.cancel(true);pipeline=null;}
        if(!active())return;
        if(cause instanceof RendererException typed && (typed.code()==RenderFailureCode.QUEUE_FULL
                || typed.code()==RenderFailureCode.MISSING_CHUNK || typed.code()==RenderFailureCode.STALE_SCENE)) {
            try { for(var observer:config.errors)observer.accept(cause); }
            catch(Throwable observerFailure) { if(observerFailure!=cause)cause.addSuppressed(observerFailure);finish(cause);return; }
            if(typed.code()!=RenderFailureCode.QUEUE_FULL)scene=null;
            nextDue=System.nanoTime()+50_000_000L;return;
        }
        finish(cause);
    }
    private void finish(Throwable error) {
        if(!active.compareAndSet(true,false))return;
        if(progress!=null)progress.stop();
        waiting=null;scene=null;lastFrame=null;if(retainedFramePipeline!=null){retainedFramePipeline.release();retainedFramePipeline=null;}status=error==null?Status.STOPPED:Status.FAILED;
        if(error!=null)lastError=unwrap(error);
        if(error!=null)for(var observer:config.errors)try { observer.accept(lastError); }
            catch(Throwable observerFailure) { if(observerFailure!=lastError)lastError.addSuppressed(observerFailure); }
        if(pipeline!=null){pipeline.result.cancel(true);pipeline.release();}
        if(captureFuture!=null)captureFuture.cancel(true);
        if(conversionFuture!=null)conversionFuture.cancel(true);
        if(coordinator!=null)coordinator.remove(this);
        Throwable cleanupFailure=null;
        for(MapSink sink:sinks){try{sink.status(status);}catch(Throwable failure){if(cleanupFailure==null)cleanupFailure=failure;else cleanupFailure.addSuppressed(failure);}try{sink.close();}catch(Throwable failure){if(cleanupFailure==null)cleanupFailure=failure;else cleanupFailure.addSuppressed(failure);}}sinks.clear();
        for(StreamOutput output:outputs)try { output.close(); }catch(Throwable failure) { if(cleanupFailure==null)cleanupFailure=failure;else cleanupFailure.addSuppressed(failure); }
        outputs.clear();StreamOutputCleanup.finished(token);
        if(error==null && cleanupFailure==null)completion.complete(null);
        else completion.completeExceptionally(error!=null?unwrap(error):cleanupFailure);
    }
    private void bindWorlds(org.bukkit.World cameraWorld) {
        var affected=new HashSet<org.bukkit.World>();
        if(cameraWorld!=null)affected.add(cameraWorld);
        for(StreamOutput output:outputs)if(output.world!=null)affected.add(output.world);
        renderer.bindWorlds(completion,affected);
    }
    private void dispatch(Runnable work) {
        if(renderer.owner().getServer().isPrimaryThread()) { work.run();return; }
        try { renderer.mainThreadExecutor().execute(work); }
        catch(Throwable failure) {
            // Owner-close hooks perform physical cleanup on main; never mutate Bukkit from a worker.
            stopRequested.set(true);status=Status.FAILED;lastError=failure;
            completion.completeExceptionally(failure);
        }
    }
    private static Throwable unwrap(Throwable failure) {
        while((failure instanceof CompletionException || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause()!=null)failure=failure.getCause();return failure;
    }
    private static boolean samePose(Location a,Location b) {
        return a.getX()==b.getX()&&a.getY()==b.getY()&&a.getZ()==b.getZ()&&a.getYaw()==b.getYaw()&&a.getPitch()==b.getPitch();
    }
}
