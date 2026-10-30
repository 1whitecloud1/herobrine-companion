// Frozen before C refactoring on 2026-10-22; preserves original float math and winding.
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.ArrayList;
public class LegacyParticleGeometry {
    public static void draw(String kind, Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                            int sides, float base, float tip, float progress, float r, float g, float b, float a) {
        switch (kind) {
            case "Beam" -> new Beam().drawCylinder(matrix, vertex, camera, path, sides, base, new Beam.ParticleColor(r,g,b,a));
            case "Arc" -> new Arc().drawVolumetricCylinder(matrix, vertex, camera, path, sides, base, new Arc.ParticleColor(r,g,b,a));
            case "Pillar" -> new Pillar().drawVolumetricCylinder(matrix, vertex, camera, path, sides, base, tip, new Pillar.ParticleColor(r,g,b,a), progress);
            default -> throw new IllegalArgumentException(kind);
        }
    }
    private static class Beam {
        private static class ParticleColor {
            private final float r;
            private final float g;
            private final float b;
            private final float a;

            private ParticleColor(float r, float g, float b, float a) {
                this.r = r;
                this.g = g;
                this.b = b;
                this.a = a;
            }
        }

        private void drawCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> points, int cylinderSegments, float radius, ParticleColor color) {
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 p1 = points.get(i).subtract(cameraPos);
                Vec3 p2 = points.get(i + 1).subtract(cameraPos);
                Vec3 axis = p2.subtract(p1);
                if (axis.lengthSqr() < 0.001D) {
                    continue;
                }
                List<Vec3> ringA = generateRing(p1, axis, cylinderSegments, radius);
                List<Vec3> ringB = generateRing(p2, axis, cylinderSegments, radius);
                for (int j = 0; j < cylinderSegments; j++) {
                    Vec3 a1 = ringA.get(j);
                    Vec3 a2 = ringA.get((j + 1) % cylinderSegments);
                    Vec3 b2 = ringB.get((j + 1) % cylinderSegments);
                    Vec3 b1 = ringB.get(j);
                    addVertex(vertex, transform, a1, color);
                    addVertex(vertex, transform, a2, color);
                    addVertex(vertex, transform, b2, color);
                    addVertex(vertex, transform, b1, color);
                }
            }
        }

        private List<Vec3> generateRing(Vec3 center, Vec3 axis, int segments, float radius) {
            List<Vec3> ring = new ArrayList<>();
            Vector3f axis3f = toVector3f(axis).normalize();
            Vector3f u = Math.abs(axis3f.y) < 0.999F
                    ? axis3f.cross(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f()).normalize()
                    : axis3f.cross(new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f()).normalize();
            Vector3f v = axis3f.cross(u, new Vector3f()).normalize();
            for (int i = 0; i < segments; i++) {
                float angle = (float) i / segments * (float) Math.PI * 2.0F;
                Vector3f pos = toVector3f(center);
                pos.add(u.mul((float) Math.cos(angle) * radius, new Vector3f()));
                pos.add(v.mul((float) Math.sin(angle) * radius, new Vector3f()));
                ring.add(toVec3(pos));
            }
            return ring;
        }

        private void addVertex(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
            vertex.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(color.r, color.g, color.b, color.a).endVertex();
        }

        private static Vector3f toVector3f(Vec3 vec) {
            return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
        }

        private static Vec3 toVec3(Vector3f vec) {
            return new Vec3(vec.x, vec.y, vec.z);
        }
    }
    private static class Arc {
        protected static class ParticleColor {
            public final float r, g, b, a;
            public ParticleColor(float r, float g, float b, float a) {
                this.r = r; this.g = g; this.b = b; this.a = a;
            }
        }

        private void drawVolumetricCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> path, int cylinderSegments, float radius, ParticleColor col) {
            int totalSegments = path.size() - 1;
            if (totalSegments <= 0) return;

            for (int i = 0; i < totalSegments; i++) {
                Vec3 p1Abs = path.get(i);
                Vec3 p2Abs = path.get(i + 1);

                Vec3 p1 = p1Abs.subtract(cameraPos);
                Vec3 p2 = p2Abs.subtract(cameraPos);

                Vec3 dir = p2.subtract(p1);
                if (dir.lengthSqr() < 0.001) continue;

                List<Vec3> prevRing = generateRing(p1, dir, cylinderSegments, radius);
                List<Vec3> nextRing = generateRing(p2, dir, cylinderSegments, radius);

                for (int j = 0; j < cylinderSegments; j++) {
                    Vec3 p1a = prevRing.get(j);
                    Vec3 p1b = prevRing.get((j + 1) % cylinderSegments);
                    Vec3 p2a = nextRing.get(j);
                    Vec3 p2b = nextRing.get((j + 1) % cylinderSegments);

                    addVertex(vertex, transform, p1a, col);
                    addVertex(vertex, transform, p1b, col);
                    addVertex(vertex, transform, p2b, col);
                    addVertex(vertex, transform, p2a, col);
                }
            }
        }

        private List<Vec3> generateRing(Vec3 center, Vec3 axis, int segments, float radius) {
            List<Vec3> ring = new ArrayList<>();
            Vector3f axis3f = toVector3f(axis).normalize();
            Vector3f u;
            if (Math.abs(axis3f.y) < 0.999f) {
                u = axis3f.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f()).normalize();
            } else {
                u = axis3f.cross(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f()).normalize();
            }
            Vector3f v = axis3f.cross(u, new Vector3f()).normalize();

            for (int i = 0; i < segments; i++) {
                float angle = (float)i / segments * (float)Math.PI * 2;
                float cos = (float)Math.cos(angle);
                float sin = (float)Math.sin(angle);

                Vector3f vertexPos = toVector3f(center);
                vertexPos.add(u.mul(cos * radius, new Vector3f()));
                vertexPos.add(v.mul(sin * radius, new Vector3f()));
                ring.add(toVec3(vertexPos));
            }
            return ring;
        }

        private void addVertex(VertexConsumer vertex, Matrix4f mat, Vec3 pos, ParticleColor col) {
            vertex.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z).color(col.r, col.g, col.b, col.a).endVertex();
        }

        private static Vector3f toVector3f(Vec3 vec) {
            return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
        }

        private static Vec3 toVec3(Vector3f vec) {
            return new Vec3(vec.x, vec.y, vec.z);
        }
    }
    private static class Pillar {
        protected static class ParticleColor {
            public final float r, g, b, a;
            public ParticleColor(float r, float g, float b, float a) {
                this.r = r; this.g = g; this.b = b; this.a = a;
            }
        }

        private void drawVolumetricCylinder(Matrix4f transform, VertexConsumer vertex, Vec3 cameraPos, List<Vec3> path, int cylinderSegments, float baseRadius, float tipRadius, ParticleColor col, float fallProgress) {
            int totalSegments = path.size() - 1;
            if (totalSegments <= 0) return;

            int maxSegmentsToDraw = (int)Math.max(1, totalSegments * fallProgress);
            maxSegmentsToDraw = Math.min(maxSegmentsToDraw, totalSegments);

            for (int i = 0; i < maxSegmentsToDraw; i++) {
                Vec3 p1Abs = path.get(i);
                Vec3 p2Abs = path.get(i + 1);

                Vec3 p1 = p1Abs.subtract(cameraPos);
                Vec3 p2 = p2Abs.subtract(cameraPos);

                Vec3 dir = p2.subtract(p1);
                if (dir.lengthSqr() < 0.001) continue;

                float r1 = baseRadius + (tipRadius - baseRadius) * ((float) i / totalSegments);
                float r2 = baseRadius + (tipRadius - baseRadius) * ((float) (i + 1) / totalSegments);

                List<Vec3> prevRing = generateRing(p1, dir, cylinderSegments, r1);
                List<Vec3> nextRing = generateRing(p2, dir, cylinderSegments, r2);

                for (int j = 0; j < cylinderSegments; j++) {
                    Vec3 p1a = prevRing.get(j);
                    Vec3 p1b = prevRing.get((j + 1) % cylinderSegments);
                    Vec3 p2a = nextRing.get(j);
                    Vec3 p2b = nextRing.get((j + 1) % cylinderSegments);

                    addVertex(vertex, transform, p1a, col);
                    addVertex(vertex, transform, p1b, col);
                    addVertex(vertex, transform, p2b, col);
                    addVertex(vertex, transform, p2a, col);
                }
            }
        }

        private List<Vec3> generateRing(Vec3 center, Vec3 axis, int segments, float radius) {
            List<Vec3> ring = new ArrayList<>();

            if (radius <= 0.001f) {
                for (int i=0; i<segments; i++) ring.add(center);
                return ring;
            }

            Vector3f axis3f = toVector3f(axis).normalize();
            Vector3f u;
            if (Math.abs(axis3f.y) < 0.999f) {
                u = axis3f.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f()).normalize();
            } else {
                u = axis3f.cross(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f()).normalize();
            }
            Vector3f v = axis3f.cross(u, new Vector3f()).normalize();

            for (int i = 0; i < segments; i++) {
                float angle = (float)i / segments * (float)Math.PI * 2;
                float cos = (float)Math.cos(angle);
                float sin = (float)Math.sin(angle);

                Vector3f vertexPos = toVector3f(center);
                vertexPos.add(u.mul(cos * radius, new Vector3f()));
                vertexPos.add(v.mul(sin * radius, new Vector3f()));

                ring.add(toVec3(vertexPos));
            }
            return ring;
        }

        private void addVertex(VertexConsumer vertex, Matrix4f mat, Vec3 pos, ParticleColor col) {
            vertex.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z).color(col.r, col.g, col.b, col.a).endVertex();
        }

        private static Vector3f toVector3f(Vec3 vec) {
            return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
        }

        private static Vec3 toVec3(Vector3f vec) {
            return new Vec3(vec.x, vec.y, vec.z);
        }
    }
}
