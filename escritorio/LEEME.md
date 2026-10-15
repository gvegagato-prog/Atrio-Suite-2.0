# Atrio 1.0

Launcher minimalista para juegos en Linux: Steam, Heroic (Epic, GOG, Amazon), Lutris,
emuladores y juegos propios, con arte de SteamGridDB.

- Probar sin instalar: `./probar.sh`
- Crear el AppImage: `./construir.sh` (queda en `dist/`)

Requiere `nodejs` y `npm` (`sudo pacman -S nodejs npm`).

Configuración: `~/.config/atrio/config.json`. Arte descargado: `~/.local/share/atrio/art`.
Horas de juego: `~/.local/share/atrio/playlog.json`.

## Windows

El instalador se construye solo en GitHub (acción «Atrio (Windows)»):
descarga «Atrio-instalador-Windows» de la última ejecución y ábrelo.
En Windows lee Steam, Heroic, emuladores y juegos propios (Lutris no existe allí).
Datos: `%LOCALAPPDATA%\Atrio-datos`.

Con tu cuenta de Atrio iniciada, las horas de todos tus equipos se juntan en el calendario.

## Atrio en el móvil

Con tu cuenta de Atrio iniciada, el móvil ve en directo lo que juegas, pide capturas a resolución real,
lanza juegos, apunta ideas y valora cada partida al cerrarla. Qué puede hacer el móvil se decide en
Ajustes → Móvil. Capturas en `~/Imágenes/Atrio` (o `~/Pictures/Atrio`). Más en el README de la raíz.

## La revista (interfaz de escritorio)

Atrio arranca con la revista (`src/renderer/revista/`): la estética de papel y tinta de la app del móvil, para
ratón y teclado. Portada, secciones de cada categoría, fichas en casillas, Diario, Cifras, Tu PC y Ajustes
(tema, acento, cuenta y clave de SteamGridDB). El lanzador clásico (`src/renderer/index.html`, pantalla grande y
mando) se abre desde Ajustes de la revista; para volver, Ajustes del lanzador → «Volver a la revista».

- La elección se guarda en `config.json` (`ui`: `revista` o `lanzador`); el tema y el acento, en `revista`.
- Forzar una al abrir: `--revista` o `--lanzador` (o la variable `ATRIO_UI`). Las capturas automáticas
  (`ATRIO_SHOTS`) usan el lanzador salvo que se pida otra cosa.
- Teclado: `Esc` vuelve, `/` busca, `F11` pantalla completa. La cabecera sirve para arrastrar la ventana.
- Letras: Fraunces e Inter, las mismas del móvil (`src/renderer/revista/fuentes/`, licencia OFL).
