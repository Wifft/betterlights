# Betterlight

Mod de Fabric para Minecraft 26.4-snapshot-5 que unifica la iluminación:
- Luz suave en fluidos, en modelos sin oclusión ambiental (raíles, plantas, carteles…) y en bloques que emiten luz (portal del Nether).
- Luz por vértice en bloques con entidad, marcos y cuadros, y luz interpolada en entidades.
- Hornos y calabazas de Halloween encendidos que solo iluminan por delante.
- Sombreado por caras hecho en el shader, con tres modelos de luz: mixto, por caras y dos luces.

Las opciones están en Opciones → Vídeo → Betterlight… y se guardan en `config/betterlight.properties`.

## Compilar y probar

Gradle no funciona con el JDK del launcher (25+36 en ARM64: el daemon se cae en `jvm.dll`). Usa el JDK 25 actual:

```
export JAVA_HOME=/c/Users/wifft/.jdks/jdk-25.0.4.1+1
./gradlew build          # jar en build/libs; también ejecuta los gametests
./gradlew runClient      # cliente de desarrollo (mundos en run/saves)
./gradlew runGameTest    # servidor de pruebas sin código de cliente: src/gametest
./gradlew genSources     # código de Minecraft para consultar firmas
```

## Dependencias de las que cuelga el mod

- **Indigo (Fabric API)**: Fabric API desvía el mallado de bloques del terreno a Indigo (`AltModelBlockRendererImpl`), así que la luz y el sombreado de los bloques se enganchan a clases **internas** de Indigo (`Indigo*Mixin`). Con Sodium u otro renderer que sustituya a Indigo, esa parte no se aplica.
- El agua sigue pasando por el `FluidRenderer` de vanilla.

## Portar a una versión nueva

1. Cambia `minecraft_version`, `loader_version`, `loom_version` y `fabric_api_version` en `gradle.properties`. Las versiones válidas están en https://fabricmc.net/develop o en la rama de la versión en https://github.com/FabricMC/fabric (`gradle.properties`).
2. `./gradlew genSources build`. Si algo no compila, corrige la firma comparándola con el código nuevo.
3. `./gradlew runClient` y entra en un mundo. Con `defaultRequire = 1`, un mixin que no encuentra su sitio cierra el juego, y el log dice cuál es.
4. `pwsh -File tools/check-overwrites.ps1`: compara las copias de `docs/vanilla` (el método sustituido con `@Overwrite` y los 5 shaders) con la versión nueva. Si algo ha cambiado, aplica el cambio de Mojang a la versión del mod y actualiza las copias con `-Update`.
5. `./gradlew runGameTest`.

Puntos frágiles, de más a menos:
- `RenderPipelinesMixin` apunta a las lambdas por su nombre sintético (`lambda$terrain$0`, `lambda$block$0`). Compruébalo con `javap -p` sobre el jar de Loom.
- Los mixins de Indigo dependen de la versión de Fabric API.
- Los shaders.
- El `@Overwrite` de `PaintingRenderer.renderPainting`.
