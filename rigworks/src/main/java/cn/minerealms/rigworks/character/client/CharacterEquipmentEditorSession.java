package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.transform.EditableTransform;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Client-thread transaction on an existing character's equipment; never reloads model geometry. */
public final class CharacterEquipmentEditorSession {
    private static CharacterEquipmentEditorSession editing;
    interface Target {
        boolean valid();
        String profileId();
        CharacterEquipmentDefinition resource();
        CharacterEquipmentDefinition equipment();
        void equipment(CharacterEquipmentDefinition value);
        CharacterEquipmentOverrides overrides();
        Vec3 focus(HumanoidArm arm, float partialTick);
        default void edited() {}
        default void saved() {}
    }

    private final Target target;
    private CharacterEquipmentDefinition saved;
    private boolean closed;

    CharacterEquipmentEditorSession(Target target) {
        this.target = Objects.requireNonNull(target);
        requireValid();
        saved = target.equipment();
    }

    public static CharacterEquipmentEditorSession open() {
        Target target = LocalPlayerCharacter.equipmentEditorTarget();
        if (target == null || !target.valid())
            throw new IllegalStateException("No active RigWorks character is ready; load a character in a world before editing equipment");
        if (editing != null) editing.cancel();
        editing = new CharacterEquipmentEditorSession(target);
        return editing;
    }

    public boolean valid() { return !closed && target.valid(); }
    public String profileId() { return target.profileId(); }
    public boolean available(HumanoidArm arm) { return valid() && mount(target.equipment(), arm) != null; }
    public String node(HumanoidArm arm) { return requireMount(arm).node(); }

    public EditableTransform get(HumanoidArm arm) {
        var mount = requireMount(arm);
        var position = mount.position();
        return EditableTransform.from(position.x(), position.y(), position.z(), mount.rotation().quaternion(), mount.scale());
    }

    public void preview(HumanoidArm arm, EditableTransform value) {
        var original = requireMount(arm);
        Objects.requireNonNull(value, "transform");
        var rotation = value.quaternion();
        replace(arm, new CharacterEquipmentDefinition.Mount(original.node(),
                new CharacterEquipmentDefinition.Position(value.x(), value.y(), value.z()),
                new CharacterProfile.Rotation(rotation.x, rotation.y, rotation.z, rotation.w), value.scale()));
    }

    public void reset(HumanoidArm arm) {
        requireValid();
        replace(arm, mount(target.resource(), arm));
    }

    public void cancel() {
        if (closed) return;
        if (target.valid()) {target.equipment(saved);target.edited();}
        closed = true;
        if (editing == this) editing = null;
    }

    public Path save() throws IOException {
        requireValid();
        var definition = target.equipment();
        Path path = target.overrides().save(definition);
        saved = definition;
        target.saved();
        return path;
    }

    public Path outputPath() { return target.overrides().path(); }
    public boolean share() {requireValid();return CharacterVisualSharing.shareCurrent();}

    public Vec3 focus(HumanoidArm arm, float partialTick) {
        requireValid();
        if (!Float.isFinite(partialTick)) throw new IllegalArgumentException("Equipment focus partial tick must be finite");
        return target.focus(Objects.requireNonNull(arm), Math.max(0, Math.min(1, partialTick)));
    }

    private CharacterEquipmentDefinition.Mount requireMount(HumanoidArm arm) {
        requireValid();
        var mount = mount(target.equipment(), arm);
        if (mount == null) throw new IllegalStateException("No equipment mount is defined for " + arm);
        return mount;
    }

    private void replace(HumanoidArm arm, CharacterEquipmentDefinition.Mount mount) {
        var current = target.equipment();
        target.equipment(current.withHands(
                arm == HumanoidArm.RIGHT ? mount : current.rightHand(),
                arm == HumanoidArm.LEFT ? mount : current.leftHand()));
        target.edited();
    }

    private static CharacterEquipmentDefinition.Mount mount(CharacterEquipmentDefinition value, HumanoidArm arm) {
        return Objects.requireNonNull(arm) == HumanoidArm.LEFT ? value.leftHand() : value.rightHand();
    }

    private void requireValid() {
        if (!valid()) throw new IllegalStateException("The character binding, profile or world changed; reopen the equipment editor");
    }
}
