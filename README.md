# Atrio

Una sola app en dos sitios:

- **Atrio en el móvil** (`movil/`, Android): tus juegos, libros, películas, series y categorías propias; valoraciones,
  apuntes, calendario, estadísticas, «Siguiente», modo pareja, copias en Drive… y el **mando del PC**.
  Es la app que antes se llamaba Ronrón: mismo paquete y misma firma, se instala encima sin perder datos.
- **Atrio en el PC** (`escritorio/`, Linux y Windows): el launcher de Steam, Heroic, Lutris, emuladores y juegos
  propios, con arte de SteamGridDB, mando, horas de juego y tus fichas… **conectado al móvil en directo**.

Las dos usan la misma cuenta de Atrio (Firebase) y se hablan en **tiempo real** (streaming de Firebase).

## Atrio en el escritorio: la revista

Atrio en el PC se abre ahora con **la revista**: la misma estética de papel y tinta que la app del móvil, pensada
para ratón, teclado y pantalla ancha (maqueta de periódico, hasta ~1400 px de ancho, y bien también en ventana
estrecha). El lanzador de siempre (pantalla grande y mando) sigue ahí: se cambia de una a otra en
Ajustes → «Abrir el lanzador clásico» y, desde el lanzador, Ajustes → «Volver a la revista». Atrio recuerda la última.

- **Portada:** «ATRIO» enorme, el número del día, «En portada» con lo que estás jugando, leyendo o viendo (la única
  imagen, con el arte del PC si es el mismo juego), «Ahora mismo», las notas más altas, lo último que has terminado
  y el sumario. Sin cuenta, invita a entrar y Tu PC funciona igual.
- **Tal día como hoy** (en la portada) y **Tu año, número especial**: cifras, la portada del año, lo mejor, mes a mes,
  tu nota media y la decepción; se puede ir a años anteriores.
- **Secciones** (Videojuegos, Libros y tus categorías): En curso / En lista / Terminados / Dejados, buscador, orden
  y «+ Añadir»: videojuegos con SteamGridDB, libros con Open Library, películas con TMDB y series con TVmaze (los
  mismos ids que usa el móvil, así la ficha es la misma en los dos), o a mano.
- **Descubrir** un juego, un libro o una peli o serie: las mismas preguntas que el móvil (teclas A–E, espacio para
  saltar), filtros de año y plataforma para juegos y «A mi lista +». Usa las claves de RAWG y TMDB de tu cuenta.
- **Fichas en casillas:** nota gigante con criterios, opinión con capitular, datos, apuntes, apuntes rápidos, horas,
  partes, «Jugar en el PC» y capturas. Apuntar, valorar partidas, terminar, cambiar de estado, favorito, borrar…
  Todo se sincroniza con el móvil al momento.
- **Diario** (calendario de periódico), **Cifras** (estadísticas) y **Tu PC** (la biblioteca del lanzador).
- **Ajustes:** tema claro, oscuro o automático; acento (rojo, azul, verde, mostaza, morado, naranja, rosa o tinta);
  cuenta; clave de SteamGridDB.
- Teclado: `Esc` vuelve, `/` busca, `F11` pantalla completa.
- Prueba sin Electron: `node herramientas/revista/prueba-revista.js carpeta` (Chromium con una cuenta de ejemplo).
- Para abrir una u otra sin tocar la configuración: `--revista` / `--lanzador` (o `ATRIO_UI=revista|lanzador`).

## Atrio 6.8 (móvil)

- **Apariencia:** de los estilos solo queda lo que hace falta: claro, oscuro o como el móvil, y el color del
  acento (rojo de imprenta por defecto; también azul, verde, mostaza, naranja, rosa, morado o tinta). La revista
  es siempre papel liso con sus letras (Fraunces e Inter).
