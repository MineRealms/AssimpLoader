package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.animation.ik.TwoBonePoseConstraint;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterGripDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tick-selected item support contact; evaluation reads the primary socket from the same incoming pose. */
public final class CharacterHandContacts implements PoseModifier {
    public record Statistics(String rule,String support,float error,boolean clamped) {}
    private record Rule(CharacterGripDefinition definition,TwoBonePoseConstraint left,TwoBonePoseConstraint right) {}
    private final CharacterProfile profile;
    private final java.util.function.Supplier<CharacterEquipmentDefinition> equipmentSource;
    private final Map<String,Rule> rules=new LinkedHashMap<>();
    private final Map<HumanoidArm,Matrix4f> displays=new java.util.EnumMap<>(HumanoidArm.class);
    private final Quaternionf torsoBind;
    private final String torso;
    private AbstractClientPlayer observer;
    private CharacterEquipmentDefinition.Mount selectedMount;
    private Rule selected;
    private HumanoidArm primary;
    private float tickWeight=1;
    private boolean enabled=true;
    private Statistics statistics=new Statistics("","",0,false);

    public CharacterHandContacts(CharacterProfile profile,CharacterEquipmentDefinition equipment,AssimpScene scene) {
        this(profile,equipment,scene,()->equipment);
    }
    public CharacterHandContacts(CharacterProfile profile,CharacterEquipmentDefinition equipment,AssimpScene scene,
                                 java.util.function.Supplier<CharacterEquipmentDefinition> equipmentSource) {
        this.profile=profile;this.equipmentSource=java.util.Objects.requireNonNull(equipmentSource);
        var torsoBone=profile.bone("chest")==null?profile.bone("hips"):profile.bone("chest");
        if(torsoBone==null)throw new IllegalArgumentException("Grips need a chest or hips mapping");
        torso=torsoBone.node();
        var bind=new PoseGraph(scene.getRootNode(),SkinBindPose.locals(scene));
        if(!bind.contains(torso))throw new IllegalArgumentException("Unknown grip torso node: "+torso);
        torsoBind=bind.global(torso).getUnnormalizedRotation(new Quaternionf()).normalize();
        for(var definition:equipment.grips()) {
            for(String role:List.of("left_upper_arm","left_forearm","left_hand","right_upper_arm","right_forearm","right_hand"))
                if(profile.bone(role)==null||!bind.contains(profile.bone(role).node()))throw new IllegalArgumentException("Grip needs humanoid bone: "+role);
            if(equipment.leftHand()==null||equipment.rightHand()==null)throw new IllegalArgumentException("Grips need both equipment sockets");
            if(!bind.contains(equipment.leftHand().node())||!bind.contains(equipment.rightHand().node()))
                throw new IllegalArgumentException("Unknown grip equipment socket node");
            for(String side:List.of("left","right")) {
                String upper=node(side,"upper_arm"),middle=node(side,"forearm"),tip=node(side,"hand");
                if(!bind.isAncestor(upper,middle)||!bind.isAncestor(middle,tip))throw new IllegalArgumentException("Grip arm hierarchy is not ordered");
                if(bind.isAncestor(tip,equipment.leftHand().node())&&bind.isAncestor(tip,equipment.rightHand().node()))
                    throw new IllegalArgumentException("Both item sockets depend on one support arm");
            }
            rules.put(definition.id(),new Rule(definition,constraint("left",definition),constraint("right",definition)));
        }
    }
    private String node(String side,String role){return profile.bone(side+"_"+role).node();}
    private TwoBonePoseConstraint constraint(String side,CharacterGripDefinition definition){return new TwoBonePoseConstraint(node(side,"upper_arm"),node(side,"forearm"),node(side,"hand"),(float)Math.toRadians(definition.minimumBend()),(float)Math.toRadians(definition.maximumBend()));}
    public Statistics statistics(){return statistics;}
    public List<String> rules(){return cn.minerealms.assimploader.util.ReadOnlyList.copyOf(rules.keySet());}

