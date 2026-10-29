# Changelog

## 0.2.2 — 2026-10-29

- **Nieve en las luces con sombras.** Las capas de nieve tapan la luz poco a poco según su altura: una capa ya no corta la mitad de la luz de una antorcha, y cada capa más acorta un poco su alcance. Las losas tapan de forma más gradual, y la superficie de la nieve o de una losa se ilumina según lo que ve desde arriba.
- Corregido: al romper un bloque que tapaba la cara delantera de un horno o una calabaza encendidos, la luz no volvía.
- **Carga más rápida.** Al entrar en un mundo o una zona con muchas luces, el cálculo de las sombras recibe más tiempo por frame y se va mostrando a medida que avanza, en lugar de verse la luz de vanilla hasta el final. Una cuadrícula de 150×150 antorchas está lista en unos 5 segundos.
- Corregido: al quitar una luz a más de 14 bloques de una antorcha, la luz de esa antorcha podía atravesar paredes.
- Corregido: si el servidor volvía a enviar un chunk ya cargado (plugins como WorldEdit), quedaba luz de antorchas que ya no existían.
- En un servidor con el mod, los hornos con luz delantera siguen al servidor y no a la opción del cliente; antes, con opciones distintas, los hornos podían quedarse sin luz.
- Corregido: con `/tick freeze`, la luz de los bloques con entidad, los marcos y los cuadros no se actualizaba.

## 0.2.1 — 2026-10-29

- Compatible con Minecraft 26.4-snapshot-6 (antes 26.4-snapshot-5), con Fabric API 0.165.1.
- Sin cambios de funcionamiento.

## 0.2.0 — 2026-10-29

### Novedades

- **Luces con sombras.** Antorchas, faroles, hogueras, velas, piedra luminosa, faroles de mar, champiluces, froglights, lámparas de redstone, bombillas de cobre, vides con bayas brillantes y otras fuentes de luz ahora solo iluminan a pleno lo que tienen a la vista. La luz que se cuela por una rendija o dobla una esquina se apaga enseguida, en lugar de repartirse por toda la zona como en vanilla.
  - Las losas, escaleras y demás bloques cuya forma vanilla tiene en cuenta para la luz también la tapan, en proporción a lo que cubren. Una media losa en un hueco deja pasar más o menos la mitad de la luz.
  - Los bordes de las sombras tienen una penumbra suave.
  - Es un efecto solo visual y del lado del cliente. La luz que usa el servidor (aparición de mobs, crecimiento de cultivos...) sigue siendo la de vanilla, y el efecto también funciona en servidores sin el mod.
- **Hornos y calabazas con sombras.** La luz de la cara delantera de hornos, ahumadores, altos hornos y jack o'lanterns también respeta la línea de visión.
- **Bloques configurables con tags.** Los tags `betterlight:shadowed_emitters` y `betterlight:front_emitters` deciden qué bloques reciben estas luces. Un datapack puede ampliarlos (`"replace": false`) o sustituirlos (`"replace": true`), y `/reload` aplica el cambio sin salir del mundo. En servidores sin el mod se usa la lista incluida en el propio mod.
- **Oclusión ambiental en bloques en movimiento.** Los bloques que caen (arena, grava, yunques...) y los que mueve un pistón conservan la oclusión ambiental de sus vecinos reales y además la proyectan sobre el terreno que los rodea. Las bases de pistón extendidas oscurecen igual que un bloque completo.

### Cambios

- Las opciones «Luces con sombras» y «Hornos con luz delantera» se aplican al cerrar Vídeo, sin tener que volver a entrar en el mundo. En los hornos, la luz ya guardada en el mundo no se corrige hasta que esos chunks se vuelvan a iluminar.
- La luz delantera de los hornos pasa a usar el mismo sistema que las luces con sombras, y ya no recalcula todo el mapa de luz con cada cambio.

### Rendimiento

- El cálculo se reparte entre frames con un presupuesto de tiempo y se descarta donde otra luz ya domina. Una cuadrícula de 150×150 antorchas se mantiene a 60 fps.
- Menos uso de memoria y menos objetos temporales en el cálculo de la luz y en la oclusión ambiental de los bloques en movimiento.
- Nuevas secciones `betterlight_*` en el profiler (`/debug start`, gráfico de F3).

## 0.1.1 — 2026-10-25

- La luz de los bloques con entidad, los marcos y los cuadros se captura una sola vez por tick.
- Las actualizaciones de la luz delantera de los hornos se agrupan y se limitan a lo que ha cambiado.
- Corregido el desplazamiento lateral de la luz suave en el iluminador de bloques de vanilla.
- El modelo de luz pasa a ser fijo, y las opciones se mueven a Opciones → Vídeo.

## 0.1.0 — 2026-10-25

Primera versión:

- Luz suave en fluidos, modelos sin oclusión ambiental y emisores de luz.
- Luz por vértice en bloques con entidad, marcos y cuadros.
- Luz interpolada en las entidades.
- Hornos y calabazas que solo iluminan por delante.
- Sombreado direccional por caras en el shader.