- **Lector:**
  - PDF y cómics (CBZ) **deslizando hacia abajo**, para manga: en «Letra y página → Pasar página», «Página a página»,
    «Deslizando hacia abajo» (las páginas una debajo de otra) o «Tira continua» (sin hueco, para webtoons). Se guarda
    por libro; los botones de volumen bajan una pantalla.
  - Con la estética de la revista y el acento elegido.
  - Línea de estado abajo con lo que te queda del capítulo, calculado con tu velocidad de lectura de verdad, y el %.
  - Notas al pie en una hoja, sin perder la página.
  - Imágenes a pantalla completa con zoom.
  - «Ir a» un % o a una página.
  - Tiempo leído: total, hoy y días seguidos. También se ve en la ficha del libro.
  - **Leer en voz alta** con la voz del móvil, marcando la frase que va diciendo, con pausa, velocidad y paso de
    capítulo solo.
  - Brillo deslizando por el borde izquierdo.
  - Ajustes nuevos: guiones, espacio entre párrafos, dos columnas en horizontal, animación al pasar página y la
    letra Fraunces para leer.
- **Titulares** que nunca se parten a mitad de palabra.
- **Fotos de las casillas:** llenar la casilla, la forma de la foto, vertical, cuadrada o apaisada; recortada o
  entera.
- **Descubrir juegos:** filtros de años y plataformas.

## Atrio 6 (móvil): cambio completo

La app del móvil se ha rehecho por fuera: otra estética, otra navegación y otras animaciones. Por dentro
conserva todo (valoraciones, opiniones, guías, estudios, duración media, abrir/cerrar/stremear juegos del PC,
capturas, calendario, modo pareja, sincronización).

- **Navegación nueva:** dock abajo con Inicio, Juegos, Libros, Cine y Diario. Perfil y ajustes, arriba a la derecha.
- **Inicio:** «Sigue donde lo dejaste» (juegos, libros con % leído, series), el PC en directo, «¿Qué empiezo?»,
  objetivos del año, colecciones y lo que te recomienda tu pareja.
- **Fichas con portada grande** (la portada vuela desde la lista), datos en chips y la acción principal a mano:
  Streaming (juegos del PC), Leer (libros con archivo) o Apuntar.
- **Rotten Tomatoes:** nota de la crítica (Tomatómetro) y del público en la portada de cada peli y serie.
  Se lee de la web pública de Rotten Tomatoes (no hay API abierta); se guarda 7 días.
- **Descubrir con preguntas:** 330 preguntas de pelis/series y 320 de libros. Cada test te hace 5, 7 o 10
  (elige tú) y devuelve muchos resultados ordenados por afinidad. Pelis/series con TMDB (clave gratuita),
  libros con Open Library.
- **Lector de libros:** adjunta a cada libro un **EPUB o PDF del propio móvil** (no se sube a ninguna parte).
  Temas (papel, sepia, gris, noche, negro), brillo y luz cálida, tamaño y tipo de letra, interlineado, márgenes,
  justificado, paginado o desplazamiento, subrayados en 4 colores y subrayado de línea, notas, marcadores,
  índice, búsqueda en todo el libro, teclas de volumen para pasar página y recuerda por dónde ibas.
  Basado en foliate-js y pdf.js (licencias en `assets/lector/LICENCIAS.txt`).
- **Estilo:** tema «Vitrina» nuevo, color de acento, intensidad de las animaciones, tamaño de portadas,
  nombres en el dock, fondo «Aurora»; todo en Perfil → Estilo.

## Lo nuevo (Atrio 5)

**En directo**
1. El móvil ve al momento a qué juegas en el PC: fondo, logo, cronómetro de la partida y horas totales.
2. Indicador en Atrio cuando el móvil está conectado (punto rojo que late) y panel con tus dispositivos.
3. Si tienes varios PC con Atrio, eliges a cuál mandar las órdenes.
4. Los cambios entre móvil y PC se mezclan al instante (antes cada 20–30 s).

