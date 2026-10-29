# Betterlight

Mod de Fabric para Minecraft 26.4-snapshot-5 que unifica la iluminación:
- Luz suave en fluidos, en modelos sin oclusión ambiental (raíles, plantas, carteles…) y en bloques que emiten luz (portal del Nether).
- Luz por vértice en bloques con entidad, marcos y cuadros, y luz interpolada en entidades.
- Hornos y calabazas de Halloween encendidos que solo iluminan por delante.
- Luces con sombras: antorchas, faroles, hogueras, piedra luminosa y otras fuentes de luz solo iluminan del todo lo que ven. Losas y escaleras tapan la luz según lo que cubren. Es solo visual: la luz del servidor sigue siendo la de vanilla.
- Oclusión ambiental en bloques que caen o empuja un pistón, y la que proyectan sobre el terreno.
- Sombreado por caras de los bloques hecho en el shader; las entidades y los objetos mantienen las dos luces de vanilla.

Las opciones están en Opciones → Vídeo, en el apartado Betterlight, y se guardan en `config/betterlight.properties`. Los cambios de cada versión están en [CHANGELOG.md](CHANGELOG.md).

## Qué bloques tienen luz con sombras

Lo deciden dos tags de bloque del mod:
- `betterlight:shadowed_emitters`: luces con sombras.
- `betterlight:front_emitters`: emisores que solo iluminan por delante. Necesitan la propiedad `facing`.

Un datapack puede ampliarlos con `"replace": false` o sustituirlos con `"replace": true`, en `data/betterlight/tags/block/<tag>.json`. `/reload` aplica el cambio sin salir del mundo. Los bloques apagados (horno sin encender, lámpara apagada…) nunca cuentan.

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
4. `pwsh -File tools/check-overwrites.ps1`: compara las copias de `docs/vanilla` (el método sustituido con `@Overwrite` y los 3 shaders) con la versión nueva. Si algo ha cambiado, aplica el cambio de Mojang a la versión del mod y actualiza las copias con `-Update`.
5. `./gradlew runGameTest`.

Puntos frágiles, de más a menos:
- `RenderPipelinesMixin` apunta a las lambdas por su nombre sintético (`lambda$terrain$0`, `lambda$block$0`). Compruébalo con `javap -p` sobre el jar de Loom.
- Los mixins de Indigo dependen de la versión de Fabric API.
- Los shaders.
- El `@Overwrite` de `PaintingRenderer.renderPainting`.
