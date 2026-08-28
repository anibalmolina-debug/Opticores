package com.example.opticore.compat;

import com.example.opticore.chunk.DynamicQualityScaler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.Frustum;

import java.lang.reflect.Method;

public final class OpticoreCompat {
    public static volatile Frustum currentFrustum;
    public static volatile Frustum currentShadowFrustum;
    private static final boolean IS_IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    public static final boolean IS_DH_LOADED = FabricLoader.getInstance().isModLoaded("distanthorizons");
    public static final boolean IS_CHUNKY_LOADED = FabricLoader.getInstance().isModLoaded("chunky");

    private OpticoreCompat() {}
    public static boolean isStandbyMode() { return IS_CHUNKY_LOADED && ChunkyCompatHelper.isRunning(); }
    public static boolean isRenderingShadowPass() { return IS_IRIS_LOADED && IrisCompatHelper.isShadowPass(); }
    public static boolean isShaderPackActive() { return IS_IRIS_LOADED && IrisCompatHelper.isShaderPackActive(); }

    private static final class IrisCompatHelper {
        private static volatile boolean resolved;
        private static Object instance;
        private static Method shadowMethod;
        private static Method shaderMethod;
        private IrisCompatHelper() {}
        private static synchronized void init() {
            if (resolved) return;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                instance = api.getMethod("getInstance").invoke(null);
                shadowMethod = api.getMethod("isRenderingShadowPass");
                shaderMethod = api.getMethod("isShaderPackInUse");
            } catch (Throwable t) {
                instance = null; shadowMethod = null; shaderMethod = null;
            } finally { resolved = true; }
        }
        static boolean isShadowPass() {
            if (!resolved) init();
            try { return shadowMethod != null && instance != null && (Boolean) shadowMethod.invoke(instance); }
            catch (Throwable ignored) { return false; }
        }
        static boolean isShaderPackActive() {
            if (!resolved) init();
            try { return shaderMethod != null && instance != null && (Boolean) shaderMethod.invoke(instance); }
            catch (Throwable ignored) { return false; }
        }
    }

    private static final class ChunkyCompatHelper {
        private static volatile boolean resolved;
        private static Method getApiMethod;
        private static Method isRunningMethod;
        private ChunkyCompatHelper() {}
        private static synchronized void init() {
            if (resolved) return;
            try {
                Class<?> api = Class.forName("org.popcraft.chunky.api.ChunkyAPI");
                getApiMethod = api.getMethod("get");
                isRunningMethod = api.getMethod("isRunning");
            } catch (Throwable t) { getApiMethod = null; isRunningMethod = null; }
            finally { resolved = true; }
        }
        static boolean isRunning() {
            if (!resolved) init();
            try {
                Object api = getApiMethod == null ? null : getApiMethod.invoke(null);
                return api != null && isRunningMethod != null && (Boolean) isRunningMethod.invoke(api);
            } catch (Throwable ignored) { return false; }
        }
    }
}