**Capturas desde el móvil**
5. Botón **Captura** en el móvil → Atrio captura la pantalla a su **resolución real** (PNG sin pérdida, o JPG si lo prefieres).
6. Llega al móvil tal cual, byte a byte, y se guarda en la app y en `Imágenes/Atrio/<juego>`.
7. En el PC se guarda en `Imágenes/Atrio/<juego>`; el PC hace un destello y avisa de que la ha enviado.
8. Visor con zoom (pellizco y doble toque), compartir, abrir en la galería y borrar.
9. Galería de capturas en el móvil, por juego en cada ficha, y en Atrio (página del juego y panel del móvil).
10. Capturar también desde el propio Atrio (panel del móvil).
11. En Linux usa la herramienta del escritorio (KDE: Spectacle, sin avisos; Sway/Hyprland: grim; GNOME:
    gnome-screenshot; X11: Chromium o ImageMagick); en Windows, la captura de Chromium a tamaño físico.

**Apuntes**
12. **Apunte rápido** desde la tarjeta en directo, la ficha o Atrio: texto suelto mientras juegas.
13. Apunte con la **captura de ese momento** incluida (un toque).
14. Desde el visor de una captura: «Apunte» con esa captura.
15. Los apuntes rápidos se ven en el móvil y en Atrio (ficha del juego) y se editan o borran en ambos.
16. Si móvil y PC añaden apuntes a la vez, se suman (no se pisan).

**Valorar partes**
17. **Automático:** al cerrar un juego que tienes en «Jugando», Atrio guarda la partida como **parte jugada**
    (inicio, fin, minutos, PC) y suma sus minutos.
18. Aparece al momento en el móvil en **Por valorar**; con la app cerrada, una notificación te lo recuerda.
19. Valorar una parte = un apunte con nota (los mismos anillos de siempre) ligado a esa partida.
20. **Desde el temporizador:** al terminar una sesión del móvil, se guarda como parte y te ofrece valorarla.
21. **Manual:** «Añadir parte» en cualquier ficha en curso (duración y día), y se valora al momento.
22. **Capítulos:** al marcar un capítulo/acto/episodio como completado, te ofrece valorarlo.
23. En Atrio, la ficha del juego lista las partidas sin valorar (en rojo) y se valoran con el mando.
24. En Atrio, los juegos en curso llevan un contador rojo con las partidas pendientes.
25. Puedes descartar una parte que no quieras valorar.

**Mando del PC** (pantalla «Mando» del móvil)
26. Biblioteca del PC en el móvil (portadas, favoritos, horas) con buscador: **toca un juego y se abre en el PC**.
27. «Seguir jugando»: los últimos juegos del PC a un toque.
28. Traer Atrio al frente, volver a leer las bibliotecas.
29. **Suspender, reiniciar o apagar** el PC (con confirmación).
30. Mandar un **mensaje** que sale como aviso en la pantalla del PC.
31. Lista de dispositivos de la cuenta y cuándo se vieron por última vez.
32. Si el juego que se juega en el PC no tiene ficha, al apuntar se añade a «Jugando» (o vuelve, si lo rejuegas).

**Guías dentro de la página del juego** (juegos online: Deadlock, LoL, Dota 2…)
37. En los juegos online (se detectan solos con los datos de Steam: multijugador sin modo de un jugador, o MMO;
    también se puede marcar a mano en Opciones) la página del juego tiene un botón **Guías**.
38. La web se abre **dentro de la propia página del juego**, a la derecha, sin salir de Atrio (también las webs
    que no se dejan incrustar). Sesión propia: si inicias sesión en una web, sigue iniciada.
39. Varias webs por juego, como pestañas; «Guardar» la deja fija, clic derecho la quita.
40. Sugerencias comprobadas para Deadlock (Deadlock Wiki, Statlocker, Mobalytics, Deadlock Labs), League of
    Legends (OP.GG, U.GG, Mobalytics) y Dota 2 (Dotabuff), y buscador de guías para cualquier juego.
41. Desde el móvil: ver las guías de cada juego del PC, abrirlas en el móvil o **en el PC**, y adjuntar una web.
42. **Compartir → Atrio** desde el navegador del móvil: elige el juego y la web aparece en su página del PC
    (le llega aunque el PC esté apagado en ese momento).
