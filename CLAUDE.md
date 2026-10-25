# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Betterlight is a Fabric mod for Minecraft `26.4-snapshot-5` (Java 25, Mojang mappings) that unifies lighting: smooth light on fluids, non-AO models and emitters, per-vertex light on block entities / item frames / paintings, interpolated entity light, front-only light for lit furnaces and jack o'lanterns, and directional face shading moved from the CPU into the shaders. The README is in Spanish; code comments are in English.

## Commands

Gradle crashes with the launcher's JDK (ARM64 daemon dies in `jvm.dll`), so always set `JAVA_HOME` first:

```
export JAVA_HOME=/c/Users/wifft/.jdks/jdk-25.0.4.1+1
./gradlew build          # jar in build/libs; also runs the gametests
./gradlew runClient      # dev client (worlds in run/saves)
./gradlew runGameTest    # headless server gametests (src/gametest, no client code)
./gradlew genSources     # decompiled Minecraft sources for checking signatures
pwsh -File tools/check-overwrites.ps1 [-Update]   # diff docs/vanilla copies against the current MC version
```

There is no linter. The only automated tests are the server gametests in `src/gametest` (`FrontEmitterLightTest`); everything client-side has to be checked in `runClient`. Mixin configs use `defaultRequire = 1`, so an injection that misses its target crashes the game at startup and the log names the mixin.

`.mcsrc/` (Minecraft) and `.mcsrc-fabric/` (Fabric renderer API + Indigo) are gitignored extracted sources, handy for grepping target classes.

## Architecture

Loom `splitEnvironmentSourceSets()`: `src/main` is common code (runs on the server too), `src/client` is client-only. Each has its own mixin config (`betterlight.mixins.json`, `betterlight.client.mixins.json`) — register new mixins there. Interfaces in `duck/` are added to vanilla classes by mixins (methods prefixed `betterlight$`).

**Config** — `BetterlightConfig` holds static volatile flags (read from section-compile and server threads), loaded once from `config/betterlight.properties`, edited via `BetterlightOptionsScreen` (button injected into Video Settings). The `betterlight.model` JVM property overrides `lightModel`. Every feature checks its flag.

**Directional shading in the shader** — The CPU per-face shade is removed (`BlockModelLighterMixin`, `IndigoAoCalculatorMixin`, `IndigoFlatLighterMixin`, `FluidRendererMixin`), and the face normal is written to the vertex instead (`VertexConsumerMixin`, `IndigoQuadViewMixin` honour `shadeDirectionOverride`; fluids get real normals). `RenderPipelinesMixin` swaps `DefaultVertexFormat.BLOCK` for `BetterlightFormats.BLOCK_WITH_NORMAL` and adds the Lighting bind group to terrain/block pipelines. `LightingMixin` grows the Lighting UBO by two vec4 (`CardinalA`/`CardinalB`: per-direction `CardinalLighting` values + light model id). The overridden shaders in `src/client/resources/assets/minecraft/shaders` read these via `include/light.glsl` (`minecraft_unified_light`). Light model ids in `BetterlightConfig.LightModel.shaderId` must match the `MINECRAFT_LIGHT_MODEL_*` defines; Java-side UBO layout and the GLSL `Lighting` block must stay in sync.

**Terrain meshing goes through Indigo, not vanilla** — Fabric API routes block meshing to Indigo's `AltModelBlockRendererImpl`, so block light/AO changes hook Indigo internals (`Indigo*Mixin`, `remap = false`). These break with Fabric API updates and don't apply under Sodium or other renderers. Water still uses vanilla `FluidRenderer`.

**LightField (block entities, item frames)** — `LightField` is a 4×4×4 grid of packed light around a block, camera-relative, sampled trilinearly per vertex (cells with zero light are skipped). Flow: cells are captured at extraction into the render state (`BlockEntityRenderDispatcherMixin` + `BlockEntityRenderStateMixin`, `ItemFrameRendererMixin` + `ItemFrameRenderStateMixin`), then set as the static `LightField.current()` while the renderer submits. Each `*SubmitMixin` copies `current()` into its submit (skipping outline/crumbling submits), and the matching `*FeatureRendererMixin` wraps the vertex buffer in `LightFieldVertexConsumer`, which replaces each vertex's light. Adding a new submit kind means adding both a Submit mixin and a FeatureRenderer mixin.

**Front emitters (furnaces, jack o'lanterns)** — A flood fill can't stop light behind a plane, so `BlockLightEngineMixin` (common code) makes them emit nothing in the real light engine; this applies on the server too and is what the gametests cover. On the client, `FrontLightOverlay` runs a per-emitter flood fill that never steps backwards and publishes an immutable merged map; `ClientLevelMixin` feeds it block/chunk events and installs it through `LevelLightEngineMixin`/`LightEngineMixin` (`BlockLightOverlayTarget`), which max-merges it into the block engine's `getLightValue`. `FrontEmitters` decides what counts as a front emitter.

**Paintings** — `PaintingRendererMixin` `@Overwrite`s `PaintingRenderer.renderPainting` for per-corner smooth light; the original vanilla method is kept in `docs/vanilla/` for diffing.

## Porting to a new Minecraft version

1. Bump `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version` in `gradle.properties` (valid versions: https://fabricmc.net/develop or the version branch of FabricMC/fabric).
2. `./gradlew genSources build`; fix signatures against the new sources.
3. `./gradlew runClient` and enter a world (missed injections crash on load).
4. `pwsh -File tools/check-overwrites.ps1`: if a `docs/vanilla` copy (the `@Overwrite`d method or the 5 shaders) changed, port Mojang's change into the mod's version, then refresh the copies with `-Update`.
5. `./gradlew runGameTest`.

Fragile points, most to least: `RenderPipelinesMixin` targets synthetic lambda names (`lambda$terrain$0`, `lambda$block$0`) — verify with `javap -p` on the Loom jar; Indigo mixins (tied to the Fabric API version); the shaders; the `PaintingRenderer.renderPainting` overwrite.
