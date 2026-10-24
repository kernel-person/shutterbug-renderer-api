package ke.ric.renderer.api;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Location;

/** One capture, sequential independent passes, one deadline and cancellation owner. */
final class RecipePipeline {
    record Pixels(RenderResult image,List<RenderPassResult> passes,Optional<CaptureProvenance> provenance,Optional<SceneCamera> camera,FrameProgressReporter progress) {
        Pixels {passes=List.copyOf(passes);Objects.requireNonNull(provenance);Objects.requireNonNull(camera);}
        Pixels(RenderResult image,List<RenderPassResult> passes){this(image,passes,Optional.empty(),Optional.empty(),null);}
    }
    private static final ScheduledThreadPoolExecutor CLOCK=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"renderer-recipe-deadline");t.setDaemon(true);return t;});
    static { CLOCK.setRemoveOnCancelPolicy(true); }
    final FrameProgressReporter progress;
    final Renderer renderer; final RendererService generation; final RendererClient client;
    final RenderSettings settings; final FrameOptions options; final RenderEnvironment environment;
    final Location pose; final Set<UUID> excluded; final long started=System.nanoTime();
    final CompletableFuture<Pixels> result; final List<RenderPassResult> passes=new ArrayList<>();
    final List<Set<Integer>> masks=new ArrayList<>();
    final List<CompositionLease> maskLeases=new ArrayList<>();
    long conversionBytes;
    boolean validated;
    volatile CompletableFuture<?> pending; volatile RenderTask task; volatile CompositionLease lease;
    RecipePipeline(Renderer renderer,RenderSettings settings,FrameOptions options,RenderEnvironment environment,Location pose,Set<UUID> excluded) {
        this(renderer,settings,options,environment,pose,excluded,null);
    }
    RecipePipeline(Renderer renderer,RenderSettings settings,FrameOptions options,RenderEnvironment environment,Location pose,Set<UUID> excluded,FrameProgressReporter sharedProgress) {
        this.renderer=renderer;this.settings=settings;this.options=options;this.environment=Objects.requireNonNull(environment,"environment supplier returned null");this.pose=pose.clone();this.excluded=Set.copyOf(excluded);
        client=renderer.client();generation=renderer.generation();progress=sharedProgress==null?new FrameProgressReporter(renderer,options.progress(),options.recipe().orderedLayers().size()):sharedProgress;progress.sourceWorld(pose.getWorld());result=renderer.track(new CompletableFuture<>());renderer.bind(result,generation);renderer.bindWorlds(result,List.of(pose.getWorld()));
        ScheduledFuture<?> timeout=CLOCK.schedule(()->result.completeExceptionally(new RendererException(RenderFailureCode.TIMEOUT,"recipe deadline exceeded")),settings.timeout().toNanos(),TimeUnit.NANOSECONDS);
        result.whenComplete((v,e)->{timeout.cancel(false);if(e!=null){if(pending!=null)pending.cancel(true);if(task!=null)task.cancel();release();}});
    }
    void release(){if(lease!=null)lease.close();synchronized(maskLeases){for(CompositionLease reservation:maskLeases)reservation.close();}}
    void finishWith(CompletableFuture<?> output){finishWith(output,true);}
    void finishWith(CompletableFuture<?> output,boolean releaseOnSuccess){
        if(releaseOnSuccess)progress.bind(output);
        long remaining=settings.timeout().toNanos()-(System.nanoTime()-started);
        ScheduledFuture<?> timer=CLOCK.schedule(()->output.completeExceptionally(new RendererException(RenderFailureCode.TIMEOUT,"recipe conversion deadline exceeded")),Math.max(0,remaining),TimeUnit.NANOSECONDS);
        output.whenComplete((v,e)->{timer.cancel(false);if(e!=null)result.cancel(true);if(e!=null||releaseOnSuccess)release();});
    }
    void validate() {
        if(validated)return;validated=true;
        for(RenderLayer layer:options.recipe().orderedLayers()) {
            RenderLighting lighting=layer.lighting()==null?options.lighting():layer.lighting();
            if(!lighting.isWorld()||layer.occlusion()==Occlusion.SELECTED_ONLY) {
                LightingCapabilities caps=client.lightingCapabilities();
                if(!caps.supported()||lighting.lights().size()>caps.maxLights()||lighting.lights().stream().filter(VirtualLight::shadows).count()>caps.maxShadowLights()||lighting.lights().stream().anyMatch(l->l.range()>caps.maxRange()||l.intensity()>caps.maxIntensity()))
                    throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"lighting or primary visibility exceeds provider capabilities");
            }
        }
        boolean transform=!options.content().isAll()||!environment.isCaptured()||!options.appearance().isDefault()||!excluded.isEmpty()||options.recipe().orderedLayers().stream().anyMatch(l->l.entityOnly()||l.content()!=null||l.environment()!=null||l.appearance()!=null||l.lighting()!=null||l.occlusion()!=Occlusion.RESPECT_SCENE)||!options.lighting().isWorld();
        if(transform&&!client.supportedSceneOperations().contains(SceneOperation.TRANSFORM_SCENE))throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"recipe needs scene transformation support");
        if(options.recipe().orderedLayers().stream().anyMatch(RenderLayer::entityOnly)&&!client.capabilities().supportedAttachments().contains(Attachment.OBJECT_ID))throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"entity layers require native OBJECT_ID evidence");
        if(composite()) { lease=client.reserveComposition(totalEstimate());if(result.isDone())lease.close(); }
    }
    long totalEstimate(){try{return Math.addExact(estimate(settings,options.recipe()),conversionBytes);}catch(ArithmeticException e){throw new RendererException(RenderFailureCode.MEMORY_LIMIT,"recipe output memory estimate overflow",e);}}
    boolean composite(){return options.recipe().orderedLayers().size()!=1||options.recipe().orderedLayers().getFirst().entityOnly()||options.recipe().orderedLayers().getFirst().brightness()!=1||options.recipe().orderedLayers().getFirst().opacity()!=1||options.recipe().orderedLayers().getFirst().color().mode()!=LayerColor.Mode.ORIGINAL;}
    static long estimate(RenderSettings settings,RenderRecipe recipe){
        try { long pixels=Math.multiplyExact((long)settings.width(),settings.height());long retained=0;
            for(RenderLayer layer:recipe.orderedLayers()){long b=4;for(Attachment a:settings.attachments())b+=a==Attachment.NORMAL?12:4;if(layer.entityOnly()&&!settings.attachments().contains(Attachment.OBJECT_ID))b+=4;retained=Math.addExact(retained,Math.multiplyExact(pixels,b));}
            // Retained passes, defensive copies, final RGBA, blend scratch, image and palette conversions.
            return Math.addExact(Math.multiplyExact(retained,2),Math.addExact(Math.multiplyExact(pixels,32),128L*128*8));
        } catch(ArithmeticException e){throw new RendererException(RenderFailureCode.MEMORY_LIMIT,"recipe memory estimate overflow",e);}
    }
    CompletableFuture<Pixels> capture(CaptureRequest request,double fov){
        try {validate();if(result.isDone())return result;progress.stage(FrameProgress.Stage.CAPTURE,0);CompletableFuture<Scene> capture=client.capture(request,fov,options.roll(),progress::capture).toCompletableFuture();link(capture);capture.whenComplete((source,error)->renderer.dispatch(result,()->{if(error!=null)result.completeExceptionally(Renderer.unwrap(error));else next(source,0);}));}
        catch(Throwable e){result.completeExceptionally(e);}return result;
    }
    CompletableFuture<Pixels> render(Scene source){try{validate();next(source,0);}catch(Throwable e){result.completeExceptionally(e);}return result;}
    void link(CompletableFuture<?> future){pending=future;Renderer.follow(result,future);if(result.isDone())future.cancel(true);}
    void next(Scene source,int index){
        if(result.isDone())return;
        renderer.requireGeneration(generation);
        RenderLayer layer=options.recipe().orderedLayers().get(index);
        SceneContent content=layer.content()==null?options.content():options.content().intersect(layer.content());
        if(!excluded.isEmpty())content=content.entities(EntitySelector.ids(excluded).not());
        EntitySelector selector=content.entities().and(layer.selector());
        Set<Integer> mask=null;
        if(layer.entityOnly()&&!selector.isAll()) {
            List<CapturedEntity> entities=source.capturedEntities().orElseThrow(()->new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"selective entity layers require captured entity metadata"));
            if(!entities.isEmpty()) {
                // Includes both temporary boxed hash entries and the retained immutable ID set.
                CompositionLease reservation=client.reserveComposition(Math.multiplyExact((long)entities.size(),128));
                synchronized(maskLeases){maskLeases.add(reservation);}
                if(result.isDone()){reservation.close();return;}
            }
            Set<Integer> selected=new HashSet<>();for(CapturedEntity entity:entities)if(selector.matches(entity.uuid(),entity.type(),entity.living()))selected.add(entity.objectId());mask=Set.copyOf(selected);
        }
        masks.add(mask);
        RenderEnvironment passEnvironment=layer.environment()==null?environment:layer.environment().inherit(environment);
        RenderAppearance appearance=layer.appearance()==null?options.appearance():layer.appearance().inherit(options.appearance());
        RenderLighting lighting=layer.lighting()==null?options.lighting():layer.lighting();
        // Passes derive from the original capture. Preserve the legacy provider fast path for
        // ordinary world lighting/visibility; explicit creative passes use replacement controls.
        SceneTransform transform=lighting.isWorld()&&layer.occlusion()==Occlusion.RESPECT_SCENE
                ?new SceneTransform(content,passEnvironment,appearance)
                :new SceneTransform(content,passEnvironment,appearance,lighting,layer.occlusion(),selector);
        Scene target=transform.isIdentity()?source:client.deriveScene(source,transform);
        Set<Attachment> attachments=new HashSet<>(settings.attachments());if(layer.entityOnly())attachments.add(Attachment.OBJECT_ID);
        long remaining=settings.timeout().toNanos()-(System.nanoTime()-started);if(remaining<=0)throw new RendererException(RenderFailureCode.TIMEOUT,"recipe deadline exceeded");
        task=client.submit(new RenderJob(1,target,settings.withAttachments(attachments).withTimeout(Duration.ofNanos(remaining))));
        RenderTask current=task;progress.watch(current,index);if(result.isDone()){current.cancel();return;}
        current.completion().whenComplete((frame,error)->{
          Runnable completed=()->{
            if(result.isDone())return;
            if(error!=null){result.completeExceptionally(Renderer.unwrap(error));return;}
            progress.rendered(index);
            passes.add(new RenderPassResult(index,frame.withWorldCapturedNanos(source.worldCapturedNanos())));
            if(index+1<options.recipe().orderedLayers().size()){next(source,index+1);return;}
            if(!composite()){result.complete(new Pixels(frame,passes,source.provenance(),source.effectiveCamera(),progress));return;}
            progress.stage(FrameProgress.Stage.CONVERSION,0);
            CompletableFuture<RenderResult> conversion=renderer.convert(generation,()->compose(options.recipe(),passes,masks));link(conversion);
            conversion.whenComplete((image,failure)->{if(failure!=null)result.completeExceptionally(Renderer.unwrap(failure));else {progress.stage(FrameProgress.Stage.CONVERSION,10000);result.complete(new Pixels(image,passes,source.provenance(),source.effectiveCamera(),progress));}});
          };
          if(index+1<options.recipe().orderedLayers().size())renderer.dispatch(result,completed);
          else {try{renderer.checkWorkerGeneration(generation);completed.run();}catch(Throwable failure){result.completeExceptionally(failure);}}
        });
    }
    static RenderResult compose(RenderRecipe recipe,List<RenderPassResult> passes){return compose(recipe,passes,Collections.nCopies(passes.size(),null));}
    static RenderResult compose(RenderRecipe recipe,List<RenderPassResult> passes,List<Set<Integer>> masks){
        RenderResult first=passes.getFirst().result();byte[] output=new byte[Math.multiplyExact(Math.multiplyExact(first.width(),first.height()),4)];for(int i=3;i<output.length;i+=4)output[i]=(byte)255;
        for(int n=0;n<passes.size();n++){RenderLayer layer=recipe.orderedLayers().get(n);RenderResult pass=passes.get(n).result();byte[] pixels=pass.rgba8();int[] ids=layer.entityOnly()?pass.objectIds():null;
            if(layer.entityOnly()&&ids==null)throw new RendererException(RenderFailureCode.UNSUPPORTED_CAPABILITY,"entity layer omitted required OBJECT_ID evidence");
            for(int i=0;i<output.length;i+=4){if((i&65535)==0&&Thread.currentThread().isInterrupted())throw new CancellationException();if(ids!=null&&(ids[i/4]==0||masks.get(n)!=null&&!masks.get(n).contains(ids[i/4])))continue;double alpha=layer.opacity()*(pixels[i+3]&255)/255.0;for(int c=0;c<3;c++){double value=Math.min(255,layer.color().channel(pixels[i+c]&255,c)*layer.brightness());output[i+c]=(byte)Math.round((output[i+c]&255)*(1-alpha)+value*alpha);}}
        }
        return new RenderResult(first.width(),first.height(),output,null,null,null,null,VisibilityStatistics.empty(first.width()*first.height()),new RenderTimings(-1,-1,-1,-1,-1,-1,-1,-1));
    }
    static boolean visible(FrameOptions options,org.bukkit.World world,List<RenderPassResult> passes,Set<UUID> hiddenAtStart){
        if(options.viewer()==null)return true;Set<UUID> hidden=options.hidden(world);if(!hiddenAtStart.containsAll(hidden))return false;
        for(RenderPassResult pass:passes)for(VisibleEntity entity:pass.result().visibleEntities())if(hidden.contains(entity.uuid()))return false;return true;
    }
}
