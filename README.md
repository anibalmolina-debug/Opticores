# OptiCores

OptiCores is a client-side Minecraft performance mod focused on visibility culling, render-work scheduling, chunk-load stability, and adaptive quality.

## Architecture

- Immutable client-thread snapshots feed asynchronous culling workers.
- Worker results are generation-tagged so stale results are discarded.
- Occlusion tests are bounded and cached.
- Temporal and predictive visibility reduce repeated expensive tests.
- Chunk/VBO work is measured and throttled conservatively without dropping required uploads.
- Dynamic quality uses frame-time and chunk-pressure telemetry with hysteresis.
- Simulation-changing entity/AI/lighting/particle tick mixins are not enabled.

## Development

The project targets Minecraft 1.21.1, Fabric Loader 0.16.5+, and Java 21.

Build with:

```text
./gradlew build
```
