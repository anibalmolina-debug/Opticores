# OptiCores

OptiCores is a client-side Minecraft performance mod for Fabric 1.21.1. The project focuses on reducing unnecessary rendering work and smoothing frame time without modifying Minecraft's gameplay simulation.

## Current architecture

```text
Minecraft client
   -> immutable frame snapshot
   -> bounded async visibility worker
   -> generation-checked result
   -> render-thread publication
   -> Sodium/Iris render pipeline
```

### Visibility
- Distance, predictive, temporal, frustum, and conservative occlusion stages.
- Occlusion tests are capped and cached.
- Worker threads never access live world, entity, or chunk objects.

### Chunk/render scheduling
- Adaptive CPU work budget based on hardware tier and current pressure.
- Chunk priority includes distance, camera direction, and movement direction.
- VBO upload and chunk meshing timings are measured rather than guessed.
- Required uploads are never dropped by OptiCores.

### Adaptive quality
- Frame-time feedback with hysteresis prevents rapid quality oscillation.
- Bottleneck classification distinguishes CPU/chunk pressure from likely GPU-bound frames without forcing GPU synchronization.
- Shadow-distance and LOD pressure can be reduced when frame time is persistently high.

### Compatibility
Optional integrations are isolated from the core where practical. Simulation-changing entity, AI, lighting, and particle tick optimizations are intentionally not enabled.

## Benchmarking

Use `/opticore benchmark` in a test world. Compare Vanilla, Sodium, and Sodium + OptiCores using the same camera path and scene. Record average FPS, 1% low FPS, 0.1% low FPS, frame time, tick time, meshing time, and upload time.

## Development

- Minecraft: 1.21.1
- Fabric Loader: 0.16.5+
- Java: 21+

Build locally with `./gradlew build`. The repository is designed for runtime verification against the exact Fabric/Yarn/Sodium/Iris versions used for release.