    /** Resets script overrides each tick; no per-bone script callbacks or item lookups occur during solving. */
    public void prepare(AbstractClientPlayer player,boolean enabled) {
        observer=player;this.enabled=enabled;selected=null;tickWeight=1;displays.clear();
        var equipment=equipmentSource.get();
        if(!enabled||!equipment.enabled()||player.isDeadOrDying()||player.isSpectator())return;
        var using=player.isUsingItem()?player.getUsedItemHand():null;
        for(var rule:rules.values()) {
            for(var hand:InteractionHand.values()) {
                var item=player.getItemInHand(hand);var definition=rule.definition;
                if(!matches(definition,item))continue;
                var physical=hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
                if(!definition.primaryArm().equals("any")&&!definition.primaryArm().equals(physical==HumanoidArm.LEFT?"left":"right"))continue;
                boolean active=definition.states().contains("held")
                        || definition.states().contains("using")&&hand==using
                        || definition.states().contains("charged")&&CrossbowItem.isCharged(item);
                if(!active||definition.freeSupport()&&!player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND).isEmpty())continue;
                primary=physical;
                display(player,item,primary);selected=rule;return;
            }
        }
    }
    private static boolean matches(CharacterGripDefinition rule,ItemStack item) {
        if(item.isEmpty())return false;
        return rule.items().contains(BuiltInRegistries.ITEM.getKey(item.getItem()).toString())
                ||item.getTags().anyMatch(tag->rule.tags().contains(tag.location().toString()));
    }
    private void display(AbstractClientPlayer player,ItemStack item,HumanoidArm arm) {
        var context=arm==HumanoidArm.LEFT?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var model=Minecraft.getInstance().getItemRenderer().getModel(item,player.level(),player,player.getId()+context.ordinal());
        var pose=new PoseStack();ForgeHooksClient.handleCameraTransforms(pose,model,context,arm==HumanoidArm.LEFT);
        displays.put(arm,new Matrix4f(pose.last().pose()));
        String state = player.isUsingItem() && player.getUseItem() == item ? "using" : CrossbowItem.isCharged(item) ? "charged" : "held";
        selectedMount = equipmentSource.get().select(arm == HumanoidArm.LEFT ? "left" : "right",
                BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                item.getTags().map(tag -> tag.location().toString()).collect(java.util.stream.Collectors.toSet()), state,
                item.getUseAnimation().name().toLowerCase(java.util.Locale.ROOT),
                arm == HumanoidArm.LEFT ? "third_person_left_hand" : "third_person_right_hand");
    }
    /** Explicit script policy may choose a rule without duplicating its item selectors. */
    public void select(String id,boolean mainHand) {
        Rule rule=rules.get(id);if(rule==null)throw new IllegalArgumentException("Unknown hand contact: "+id);
        if(observer==null)throw new IllegalStateException("Hand contacts have not observed this tick");
        primary=mainHand?observer.getMainArm():observer.getMainArm().getOpposite();
        display(observer,observer.getItemInHand(mainHand?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND),primary);selected=rule;
    }
    /** Explicit resolved display frame is also usable by numerical fixture validation. */
    public void resolve(String id,HumanoidArm primary,Matrix4f itemDisplay) {
        Rule rule=rules.get(id);if(rule==null||!itemDisplay.isFinite())throw new IllegalArgumentException("Invalid resolved hand contact");
        this.primary=primary;displays.put(primary,new Matrix4f(itemDisplay));selected=rule;enabled=true;
        selectedMount = null;
    }
    public void weight(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Contact weight in [0,1] required");tickWeight=(float)value;}
    public void disable(){selected=null;}
    @Override public Category category(){return Category.IK;}
    @Override public Set<String> controlledBones(){if(rules.isEmpty())return Set.of();return Set.of(node("left","upper_arm"),node("left","forearm"),node("left","hand"),node("right","upper_arm"),node("right","forearm"),node("right","hand"));}
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
        if(!enabled||selected==null||tickWeight==0||selected.definition.weight()==0){statistics=new Statistics("","",0,false);return;}
        var equipment=equipmentSource.get();
        if(!equipment.enabled()){statistics=new Statistics("","",0,false);return;}
        var rule=selected.definition;String side=primary==HumanoidArm.RIGHT?"left":"right";
        var mount=selectedMount!=null?selectedMount:primary==HumanoidArm.LEFT?equipment.leftHand():equipment.rightHand();
        if (mount == null) return;
        var graph=new PoseGraph(scene.getRootNode(),locals);
        String shoulder=node(side,"upper_arm"),hand=node(side,"hand");
        if(graph.isAncestor(shoulder,mount.node()))throw new IllegalArgumentException("Support arm cannot own its primary item socket");
        var item=mount.transform(graph.global(mount.node())).mul(displays.get(primary));
        var rotation=item.getUnnormalizedRotation(new Quaternionf()).normalize().mul(rule.rotation().quaternion());
        Vector3f offset=rule.handOffsetVector();
        // With no orientation override, the offset is a wrist-space measurement from the incoming hand frame.
        if(rule.orientationWeight()==0)graph.global(hand).getUnnormalizedRotation(new Quaternionf()).normalize().transform(offset);
        else rotation.transform(offset);
        var target=item.transformPosition(rule.socketVector()).sub(offset);
        var torsoRotation=graph.global(torso).getUnnormalizedRotation(new Quaternionf()).normalize().mul(new Quaternionf(torsoBind).invert());
        var pole=rule.poleVector();if(side.equals("right"))pole.x=-pole.x;
        torsoRotation.transform(pole).add(graph.position(shoulder));
        float weight=rule.weight()*tickWeight;
        var result=(side.equals("left")?selected.left:selected.right).solve(graph,target,pole,weight);
        if(rule.orientationWeight()>0) {
            var current=graph.global(hand);var original=current.getUnnormalizedRotation(new Quaternionf()).normalize();
            graph.setGlobal(hand,new Matrix4f().translationRotateScale(current.getTranslation(new Vector3f()),
                    original.slerp(rotation,rule.orientationWeight()*weight),current.getScale(new Vector3f())));
        }
        var handFrame=graph.global(hand);
        var actual=handFrame.getUnnormalizedRotation(new Quaternionf()).normalize().transform(rule.handOffsetVector()).add(handFrame.getTranslation(new Vector3f()));
        statistics=new Statistics(rule.id(),side,actual.distance(item.transformPosition(rule.socketVector())),result.clamped());
    }
    @Override public void reset(){selected=null;observer=null;displays.clear();statistics=new Statistics("","",0,false);}
}
