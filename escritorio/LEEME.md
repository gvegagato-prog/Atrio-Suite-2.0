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

## macOS (Apple Silicon)

`Atrio.app` se construye en GitHub (acción «Atrio (macOS Apple Silicon)»: artefacto «Atrio-macOS-arm64», con un `.zip` y
un `.dmg`). Sin cuenta de Apple Developer va firmada «ad-hoc», así que la primera vez: clic derecho → Abrir (o
`xattr -dr com.apple.quarantine /Applications/Atrio.app`). Probarla desde Linux no es posible: `npm run dist:mac` genera el
`.app` pero no se puede ejecutar ni firmar fuera de un Mac. Datos: `~/Library/Application Support/Atrio-datos`.
Captura de pantalla con `screencapture` (pide permiso de Grabación de pantalla); apagar/reiniciar/suspender con
`osascript` y `pmset`; arranque al iniciar sesión con los «Elementos de inicio de sesión» (`app.setLoginItemSettings`;
«En segundo plano» lo hace Atrio al ver `wasOpenedAtLogin`, porque macOS ya no deja pedir «abrir oculto»; si el sistema
pide aprobarlo, Ajustes → Comportamiento lo dice y abre «Elementos de inicio»). Menú Edición propio: Deshacer, Rehacer y
Buscar se los pide a la página (`menu` por IPC), porque en el editor de Escribir son suyos. Juegos añadidos `.app`: se
abre el ejecutable de dentro (`CFBundleExecutable`) para que cuenten las horas. Icono: `build/icon-mac.png`
(`herramientas/icono-mac.py`, 1024 px con la retícula de macOS). En CI, `herramientas/prueba-escribir-atrio.js` con
`ATRIO_BIN` prueba la app firmada por CDP.

## Escribir

Dentro de la revista (modo Revista → «Escribir»): obras, capítulos y escenas, el editor con formato a la vista, fichas,
notas, borradores, historial, línea temporal, cifras, papelera, sprints y exportar a Word/PDF/EPUB/Markdown/texto. Lo
guarda y sube `src/main/escritos.js` (mezcla en `escritos-mezcla.js`; formato en `docs/escribir.md`); el PDF lo imprime
Chromium (`escritos-pdf.js`), los sinónimos están en `sinonimos.js` (el tesauro se copia de
`movil/app/src/main/assets/sinonimos` con `extraResources`) y el menú del botón derecho con el corrector en
`menu-contextual.js`. La interfaz está en `src/renderer/revista/js/escribir*.js`.
Pruebas: `node herramientas/prueba-escritos.js`, `prueba-escribir-texto.js`, `prueba-escribir-export.js`, y con
Chromium/Electron `prueba-escribir-ui.js` y `prueba-escribir-atrio.js`.
