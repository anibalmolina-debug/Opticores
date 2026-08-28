package com.example.opticore.culling;

import net.minecraft.util.math.Vec3d;

/** Cheap future-view heuristic; intentionally math-only. */
public final class PredictiveVisibility {
    private PredictiveVisibility() {}
    public static boolean likelyRelevant(double dx, double dz, Vec3d look, double cameraSpeed, double objectDistanceSq) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4) return true;
        double forward = (dx * look.x + dz * look.z) / horizontal;
        double predictionBoost = Math.min(0.35, cameraSpeed * 0.75);
        return forward > -0.15 - predictionBoost || objectDistanceSq < 64.0;
    }
}
