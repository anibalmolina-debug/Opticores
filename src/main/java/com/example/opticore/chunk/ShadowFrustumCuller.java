package com.example.opticore.chunk;

import com.example.opticore.compat.OpticoreCompat;
import net.minecraft.util.math.Box;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Conservative shadow-pass frustum culling with reusable scratch state. */
public final class ShadowFrustumCuller {
    private static final FrustumIntersection SHADOW_INTERSECTION = new FrustumIntersection();
    private static final Matrix4f TEMP_MATRIX = new Matrix4f();
    private static final Matrix4f INV_LIGHT_VIEW = new Matrix4f();
    private static final Vector3f LIGHT_DIR = new Vector3f(0, -1, 0);
    private static final ThreadLocal<Box> TEMP_BOX = ThreadLocal.withInitial(() -> new Box(0, 0, 0, 0, 0, 0));
    private static volatile boolean shadowFrustumValid;
    private static float shadowDistanceCap = 1.0f;

    private ShadowFrustumCuller() {}

    public static void updateShadowFrustum(Matrix4f lightView, Matrix4f lightProjection) {
        TEMP_MATRIX.set(lightProjection).mul(lightView);
        SHADOW_INTERSECTION.set(TEMP_MATRIX);
        INV_LIGHT_VIEW.set(lightView).invert();
        LIGHT_DIR.set(0, 0, -1).mulDirection(INV_LIGHT_VIEW).normalize();
        shadowFrustumValid = true;
    }

    public static boolean isVisibleInShadow(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (!shadowFrustumValid) return true;
        if (!SHADOW_INTERSECTION.testAab(minX, minY, minZ, maxX, maxY, maxZ)) return false;
        if (OpticoreCompat.currentFrustum != null) {
            Box box = TEMP_BOX.get().set(minX, minY, minZ, maxX, maxY, maxZ);
            if (!OpticoreCompat.currentFrustum.isVisible(box)) {
                float ext = 64.0f;
                float ex = LIGHT_DIR.x * ext, ey = LIGHT_DIR.y * ext, ez = LIGHT_DIR.z * ext;
                float minEX = Math.min(minX, minX + ex), minEY = Math.min(minY, minY + ey), minEZ = Math.min(minZ, minZ + ez);
                float maxEX = Math.max(maxX, maxX + ex), maxEY = Math.max(maxY, maxY + ey), maxEZ = Math.max(maxZ, maxZ + ez);
                box.set(minEX, minEY, minEZ, maxEX, maxEY, maxEZ);
                if (!OpticoreCompat.currentFrustum.isVisible(box)) return false;
            }
        }
        return true;
    }

    public static boolean isSectionVisibleInShadow(int sectionX, int sectionY, int sectionZ) {
        float minX = sectionX << 4, minY = sectionY << 4, minZ = sectionZ << 4;
        return isVisibleInShadow(minX, minY, minZ, minX + 16.0f, minY + 16.0f, minZ + 16.0f);
    }

    public static boolean shouldRenderInShadowPass(int sectionX, int sectionY, int sectionZ, double cameraX, double cameraZ, double baseRenderDistSq) {
        double effectiveDistSq = baseRenderDistSq * shadowDistanceCap * shadowDistanceCap;
        double dx = (sectionX << 4) + 8.0 - cameraX, dz = (sectionZ << 4) + 8.0 - cameraZ;
        return dx * dx + dz * dz <= effectiveDistSq && isSectionVisibleInShadow(sectionX, sectionY, sectionZ);
    }

    public static void invalidate() { shadowFrustumValid = false; }
    public static void setShadowDistanceCap(float multiplier) { shadowDistanceCap = Math.max(0.25f, Math.min(1.0f, multiplier)); }
    public static float getShadowDistanceCap() { return shadowDistanceCap; }
    public static boolean isShadowFrustumValid() { return shadowFrustumValid; }
}
