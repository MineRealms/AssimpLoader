package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.config.GpuBatchPolicy;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimpshowcase.deer.client.DeerShowcaseRenderer;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.rendering.CachedMeshRenderer;
import cn.minerealms.assimploader.rendering.GpuInstanceBatcher;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Explicit deterministic crowd benchmark. Client-world behavior has a separate integration run. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class BatchSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("batchtest.enabled");
    private static final String RUN=Long.toString(System.currentTimeMillis());
    private static long changed=System.currentTimeMillis();
    private static int stage=-1;
    private static boolean done,pending;
    private static Probe screen;
    private static final int SIZE=512;
    private BatchSmokeTest() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || done || event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            require(System.currentTimeMillis()-changed<180_000,"timeout stage "+stage);
            if(stage<0) {
                if(mc.screen instanceof TitleScreen) {
                    if(Boolean.getBoolean("batchtest.safety")) {
                        screen=new Probe();screen.load();stage=7;screen.prepare();screen.verifyAdmission();screen.release();done=true;
                        AssimpLoader.LOGGER.info("[BatchSmoke] SAFETY SUCCESS run={} capacity/frame/geometry/cleanup/invalid-input checks",RUN);mc.stop();return;
                    }
                    screen=new Probe();screen.load();stage=0;screen.prepare();mc.setScreen(screen);changed=System.currentTimeMillis();
                }
                return;
            }
            if(pending || System.currentTimeMillis()-changed<10_000 || screen.frames<72 || !screen.texturesReady()) return;
            screen.verify();pending=true;
            String name="assimp-batch-"+RUN+"-"+stage+".png";
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()->{
                try {
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)),"missing capture");
                    if(++stage==8) {
                        screen.report();screen.release();done=true;
                        AssimpLoader.LOGGER.info("[BatchSmoke] SUCCESS run={} stages=8 64/256 deer plus 256-bone fixtures, INSTANCED/MDI/HiZ masks",RUN);
                        mc.stop();
                    } else {screen.prepare();pending=false;changed=System.currentTimeMillis();}
                } catch(Exception failure) {fail(failure);}
            }));
        } catch(Exception|LinkageError failure) {fail(failure);}
    }
    private static void require(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
    private static void fail(Throwable error) {
        if(done) return;done=true;AssimpLoader.LOGGER.error("[BatchSmoke] FAILED stage="+stage,error);
        if(screen!=null) screen.release();Minecraft.getInstance().stop();
    }
    private record Draw(AssimpScene scene,PoseSnapshot pose,Matrix4f view,int light,int overlay) {}
    private static final class Probe extends Screen {
        private final TextureTarget target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);
        private final MultiBufferSource.BufferSource buffers=MultiBufferSource.immediate(new BufferBuilder(4096));
        private final List<Draw> draws=new ArrayList<>();
        private final List<String> results=new ArrayList<>();
        private final Matrix4f projection=new Matrix4f().ortho(-18,18,-18,18,1,128);
        private AssimpScene deer,highBone,staticProbe;
        private SkinnedMeshRenderer immediate;
        private GpuInstanceBatcher batch;
        private boolean[] reference;
        private byte[] referenceColors,lastColors;
        private int frames,measured;
        private long submitNanos,simulationNanos;
        private final List<int[]> queries=new ArrayList<>();
        private final List<Long> gpuTimes=new ArrayList<>(),cpuTimes=new ArrayList<>();
        Probe() {super(Component.literal("AssimpLoader GPU Batch Validation"));}
        void load() throws Exception {
            require(GpuInstanceBatcher.available(),"GL 4.3 required; fallback cannot pass batch validation");
            deer=new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(),DeerShowcaseRenderer.MODEL_RESOURCE,ModelImportMode.DYNAMIC);
            highBone=new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(),
                    new ResourceLocation("assimploader","models/validation/highbone_probe.glb"),ModelImportMode.DYNAMIC);
            staticProbe=new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(),
                    new ResourceLocation("assimploader","models/showcase/probe_textured.fbx"),ModelImportMode.STATIC);
            immediate=new SkinnedMeshRenderer();immediate.initialize();
            require(deer.hasSkeleton()&&highBone.getMeshes().get(0).getBoneNames().size()==256,"fixtures lost skeletons");
            target.setClearColor(0,0,0,0);
            AssimpLoader.LOGGER.info("[BatchSmoke] GL={} GPU={} driver={} deerMeshes={} deerVertices={} highBoneVertices={}",
                    GL11.glGetString(GL11.GL_VERSION),GL11.glGetString(GL11.GL_RENDERER),GL11.glGetString(GL11.GL_VENDOR),
                    deer.getMeshes().size(),deer.getMeshes().stream().mapToInt(mesh->mesh.getVertexCount()).sum(),highBone.getMeshes().get(0).getVertexCount());
            AssimpLoader.LOGGER.info("[BatchSmoke] Resource static probe meshes={} vertices={} indices={} sourcePack={}",staticProbe.getMeshes().size(),
                    staticProbe.getMeshes().stream().mapToInt(mesh->mesh.getVertexCount()).sum(),
                    staticProbe.getMeshes().stream().mapToInt(mesh->mesh.getIndices().length).sum(),
                    Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("assimploader","models/showcase/probe_textured.fbx")).orElseThrow().sourcePackId());
        }
        void prepare() throws Exception {
            if(batch!=null) batch.close();batch=null;
            for(int[] pair:queries) {GL15.glDeleteQueries(pair[0]);GL15.glDeleteQueries(pair[1]);}queries.clear();
            frames=measured=0;submitNanos=0;gpuTimes.clear();cpuTimes.clear();draws.clear();
            int count=stage<4?64:256,side=stage<4?8:16;
            long started=System.nanoTime();
            for(int i=0;i<count;i++) {
                var state=new AnimationState("batch-deer-"+i,deer);state.initialize();
                var clips=deer.getAnimations();
                require(state.transitionTo(clips.get(1).getName(),0),"deer animation missing");
                state.update((i%37)/37f);
                var pose=state.getPoseSnapshot();
                float x=(i%side-(side-1)*.5f)*32/side,y=(i/side-(side-1)*.5f)*32/side;
                Matrix4f view=new Matrix4f().translation(x,y,-32).scale(stage<4?1.1f:.65f).rotateY(.5f);
                int light=i%3==0?0x00b000b0:0x00f000f0;
                int overlay=i%7==0?OverlayTexture.pack(0,true):OverlayTexture.NO_OVERLAY;
                draws.add(new Draw(deer,pose,view,light,overlay));state.close();
            }
            // A second texture group and a 256-bone mesh exercise mixed arena ranges and baseInstance.
            for(int i=0;i<4;i++) {
                var state=new AnimationState("batch-highbone-"+i,highBone);state.initialize();state.transitionTo("Wave",0);state.update(i*.15f);
                draws.add(new Draw(highBone,state.getPoseSnapshot(),new Matrix4f().translation(-12+i*8,-16,-28).scale(.5f),0x00f000f0,OverlayTexture.NO_OVERLAY));state.close();
            }
            draws.add(new Draw(staticProbe,null,new Matrix4f().translation(13,-13,-27).rotateY(.6f).scale(1.5f),0x00f000f0,OverlayTexture.NO_OVERLAY));
            draws.add(new Draw(staticProbe,null,new Matrix4f().translation(13,-9,-27).rotateY(-1.2f).scale(1.5f),0x00b000b0,OverlayTexture.NO_OVERLAY));
            simulationNanos=System.nanoTime()-started;
            int mode=stage%4;
            if(mode!=0) batch=new GpuInstanceBatcher(new GpuBatchPolicy(mode==1?GpuBatchPolicy.Mode.INSTANCED:GpuBatchPolicy.Mode.MDI,
                    8192,128,64,mode==3,512,.0005));
            AssimpLoader.LOGGER.info("[BatchSmoke] Prepared stage={} deer={} models={} backend={} independentPoseBuildMs={}",
                    stage,count,draws.size(),mode==0?"IMMEDIATE":batch.policy(),simulationNanos/1e6);
        }
        boolean texturesReady() {
            for(var scene:List.of(deer,highBone,staticProbe)) for(var material:scene.getMaterials()) {
                ModelTextureCache.getTexture(scene,material);var status=ModelTextureCache.status(scene,material);
                require(!status.failed(),"texture failed");if(!status.ready()) return false;
            }
            return true;
        }
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            if(done || pending) return;
            try {
                graphics.fill(0,0,width,height,0xff252a2e);graphics.flush();
                draw();
                int side=Math.min(width-32,height-36);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderColor(1,1,1,1);
                RenderSystem.setShaderTexture(0,target.getColorTextureId());
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
                Matrix4f pose=graphics.pose().last().pose();int x=(width-side)/2,y=18;
                BufferBuilder quad=Tesselator.getInstance().getBuilder();quad.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
                quad.vertex(pose,x,y+side,0).uv(0,0).endVertex();quad.vertex(pose,x+side,y+side,0).uv(1,0).endVertex();
                quad.vertex(pose,x+side,y,0).uv(1,1).endVertex();quad.vertex(pose,x,y,0).uv(0,1).endVertex();
                BufferUploader.drawWithShader(quad.end());RenderSystem.disableBlend();
                graphics.flush();frames++;poll();
            } catch(Exception|LinkageError failure) {fail(failure);}
        }
        private void draw() {
            Minecraft mc=Minecraft.getInstance();
            Matrix4f previousProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
            PoseStack modelView=RenderSystem.getModelViewStack();modelView.pushPose();
            try {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                // A deterministic opaque wall occludes the left part; leave a sky/depth hole at the top.
                GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,SIZE/3,SIZE*3/4);
                GL11.glClearDepth(.1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glClearDepth(1);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                modelView.setIdentity();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);
                mc.gameRenderer.lightTexture().turnOnLightLayer();mc.gameRenderer.overlayTexture().setupOverlayColor();
                int[] query=null;
                if(frames>=64 && queries.size()<64) {
                    query=new int[]{GL15.glGenQueries(),GL15.glGenQueries()};GL33.glQueryCounter(query[0],GL33.GL_TIMESTAMP);
                }
                long start=System.nanoTime();
                if(batch==null) {
                    for(var draw:draws) {
                        PoseStack poses=new PoseStack();poses.last().pose().set(draw.view);poses.last().normal().set(draw.view).invert().transpose();
                        if(draw.pose==null) {require(CachedMeshRenderer.render(draw.scene,SceneTransforms.instances(draw.scene),poses,buffers,draw.light,draw.overlay),"static baseline fell back");continue;}
                        for(var mesh:draw.scene.getMeshes()) {
                        var material=ModelTextureCache.material(draw.scene,mesh);
                        immediate.renderWithPose(mesh,draw.pose.getBoneTransforms(mesh),poses,buffers,ModelTextureCache.getTexture(draw.scene,mesh),mc.getTextureManager(),
                                draw.light,draw.overlay,material==null?new float[]{1,1,1,1}:material.getDiffuseColor());
                        }
                    }
                } else {
                    batch.begin();for(var draw:draws) require(batch.submit(draw.scene,draw.pose,draw.view,draw.light,draw.overlay),"batch rejected representative fixture: "+batch.lastRejection());
                    batch.flush(projection,target.getDepthTextureId(),SIZE,SIZE,0,frames);
                }
                long duration=System.nanoTime()-start;
                if(frames>=64) {submitNanos+=duration;measured++;cpuTimes.add(duration);}
                if(query!=null) {GL33.glQueryCounter(query[1],GL33.GL_TIMESTAMP);queries.add(query);}
            } finally {
                modelView.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(previousProjection,VertexSorting.ORTHOGRAPHIC_Z);
                mc.getMainRenderTarget().bindWrite(true);
            }
        }
        private void poll() {
            var iterator=queries.iterator();
            while(iterator.hasNext()) {
                var pair=iterator.next();
                if(GL15.glGetQueryObjecti(pair[1],GL15.GL_QUERY_RESULT_AVAILABLE)==0) continue;
                gpuTimes.add(GL33.glGetQueryObjectui64(pair[1],GL15.GL_QUERY_RESULT)-GL33.glGetQueryObjectui64(pair[0],GL15.GL_QUERY_RESULT));
                GL15.glDeleteQueries(pair[0]);GL15.glDeleteQueries(pair[1]);iterator.remove();
            }
        }
        void verify() throws Exception {
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL error");
            boolean[] mask=mask();int pixels=0,diff=0,union=0;
            for(int i=0;i<mask.length;i++) {if(mask[i]) pixels++;if(reference!=null) {if(mask[i]!=reference[i]) diff++;if(mask[i]||reference[i]) union++;}}
            require(pixels>400,"blank crowd target");
            int colorDiff=0;
            if(stage%4==0) {reference=mask;referenceColors=lastColors;diff=0;}
            else {
                require(diff/(double)Math.max(1,union)<.005,"batch silhouette mismatch "+diff+"/"+union);
                for(int i=0;i<mask.length;i++) if(mask[i]&&reference[i]) {
                    for(int channel=0;channel<3;channel++) if(Math.abs((lastColors[i*4+channel]&255)-(referenceColors[i*4+channel]&255))>3) {colorDiff++;break;}
                }
                require(colorDiff/(double)pixels<.01,"batch material/light/overlay mismatch "+colorDiff+"/"+pixels);
            }
            long culled=0;
            if(batch!=null) {
                var stats=batch.statistics();require(stats.lastInstances()==draws.stream().mapToInt(value->value.scene.getMeshes().size()).sum(),"missing fixture instances");
                require(stats.lastSubmissions()<stats.lastInstances()/4,"batch did not reduce API submissions");
                if(stage%4==3) {require(stats.lastHiZ(),"HiZ silently fell back");for(int visible:batch.readVisibility()) if(visible==0) culled++;require(culled>0,"wall did not cull any instances");}
            }
            require(measured>=8&&!gpuTimes.isEmpty(),"missing warmed timing");
            String row=String.format(Locale.ROOT,"%d,%d,%s,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d,%.3f",stage,draws.size(),stage%4==0?"IMMEDIATE":batch.policy().mode()+(stage%4==3?"_HIZ":""),
                    batch==null?draws.stream().mapToInt(value->value.scene.getMeshes().size()).sum():batch.statistics().lastInstances(),
                    batch==null?draws.stream().mapToInt(value->value.scene.getMeshes().size()).sum():batch.statistics().lastSubmissions(),culled,
                    submitNanos/1e6/measured,percentile(cpuTimes,.95)/1e6,percentile(gpuTimes,.5)/1e6,pixels,diff,simulationNanos/1e6);
            results.add(row);AssimpLoader.LOGGER.info("[BatchSmoke] RESULT {} statistics={}",row,batch==null?"immediate":batch.statistics());
            AssimpLoader.LOGGER.info("[BatchSmoke] Color mismatch pixels={} (RGB tolerance=3) static + skinned + lighting + overlay",colorDiff);
            if(stage==7) {HiZGpuChecks.verify();verifyAdmission();}
        }
        private boolean[] mask() {
            ByteBuffer pixels=MemoryUtil.memAlloc(SIZE*SIZE*4);boolean[] result=new boolean[SIZE*SIZE];
            int fbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            try {GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                for(int i=0;i<result.length;i++) result[i]=(pixels.get(i*4+3)&255)>127;
                lastColors=new byte[SIZE*SIZE*4];pixels.get(lastColors);
            } finally {GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);MemoryUtil.memFree(pixels);}return result;
        }
        private void verifyAdmission() {
            try(var bounded=new GpuInstanceBatcher(new GpuBatchPolicy(GpuBatchPolicy.Mode.MDI,1,1,1,false,64,0))) {
                bounded.begin();var draw=draws.get(0);
                require(bounded.submit(draw.scene,draw.pose,draw.view,draw.light,draw.overlay),"one-instance admission failed");
                require(!bounded.submit(draw.scene,draw.pose,draw.view,draw.light,draw.overlay),"instance limit ignored");
                require(bounded.statistics().accepted()==1&&bounded.statistics().rejected()==1,"capacity rejection changed accepted instances");
                bounded.begin();
                require(!bounded.submit(deer,draws.get(draws.size()-6).pose,new Matrix4f(),draw.light,draw.overlay),"foreign scene snapshot accepted");
                require(!bounded.submit(deer,draw.pose,new Matrix4f().m00(Float.NaN),draw.light,draw.overlay),"non-finite transform accepted");
                bounded.close();require(bounded.statistics().geometryBytes()==0,"closed batch retained geometry accounting");
            } catch(Exception failure) {throw new IllegalStateException("Admission/cleanup verification failed",failure);}
            try(var bounded=new GpuInstanceBatcher(new GpuBatchPolicy(GpuBatchPolicy.Mode.MDI,1024,1,1,false,64,0))) {
                bounded.begin();var draw=draws.get(draws.size()-6);
                require(draw.scene==highBone,"wrong palette-budget fixture");
                int admitted=0;
                while(bounded.submit(draw.scene,draw.pose,draw.view,draw.light,draw.overlay)) {require(++admitted<100,"frame budget ignored");}
                require(admitted>0&&bounded.lastRejection().equals("frame budget"),"palette budget did not constrain admission");
                bounded.begin();
                long staticVertices=staticProbe.getMeshes().stream().mapToLong(mesh->(long)mesh.getVertexCount()*64).sum();
                if(staticVertices>1024*1024*3/4) {
                    require(!bounded.submit(staticProbe,null,new Matrix4f(),0x00f000f0,OverlayTexture.NO_OVERLAY),"geometry budget ignored");
                    require(bounded.lastRejection().startsWith("geometry budget"),"unexpected geometry rejection");
                }
                AssimpLoader.LOGGER.info("[BatchSmoke] Instance limit, 1 MiB palette budget ({} admitted), geometry rejection and closed accounting verified",admitted);
            } catch(Exception failure) {throw new IllegalStateException("Frame/geometry budget verification failed",failure);}
        }
        private long percentile(List<Long> values,double fraction) {var sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*fraction)));}
        void report() throws Exception {
            Path directory=Path.of("../build/reports");Files.createDirectories(directory);
            Files.writeString(directory.resolve("gpu-batch-"+RUN+".csv"),"stage,models,backend,meshInstances,submissions,culled,cpuMeanMs,cpuP95Ms,gpuMedianMs,pixels,maskDiff,independentPoseBuildMs\n"+String.join("\n",results)+"\n");
        }
        void release() {
            if(batch!=null) batch.close();batch=null;if(immediate!=null) immediate.cleanup();immediate=null;
            for(int[] pair:queries) {GL15.glDeleteQueries(pair[0]);GL15.glDeleteQueries(pair[1]);}queries.clear();target.destroyBuffers();
        }
    }
}
