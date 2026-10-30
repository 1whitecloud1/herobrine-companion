package com.whitecloud233.herobrine_companion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.client.model.PlayerModelParts;
import com.whitecloud233.herobrine_companion.regression.SourceFixturesChecks;
import com.whitecloud233.herobrine_companion.util.Ease;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Compares actual emitted vertices against frozen implementations, with no GPU or game window. */
public final class RenderReuseChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        geometryContracts();
        modelContracts();
        check("cubic first quarter", Ease.window(.25F, 0, 1) == .15625F);
        check("cubic last quarter", Ease.window(.75F, 0, 1) == .84375F);
        check("window lower clamp", Ease.window(-1F, 0, 1) == 0);
        check("window upper clamp", Ease.window(2F, 0, 1) == 1);
        check("collapsed window at end", Ease.window(.5F, .5F, .5F) == 1);
        check("collapsed window before end", Ease.window(.4F, .5F, .5F) == 0);
        check("reversed window retains step rule", Ease.window(.5F, .8F, .3F) == 1);
        System.out.println("RenderReuseChecks: " + checks + " checks passed");
    }

    private static void geometryContracts() throws Exception {
        try (var fixture = SourceFixturesChecks.compile("LegacyParticleGeometry", SourceFixturesChecks.resource("LegacyParticleGeometry.java"))) {
            var legacy = fixture.type().getMethod("draw", String.class, Matrix4f.class, VertexConsumer.class, Vec3.class,
                    List.class, int.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class);
            List<List<Vec3>> paths = List.of(List.of(), List.of(Vec3.ZERO),
                    List.of(Vec3.ZERO, new Vec3(0,10,0), new Vec3(0,20,0)),
                    List.of(new Vec3(1,2,3), new Vec3(11,2,3), new Vec3(15,7,9)),
                    List.of(Vec3.ZERO, Vec3.ZERO, new Vec3(.001,0,0), new Vec3(2,5,-3)),
                    List.of(new Vec3(30_000_000.125,4,-30_000_000.875), new Vec3(30_000_005.625,15,-30_000_002.125)));
            Matrix4f matrix = new Matrix4f().translation(.1F,-.3F,.5F).rotateY(.7F).rotateZ(.2F);
            ParticleColor color = new ParticleColor(.3F,.7F,.9F,1.2F);
            for (String kind : new String[]{"Beam", "Arc", "Pillar"}) {
                for (List<Vec3> path : paths) {
                    Vec3 camera = path.isEmpty() ? Vec3.ZERO : path.get(0).add(.25,.5,.75);
                    for (float progress : new float[]{0F,.5F,1F}) {
                        for (float radius : new float[]{0F,.0005F,1.7F}) {
                            List<Vertex> before = new ArrayList<>(), after = new ArrayList<>();
                            legacy.invoke(null, kind, matrix, capture(before), camera, path, 8, radius, 0F, progress,
                                    color.r(),color.g(),color.b(),color.a());
                            if (kind.equals("Pillar")) ParticleGeometry.taperedCylinderIntColor(matrix,capture(after),camera,path,8,radius,0,color,progress);
                            else if (kind.equals("Arc")) ParticleGeometry.cylinderUntransformed(matrix,capture(after),camera,path,8,radius,color);
                            else ParticleGeometry.cylinder(matrix,capture(after),camera,path,8,radius,color);
                            check("bitwise vertex/color/winding equality " + kind + " size=" + path.size() + " progress=" + progress + " radius=" + radius,
                                    before.equals(after));
                        }
                    }
                }
            }
        }
    }

    private static VertexConsumer capture(List<Vertex> output) {
        long[] point = new long[3];
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            if (method.getName().equals("addVertex")) {
                for (int i=0;i<3;i++) point[i]=Float.floatToIntBits((Float)args[i]);
                return proxy;
            }
            if (method.getName().equals("setColor")) {
                output.add(new Vertex(point[0],point[1],point[2],(Integer)args[0],(Integer)args[1],(Integer)args[2],(Integer)args[3]));
                return proxy;
            }
            throw new AssertionError("Unexpected vertex call " + method);
        });
    }
    private record Vertex(long x,long y,long z,int r,int g,int b,int a) { }

    private static void modelContracts() {
        ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot();
        PlayerModel<LivingEntity> model = new PlayerModel<>(root,false);
        ModelPart[] base = {model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg};
        ModelPart[] overlays = {model.hat,model.jacket,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants};
        for (int i=0;i<base.length;i++) {
            base[i].setPos(i+.25F,i+.5F,i+.75F);
            base[i].xRot=i*.1F; base[i].yRot=i*.2F; base[i].zRot=i*.3F;
            base[i].xScale=1+i*.01F; base[i].yScale=1+i*.02F; base[i].zScale=1+i*.03F;
            base[i].visible=false;
        }
        PlayerModelParts.syncOverlays(model);
        for (int i=0;i<base.length;i++) {
            ModelPart from=base[i],to=overlays[i];
            check("overlay position " + i, from.x==to.x && from.y==to.y && from.z==to.z);
            check("overlay rotation " + i, from.xRot==to.xRot && from.yRot==to.yRot && from.zRot==to.zRot);
            check("overlay scale " + i, from.xScale==to.xScale && from.yScale==to.yScale && from.zScale==to.zScale);
            check("copyFrom retains overlay visibility " + i, to.visible);
        }
        float armX=model.leftArm.x,armRot=model.leftArm.xRot;
        PlayerModelParts.anchorY(model,-1.25F);
        check("torso/head/clothing height", model.body.y==-1.25F && model.head.y==-1.25F && model.hat.y==-1.25F && model.jacket.y==-1.25F);
        check("arm/sleeve anchor", model.leftArm.y==.75F && model.rightArm.y==.75F && model.leftSleeve.y==.75F && model.rightSleeve.y==.75F);
        check("leg/pants anchor", model.leftLeg.y==10.75F && model.rightLeg.y==10.75F && model.leftPants.y==10.75F && model.rightPants.y==10.75F);
        check("height sync leaves x and rotation intact", model.leftArm.x==armX && model.leftArm.xRot==armRot);
    }
    private static void check(String label, boolean condition) { if (!condition) throw new AssertionError(label); checks++; }
}