43. En la tarjeta en directo, botón **Guías** del juego que se está jugando.
44. En el móvil, la **ficha de cada juego** (en curso o valorado) tiene el apartado **Guías** arriba del todo:
    tarjetas grandes con las guías del PC, las guardadas en el móvil y las sugeridas, y «Buscar guías».
45. Al tocar una guía se abre **dentro de la app**, a pantalla completa, para tenerla en el móvil mientras juegas
    en el PC (atrás, recargar, compartir, abrir en el navegador). Mantener pulsada: abrirla en el PC o guardarla.

46. Las guías **siguen abiertas** aunque salgas de la app o cierres la ventana: vuelves y están donde las dejaste.
    Si el móvil cerró la app del todo, se reabren en la última página vista («Seguías en…»; o «Empezar desde el inicio»).

**Streaming: jugar en el móvil a los juegos del PC** (Polaris en el PC + Nova en el móvil)
47. Cada juego del PC tiene dos botones grandes: **Jugar en el PC** y **Streaming**. Streaming arranca Polaris,
    lanza el juego en el PC y abre Nova directamente en ese PC (sin elegir nada en Nova).
48. En la tarjeta en directo, **Streaming** para seguir en el móvil la partida que ya está en marcha.
49. **Emparejar con un toque**: Atrio pide a Polaris un código de un solo uso y se lo pasa a Nova en un enlace
    (`art://…`); en Nova solo confirmas. También vale el PIN de 4 números que enseña Nova.
50. Fuera de casa: Atrio detecta **Tailscale** en el PC y «Emparejar por Tailscale» añade el PC a Nova con esa dirección.
51. **Encender el PC** desde el móvil (Wake-on-LAN, en la wifi de casa).
52. En Atrio del PC → Ajustes → Streaming: estado de Polaris, instalarlo (AUR `polaris-gamestream-bin` o paquetes
    oficiales), arrancarlo, tu usuario de Polaris (o su clave de API), puertos del cortafuegos y qué aplicación abre
    Nova («Desktop»). Polaris solo existe para Linux.

**Estilo** (móvil: Ajustes → Apariencia → Estilo · PC: Ajustes → Apariencia)
53. **30 combinaciones** listas (Arcade, Cyberpunk, Revista, Fantasía, Terminal, Dracula, Tokyo Night…).
54. **28 paletas**, cada una con versión clara y oscura; las de editores (Dracula, Nord, Catppuccin, Gruvbox,
    Solarized, Tokyo Night, Rosé Pine, Everforest) con sus colores oficiales.
55. **88 letras** en el móvil (48 para títulos y 40 para el texto, de Google Fonts) y tamaño del texto.
56. **12 iconos** de la app para elegir (y el icono nuevo de Atrio, con luz en la puerta, también en el PC).
57. En el PC, las mismas paletas como **37 esquemas** y **13 juegos de letras** nuevos (van dentro de la app,
    funcionan sin internet). Se generan de las paletas del móvil (`herramientas/gen-estilos.py`).

**Valorar por partes, a tu gusto**
58. Cada juego en curso tiene el interruptor **Valorar por partes** (móvil: en su ficha; PC: menú ⋯ de la ficha).
    Apagado (por ejemplo en Deadlock o LoL), cada partida sigue sumando horas, pero no pide nota, no avisa, no sale
    como pendiente y los capítulos o las sesiones con temporizador tampoco ofrecen valorarlas.
60. Las fichas de **Jugando** (y de lo que estás leyendo o viendo) se abren en **modo lectura**, como los valorados:
    estado, plataforma, horas y fechas en texto. Los campos, el progreso, la portada y «Eliminar» salen con **Editar**
    (bajo el título y abajo del todo); «Listo» los vuelve a cerrar. Lo recién creado se abre ya en edición.

**Arreglos**
61. Apagar, reiniciar o suspender desde el móvil prueba varias vías (systemctl, logind, Plasma, GNOME); si algo lo
    impide, el móvil te dice qué (por ejemplo, el programa que lo bloquea). La orden ya no puede repetirse al encender.
