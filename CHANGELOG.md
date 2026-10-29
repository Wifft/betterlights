# Changelog

## 0.2.0 — 2026-10-29

### Novedades
- **Luces con sombras.** Las antorchas, faroles, hogueras, velas, piedra luminosa, faroles de mar, champiluces, froglights, lámparas de redstone, bombillas de cobre, vides con bayas brillantes y otras fuentes de luz solo iluminan del todo lo que ven. La luz que se escapa por una rendija o dobla una esquina se apaga rápido, en vez de iluminarlo todo como en vanilla.
  - Las losas, escaleras y demás bloques que vanilla trata por forma también tapan la luz, en proporción a lo que cubren: media losa en un hueco deja pasar más o menos media luz.
  - Los bordes de las sombras salen con penumbra suave.
  - Es solo visual, en el cliente. La luz del servidor (aparición de mobs, cultivos...) sigue siendo la de vanilla, y funciona también en servidores sin el mod.
- **Hornos y calabazas con sombras.** La luz delantera de hornos, ahumadores, altos hornos y jack o'lanterns también respeta la línea de visión.
- **Lista de bloques por tags.** Qué bloques reciben estas luces lo deciden los tags `betterlight:shadowed_emitters` y `betterlight:front_emitters`. Un datapack puede ampliarlos (`"replace": false`) o sustituirlos (`"replace": true`), y `/reload` aplica el cambio sin salir del mundo. En servidores sin el mod se usa la lista que trae el propio mod.
- **AO en bloques en movimiento.** Los bloques que caen (arena, grava, yunques...) y los que empuja un pistón mantienen la oclusión ambiental de sus vecinos reales, y proyectan AO sobre el terreno de alrededor. Las bases de pistón extendidas oscurecen como un bloque completo.

### Cambios
- Las opciones "Luces con sombras" y "Hornos con luz delantera" se aplican al cerrar Vídeo, sin volver a entrar en el mundo. En los hornos, la luz ya guardada en el mundo se corrige cuando esos chunks se vuelven a iluminar.
- La luz delantera de los hornos comparte el mismo sistema que las luces con sombras. Ya no reconstruye todo el mapa de luz con cada cambio.

### Rendimiento
- El cálculo se reparte entre frames con un presupuesto de tiempo y se poda donde otra luz ya domina. Un grid de 150×150 antorchas se mantiene a 60 fps.
- Menos memoria y menos reservas temporales en el cálculo de luz y en el AO de bloques en movimiento.
- Secciones `betterlight_*` en el profiler (`/debug start`, gráfico de F3).

## 0.1.1 — 2026-10-25

- La luz de bloques con entidad, marcos y cuadros se captura una vez por tick.
- Las actualizaciones de la luz delantera de los hornos se agrupan y se limitan a lo que cambia.
- Corregido el desplazamiento lateral de la luz suave en el iluminador de bloques de vanilla.
- Modelo de luz fijo, y opciones trasladadas a Opciones → Vídeo.

## 0.1.0 — 2026-10-25

- Primera versión: luz suave en fluidos, modelos sin oclusión y emisores; luz por vértice en bloques con entidad, marcos y cuadros; luz interpolada en entidades; hornos y calabazas que solo iluminan por delante; sombreado por caras en el shader.
