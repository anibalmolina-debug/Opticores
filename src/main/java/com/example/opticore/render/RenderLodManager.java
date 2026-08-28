package com.example.opticore.render;

/** Render-quality policy that never changes simulation state. */
public final class RenderLodManager {
    public enum Tier { FULL, REDUCED, MINIMAL, CULLED }
    private RenderLodManager() {}
    public static Tier entityTier(double distanceSq, boolean stutterRisk) {
        if (stutterRisk) {
            if (distanceSq > 128.0 * 128.0) return Tier.CULLED;
            if (distanceSq > 64.0 * 64.0) return Tier.MINIMAL;
        }
        if (distanceSq > 192.0 * 192.0) return Tier.CULLED;
        if (distanceSq > 96.0 * 96.0) return Tier.MINIMAL;
        if (distanceSq > 48.0 * 48.0) return Tier.REDUCED;
        return Tier.FULL;
    }
    public static boolean shouldUpdateVisuals(double distanceSq, int frame, boolean stutterRisk) {
        return switch (entityTier(distanceSq, stutterRisk)) {
            case FULL -> true; case REDUCED -> (frame & 1) == 0;
            case MINIMAL -> (frame & 3) == 0; case CULLED -> false;
        };
    }
}
