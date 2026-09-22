package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.asset.JsonAssetLoader;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiCollider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;

/** Exercises the real EF broad/narrow collision tests without starting a world. */
public class V6ReachCheck {
    public static void main(String[] args) throws Exception {
        Armature armature;
        try (var input = Files.newInputStream(Path.of("build/epicfight-v6/biped.json"))) {
            armature = new JsonAssetLoader(input, ResourceLocation.parse("epicfight:entity/biped"))
                    .loadArmature(Armature::new);
        }
        var sweepField = PoemScythePlayerAnimations.class.getDeclaredField("FRONT_SWEEP");
        sweepField.setAccessible(true);
        var childrenField = MultiCollider.class.getDeclaredField("colliders");
        childrenField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var samples = (List<OBBCollider>) childrenField.get(sweepField.get(null));
        var hitboxMethod = Collider.class.getDeclaredMethod("getHitboxAABB");
        hitboxMethod.setAccessible(true);

        var cases = List.of(
                new Target("point blank", 0, 0, .35, 1.8, true),
                new Target("small close mob", 0, 0, .6, .5, true),
                new Target("front", 0, 0, 2, 1.8, true),
                new Target("extended reach", 0, 0, 4.4, 1.8, true),
                new Target("left front", 1.1, 0, 2, 1.8, true),
                new Target("right front", -1.1, 0, 2, 1.8, true),
                new Target("air slash below", 0, -1, 2, 1.8, true),
                new Target("out of reach", 0, 0, 5.1, 1.8, false),
                new Target("behind player", 0, 0, -1, 1.8, false),
                new Target("far to the side", 3, 0, 2, 1.8, false),
                new Target("above reach", 0, 3, 2, 1.8, false));
        int checked = 0;
        for (Vec3 player : List.of(Vec3.ZERO, new Vec3(123, 70, -234))) {
            for (int yaw = 0; yaw < 360; yaw += 45) {
                var model = OpenMatrix4f.createRotatorDeg(yaw, Vec3f.Y_AXIS);
                // EF's root collider uses an empty pose, independent of Tool_R
                // spin/scale, then removes the root bind translation.
                var root = JointTransform.empty().getAnimationBoundMatrix(armature.rootJoint,
                        new OpenMatrix4f()).removeTranslation();
                var world = OpenMatrix4f.createTranslation(-(float) player.x, (float) player.y, -(float) player.z)
                        .mulBack(model);
                root.mulFront(world);
                for (var sample : samples) {
                    var collider = sample.deepCopy();
                    collider.transform(root);
                    AABB broadPhase = (AABB) hitboxMethod.invoke(collider);
                    for (Target target : cases) {
                        Vec3 relative = OpenMatrix4f.transform(model, new Vec3(target.side, target.height, -target.distance));
                        Vec3 center = player.add(-relative.x, relative.y, -relative.z);
                        AABB bounds = new AABB(center.x - .3, center.y, center.z - .3,
                                center.x + .3, center.y + target.size, center.z + .3);
                        boolean hit = broadPhase.intersects(bounds) && collider.isCollide(new OBBCollider(bounds));
                        if (hit != target.hit) {
                            throw new AssertionError(target.name + " yaw=" + yaw + " position=" + player + " hit=" + hit);
                        }
                        checked++;
                    }
                }
            }
        }
        Files.writeString(Path.of("build/epicfight-v6/reach_validation.json"),
                "{\"collision_checks\":" + checked + ",\"yaw_directions\":8,\"world_positions\":2,"
                        + "\"near_and_extended_targets_hit\":true,\"outside_targets_rejected\":true,\"ingame_tested\":false}\n");
        System.out.println("V6_REACH_OK: " + checked + " EF broad/narrow collision checks passed");
    }

    private record Target(String name, double side, double height, double distance, double size, boolean hit) { }
}
