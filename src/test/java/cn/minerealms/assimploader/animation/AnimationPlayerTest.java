package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.*;

class AnimationPlayerTest {
    @Test
    void constantZeroDurationClipNeverProducesNaN() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var animation = new AssimpAnimation(AIAnimation.calloc(stack).mDuration(0).mTicksPerSecond(25),
                    0, CoordinateSystem.MINECRAFT_LEFT_HANDED);
            var player = new AnimationPlayer(animation);
            player.play();
            player.update(1);
            assertEquals(0, player.getCurrentTime());
            player.setLooping(false);
            player.update(1);
            assertFalse(player.isPlaying());
            assertEquals(0, player.getCurrentTime());
        }
    }

    @Test
    void sparseRotationChannelKeepsBindTranslationAndScale() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var node = BoneHierarchyBuilderTest.node(stack, "Joint", 2, 3, 4);
            node.mTransformation().a1(2).b2(3).c3(4);
            AINodeAnim channel = AINodeAnim.calloc(stack);
            channel.mNodeName().data(stack.UTF8("Joint"));
            AIQuatKey.Buffer keys = AIQuatKey.calloc(1, stack);
            keys.get(0).mTime(0).mValue().w(1);
            channel.mRotationKeys(keys);
            AIAnimation nativeAnimation = AIAnimation.calloc(stack).mDuration(10).mTicksPerSecond(25)
                    .mChannels(stack.pointers(channel.address()));
            var animation = new AssimpAnimation(nativeAnimation, 0, CoordinateSystem.MINECRAFT_LEFT_HANDED);
            var player = new AnimationPlayer(animation, new AssimpNode(node, CoordinateSystem.MINECRAFT_LEFT_HANDED));
            player.play();
            player.update(0.2);
            Matrix4f actual = player.getBoneTransforms().get("Joint");
            assertTrue(actual.equals(new Matrix4f().translation(2, 3, 4).scale(2, 3, 4), 1e-5f));
            assertThrows(UnsupportedOperationException.class,
                    () -> player.getBoneTransforms().put("Other", new Matrix4f()));
            player.setSpeed(-1);
            player.update(0.4);
            assertEquals(5, player.getCurrentTime(), 1e-6);
            player.stop();
            assertEquals(0, player.getCurrentTime());
        }
    }
}