62. **Formas** (Estilo → Forma, en el móvil y en el PC): cada estilo dibuja las cosas a su manera, no solo cambia colores.
    Diez formas: Clásica (anillos), Editorial (barras finas, marcos de foto, grano), Píxel (bloques de 8 bits, sombra dura,
    líneas de TV), Terminal (barras ▓▓░░, [ botones ]), Neón (diales de aguja, esquinas biseladas, rejilla), Suave
    (todo redondo, barras gorditas), Brutalista (cifras enormes, bordes negros), Cómic (estrellas, tinta y trama),
    Minimal (puntos) y Militar (segmentos, esquinas cortadas). En el móvil también cambia la cuadrícula de la biblioteca:
    pósters, mosaico apretado, lista, tarjetas anchas o título sobre la portada. Cada combinación trae su forma.
63. **Notas aparte** (Estilo → Notas): el marcador de las notas (anillos, barras, bloques, texto, segmentos, dial,
    cifra enorme, estrellas o puntos) se elige por separado de la forma, para mezclar, por ejemplo, tarjetas de Píxel
    con estrellas. **Todo lo del aspecto está en Estilo** (tema claro u oscuro, fondo, intensidad y colores incluidos):
    Ajustes solo tiene la tarjeta que lo abre. **Sorpréndeme** lo cambia todo al azar (también en el PC).
64. **Camaleón** (Estilo → Fondo, encendido por defecto): al abrir una ficha, la app se tiñe con el color de su
    portada (fondo, tarjetas y el color de la nota general) y el cambio se abre en círculo desde donde tocaste.
    Al salir vuelve tu paleta.
65. **Streaming sin «error parsing applist cache»**: Atrio del PC lee el UUID de la app de Polaris (su apps.json, o
    su API) y el móvil se lo pasa a Nova, que así abre la app directamente sin depender de su lista guardada. Si no
    se conoce, Nova abre la lista de apps de ese PC en vez de dar error.
66. **Encender el PC con un enchufe de Tuya** (móvil → PC → Streaming → «Enchufe del PC»): con la BIOS en «Restore on
    AC power loss → Power On», Atrio corta la corriente unos segundos y la vuelve a dar, y el PC arranca. Va por la API
    en la nube de Tuya (proyecto gratuito en iot.tuya.com con tu cuenta de Smart Life enlazada), así que funciona
    también con datos. Al tocar «Streaming» con el PC apagado se ofrece (y Wake-on-LAN si estás en casa).
67. **Streaming sin que Atrio robe el foco**: al jugar en streaming desde el móvil, Atrio se minimiza y no vuelve a
    ponerse delante al cerrar el juego; con un juego abierto, las órdenes del móvil no le quitan el foco; y al cerrar
    un juego, Atrio espera 15 s por si vuelve (Deadlock y los que se reinician o pasan por un lanzador).
68. **Calidad en casa y con datos** (móvil → Streaming): qué poner en Nova con la pantalla de tu móvil (nativa y
    90 FPS en wifi; bitrate de datos que Nova aplica sola; perfil «Datos» para 60 FPS).

**Control y privacidad** (Atrio en el PC → Ajustes → Móvil)
33. Permitir o no capturas, lanzar juegos y energía desde el móvil.
34. Formato (PNG/JPG), qué pantalla capturar y si se envían al móvil o solo se guardan en el PC.
35. Partes automáticas sí/no y duración mínima (5/15/30 min).
36. Todo va por tu cuenta: las reglas de Firebase **no cambian** (ver `nube/`).

Y **todo lo que ya había** sigue igual en las dos apps (layouts, temas, mando, emuladores, SteamGridDB,
horas, calendario, pareja, cartas, importaciones, Drive, exportar fichas…).

## Estructura

```
movil/         Atrio en el móvil (Android, Kotlin + Compose). Mismo applicationId y misma firma: se instala encima sin perder datos
escritorio/    Atrio en el PC (Electron). Mismas carpetas de datos: se actualiza sin perder nada
nube/          reglas de Firebase y esquema de datos (ESQUEMA.md)
herramientas/  Firebase de mentira (con streaming), pruebas de punta a punta y prueba en emulador
```

