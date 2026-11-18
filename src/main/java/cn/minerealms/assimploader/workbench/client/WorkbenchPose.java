package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.animation.AnimationPlayer;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.SceneMetadata;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;

/** An editor owns its animation clock; sampling never changes a live entity or imported scene. */
public final class WorkbenchPose {
    private final AssimpScene scene;
    private final Map<String, AnimationPlayer> players = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, Matrix4f> paths = new HashMap<>();
    private final Map<Integer, Boolean> visible = new HashMap<>();
    private final List<PreviewSceneData.Draw> draws=new ArrayList<>();
    private final List<PreviewSceneData.Draw> staticDraws;
    private List<PreviewSceneData.Draw> sampledDraws=List.of();
    private WorkbenchDocument sampled;
    private double sampledTime = Double.NaN;
    private long frame;
    private PoseSnapshot snapshot;

    public WorkbenchPose(AssimpScene scene) {
        this.scene = scene;
        staticDraws=scene.isDynamic()?List.of():new PreviewSceneData(scene).draws();
        for (var clip : scene.getAnimations()) players.put(clip.getName(), new AnimationPlayer(clip, scene.getRootNode()));
    }

    public PoseSnapshot sample(WorkbenchDocument document, double time) {
        if (document.equals(sampled) && (time == sampledTime||!players.containsKey(document.animation().clip()))) return snapshot;
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        AnimationPlayer player = players.get(document.animation().clip());
        if (player != null) {
            double duration = player.getAnimation().getDuration() / player.getAnimation().getTicksPerSecond();
            double seconds = document.animation().loop() && duration > 0 ? time % duration : Math.min(time, duration);
            player.synchronizePhase(duration > 0 ? Math.max(0, Math.min(1, seconds / duration)) : 0);
            player.getBoneTransforms().forEach((name, matrix) -> locals.put(name, new Matrix4f(matrix)));
        }
        names.clear(); paths.clear(); visible.clear();draws.clear();
        if (scene.getRootNode() != null) collect(scene.getRootNode(), "", 0, new Matrix4f(), true, document, locals);
        if(!scene.isDynamic()) {draws.clear();draws.addAll(staticDraws);}
        sampledDraws=List.copyOf(draws);
        var morphs=new java.util.IdentityHashMap<cn.minerealms.assimploader.model.AssimpMesh,float[]>();
        if(player!=null)for(var channel:player.getAnimation().getMorphChannels())for(var mesh:scene.getMeshes())
            if(mesh.getName().equals(channel.getMeshName()))morphs.put(mesh,channel.sample(player.getCurrentTime(),mesh.getMorphTargets().size()));
        snapshot = PoseSnapshot.capture(++frame, scene, locals,morphs);
        sampled = document; sampledTime = time;
        return snapshot;
    }

    private void collect(AssimpNode node, String parent, int sibling, Matrix4f parentMatrix, boolean shown,
                         WorkbenchDocument document, Map<String, Matrix4f> locals) {
        String path = SceneMetadata.nodePath(parent, node.getName(), sibling);
        names.put(path, node.getName());
        var edit = document.nodes().getOrDefault(path, document.nodes().get(node.getName()));
        Matrix4f local = new Matrix4f(locals.getOrDefault(node.getName(), node.getTransformation()));
        if (edit != null) { local.mul(edit.transform().matrix()); shown &= edit.visible(); locals.put(node.getName(), local); }
        Matrix4f global = new Matrix4f(parentMatrix).mul(local);
        paths.put(path, global);
        for (int index : node.getMeshIndices()) {
            visible.merge(index, shown, (a, b) -> a || b);
            if(shown&&index>=0&&index<scene.getMeshCount())draws.add(new PreviewSceneData.Draw(index,new Matrix4f(global)));
        }
        for (int i = 0; i < node.getChildren().size(); i++) collect(node.getChildren().get(i), path, i, global, shown, document, locals);
    }

    public boolean visible(int mesh) { return visible.getOrDefault(mesh, true); }
    public Matrix4f node(String path) {
        Matrix4f matrix = paths.get(path);
        if (matrix == null && snapshot != null) matrix = snapshot.getNodeTransform(path);
        return matrix == null ? new Matrix4f() : new Matrix4f(matrix);
    }
    public String nodeName(String path) { return names.getOrDefault(path, path); }
    public PoseSnapshot snapshot() { return snapshot; }
    public List<PreviewSceneData.Draw> draws(){return sampledDraws;}
}
