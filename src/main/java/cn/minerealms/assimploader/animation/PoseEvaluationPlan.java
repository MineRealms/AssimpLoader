package cn.minerealms.assimploader.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Immutable validated schedule; compiling is confined to changes in modifier ownership. */
public final class PoseEvaluationPlan {
    public static final int MAX_MODIFIERS = 512;
    public static final int MAX_DEPENDENCIES = 64;
    public record Stage(String id, PoseModifier modifier, PoseModifier.Category category, Set<String> dependencies,
                        Set<String> bones) {}
    private final List<Stage> stages;
    private final List<Map<String, Object>> diagnostic;

    private PoseEvaluationPlan(List<Stage> stages) {
        this.stages = List.copyOf(stages);
        diagnostic = stages.stream().map(stage -> Map.<String, Object>of("id", stage.id(),
                "category", stage.category().name(), "after", stage.dependencies().stream().sorted().toList(),
                "bones", stage.bones().stream().sorted().toList())).toList();
    }

    public static PoseEvaluationPlan compile(Map<String, ? extends PoseModifier> modifiers) {
        if(modifiers.size()>MAX_MODIFIERS)throw new IllegalArgumentException("Pose plan exceeds 512 modifiers");
        var declared = new LinkedHashMap<String, Stage>();
        modifiers.forEach((id, modifier) -> {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Modifier ID must not be blank");
            Objects.requireNonNull(modifier, "modifier");
            var dependencies = Set.copyOf(modifier.dependencies());
            if(dependencies.size()>MAX_DEPENDENCIES)throw new IllegalArgumentException("Modifier exceeds 64 dependencies: "+id);
            if (dependencies.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Blank modifier dependency: " + id);
            declared.put(id, new Stage(id, modifier, Objects.requireNonNull(modifier.category()), dependencies,
                    Set.copyOf(modifier.controlledBones())));
        });
        var writers = new HashMap<String, String>();
        for (Stage stage : declared.values()) {
            for (String dependency : stage.dependencies()) {
                Stage required = declared.get(dependency);
                if (required == null) throw new IllegalArgumentException("Missing modifier dependency " + dependency + " for " + stage.id());
                if (required.category().ordinal() > stage.category().ordinal())
                    throw new IllegalArgumentException("Modifier dependency crosses backwards through categories: " + stage.id() + " after " + dependency);
            }
            if (stage.category() == PoseModifier.Category.PHYSICS || stage.category() == PoseModifier.Category.SPRING)
                for (String bone : stage.bones()) {
                    String previous = writers.putIfAbsent(bone, stage.id());
                    if (previous != null) throw new IllegalArgumentException("Exclusive physics/Spring writers " + previous + " and " + stage.id() + " share bone " + bone);
                }
        }
        var result = new ArrayList<Stage>();
        for (var category : PoseModifier.Category.values()) {
            var members = declared.values().stream().filter(stage -> stage.category() == category).toList();
            var index = new HashMap<String, Integer>();
            var degrees = new HashMap<String, Integer>();
            var children = new HashMap<String, List<String>>();
            for (int i = 0; i < members.size(); i++) {
                Stage stage = members.get(i); index.put(stage.id(), i);
                int degree = 0;
                for (String dependency : stage.dependencies()) if (declared.get(dependency).category() == category) {
                    degree++; children.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(stage.id());
                }
                degrees.put(stage.id(), degree);
            }
            var ready = new PriorityQueue<String>(java.util.Comparator.comparingInt(index::get));
            members.forEach(stage -> { if (degrees.get(stage.id()) == 0) ready.add(stage.id()); });
            int emitted = 0;
            while (!ready.isEmpty()) {
                String id = ready.remove(); result.add(declared.get(id)); emitted++;
                for (String child : children.getOrDefault(id, Collections.emptyList()))
                    if (degrees.compute(child, (ignored, degree) -> degree - 1) == 0) ready.add(child);
            }
            if (emitted != members.size()) throw new IllegalArgumentException("Modifier dependency cycle in " + category);
        }
        return new PoseEvaluationPlan(result);
    }

    public List<Stage> stages() { return stages; }
    public List<Map<String, Object>> diagnostic() { return diagnostic; }
}