## Construir

- **APK**: GitHub → Actions → «Atrio (móvil)» → artefacto `atrio-movil-apk`. En local: `cd movil && gradle assembleRelease`.
- **AppImage**: Actions → «Atrio (Linux)» → `Atrio-AppImage`. En local: `cd escritorio && ./construir.sh`.
- **Windows**: Actions → «Atrio (Windows)» → `Atrio-instalador-Windows`.

## Pruebas

- `node herramientas/prueba-suite.js`: Firebase de mentira + Atrio de verdad (Xvfb) + un móvil simulado:
  directo, órdenes, captura a resolución real bajada byte a byte, lanzar juego, parte jugada, mezcla al instante,
  streaming con un Polaris de mentira (`herramientas/fakepolaris.js`: detectar, preparar, entrar con usuario y CSRF,
  código de un solo uso, PIN bueno y malo) y apagar.
- `cd movil && gradle testDebugUnitTest`: streaming y mezcla del móvil contra el Firebase de mentira.
- `node herramientas/capturas-atrio.js`: capturas de la interfaz de Atrio con el móvil conectado.
- Actions → «Prueba en emulador»: Atrio en un emulador Android con Atrio de verdad al lado, de principio a fin.

## Capturas

Atrio en el móvil (emulador Android en GitHub Actions, con Atrio de verdad al lado):
[PC conectado](docs/capturas/25_suite_conectado.png) ·
[captura recibida a 1920×1080](docs/capturas/26_captura.png) ·
[biblioteca del PC](docs/capturas/28_biblioteca_pc.png) ·
[en directo](docs/capturas/29_en_directo.png) ·
[apunte rápido](docs/capturas/30_apunte.png) ·
[por valorar](docs/capturas/31_por_valorar.png) ·
[valorar la partida](docs/capturas/32_valorar_partida.png) ·
[Guías en la ficha del juego](docs/capturas/34_ficha_guias.png) ·
[guía abierta en el móvil](docs/capturas/35_guia_abierta.png) ·
[la guía sigue al volver a la app](docs/capturas/36_guia_al_volver.png) ·
[Estilo](docs/capturas/40_estilo.png) ·
[estilo Arcade](docs/capturas/42_arcade.png) ·
[paletas](docs/capturas/43_paletas.png) ·
[iconos de la app](docs/capturas/47_iconos.png)

Atrio en el PC: [panel del móvil](docs/capturas/02-panel-suite.png) ·
[ficha con partidas por valorar](docs/capturas/06-ficha-ronron.png) ·
[ajustes del móvil](docs/capturas/07-ajustes-suite.png) ·
[Guías: inicio](docs/capturas/08-guias-inicio.png) ·
[Guías: web guardada](docs/capturas/10-guias-guardada.png) ·
[Apariencia](docs/capturas/11-apariencia.png) ·
[Streaming](docs/capturas/12-ajustes-streaming.png) ·
estilos [Cyberpunk](docs/capturas/13-estilo-cyberpunk.png), [Arcade](docs/capturas/14-estilo-arcade.png),
[Catppuccin Latte](docs/capturas/16-estilo-latte.png) y [Fantasía](docs/capturas/17-estilo-fantasia.png); formas en el PC: [Clásica](docs/capturas/20-forma-clasico-ficha.png), [Editorial](docs/capturas/21-forma-editorial-ficha.png), [Píxel](docs/capturas/22-forma-pixel-ficha.png), [Terminal](docs/capturas/23-forma-terminal-ficha.png), [Neón](docs/capturas/24-forma-neon-ficha.png), [Suave](docs/capturas/25-forma-suave-ficha.png), [Brutalista](docs/capturas/26-forma-brutal-ficha.png), [Cómic](docs/capturas/27-forma-comic-ficha.png), [Minimal](docs/capturas/28-forma-minimal-ficha.png), [Militar](docs/capturas/29-forma-militar-ficha.png)
