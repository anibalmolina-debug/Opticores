package com.example.opticore.mixin;

import com.example.opticore.benchmark.BenchmarkSuite;
import com.example.opticore.profiler.MicroProfiler;
import com.example.opticore.util.CrashHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method="render",at=@At("HEAD")) private void opticore$start(boolean tick,CallbackInfo ci){MicroProfiler.startFrame(System.nanoTime());}
    @Inject(method="render",at=@At("RETURN")) private void opticore$end(boolean tick,CallbackInfo ci){long d=MicroProfiler.endFrame(System.nanoTime());if(d>0)BenchmarkSuite.onFrame(d);}
    @Inject(method="printCrashReport",at=@At("HEAD")) private void opticore$crash(CrashReport report,CallbackInfo ci){CrashHandler.handleCrash(report);}
}
