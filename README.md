# Atrio

Una sola app en dos sitios:

- **Atrio en el móvil** (`movil/`, Android): tus juegos, libros, películas, series y categorías propias; valoraciones,
  apuntes, calendario, estadísticas, «Siguiente», modo pareja, copias en Drive… y el **mando del PC**.
  Es la app que antes se llamaba Ronrón: mismo paquete y misma firma, se instala encima sin perder datos.
- **Atrio en el PC** (`escritorio/`, Linux, Windows y macOS con Apple Silicon): el launcher de Steam, Heroic, Lutris, emuladores y juegos
  propios, con arte de SteamGridDB, mando, horas de juego y tus fichas… **conectado al móvil en directo**.

Las dos usan la misma cuenta de Atrio (Firebase) y se hablan en **tiempo real** (streaming de Firebase).

## Escribir (desde el móvil 6.80 y el PC 3.0; a fondo en 6.82 y 3.1)

Un apartado para escribir novelas, en el móvil, en el PC y en el Mac, con la misma cuenta y **sin conexión incluida**:
lo que escribes se guarda en el dispositivo al momento, se sube solo cuando hay red y llega a los demás en directo.

- **Obras, capítulos y escenas.** Cada escena es un documento (una novela larga no es un bloque enorme). Las partes
  («Parte I»), el orden (arrastrar en el PC; subir y bajar en el móvil) y mover escenas entre capítulos. Estados de
  cada escena (borrador, revisión, final) y vista de esquema con los resúmenes.
- **El editor.** Una página de libro con el formato a la vista (títulos, citas, *cursiva*, **negrita** y saltos de
  escena `***`, con los signos apagados), deshacer y rehacer, buscar y reemplazar (en la escena o en toda la obra),
  modo concentración, máquina de escribir (la línea queda a media altura), tamaño y tipo de letra, y contadores
  (palabras, tiempo de lectura, lo escrito hoy frente a tu meta). Se guarda solo.
  **Tipografía automática** (`--` es una raya, `- ` al empezar la línea abre un diálogo, `...` son puntos suspensivos y
  las comillas son « »; se deshace aparte), **sinónimos** de la palabra (tesauro libre de RLA-ES, en el dispositivo),
  **sprints** de escritura con meta e historial, atajos de teclado (también con un teclado físico en Android: Ctrl+B,
  Ctrl+I, Ctrl+Z, Ctrl+Y, Ctrl+F, Ctrl+S) y, en el PC, corrector ortográfico en español y menú del botón derecho
  (correcciones, añadir al diccionario, sinónimos, cortar/copiar/pegar).
- **Fichas del mundo.** Personajes, lugares, objetos y reglas, con datos y alias: en cuanto nombras uno en el texto
  queda subrayado y lo tienes en el panel, con «poner el nombre».
- **Notas y tablón.** Notas, ideas, investigación y **plantillas** que puedes tener al lado mientras escribes
  (y insertar con un toque), con color y fijadas arriba.
- **Borradores, historial y comparar.** Borradores alternativos de una escena y las últimas 40 versiones (en cada
  dispositivo). Se comparan párrafo a párrafo y se mezclan eligiendo de cada diferencia.
- **Cambios a la vez en dos sitios: se juntan.** Si cambias la misma escena en el móvil y en el PC (sin conexión, o a la
  vez), se juntan por campos, por párrafos y, dentro de un párrafo, por palabras. Solo si los dos cambian la misma frase
  manda la más nueva, y **el texto entero de la otra queda como borrador** (no se pierde nada).
- **Papelera.** Lo que borras (una obra con todo lo suyo, un capítulo con sus escenas, una escena, una ficha…) se queda
  30 días en la papelera, en todos tus dispositivos, con «Deshacer» al borrar y «Recuperar» (vuelve a su sitio).
- **Línea temporal y cifras.** Hitos ordenables enlazados a escenas, escenas con «cuándo pasa», palabras por día
  (30 días), racha, mejor día, palabras por capítulo, estado del manuscrito y cuándo terminarás al ritmo que llevas.
- **Exportar e importar.** Word (.docx), **PDF**, libro (.epub), Markdown y texto. Word y PDF en dos estilos: «Libro»
  (A5 con las letras de la revista, justificado, capítulos en página nueva) y **«Manuscrito»** para editoriales y
  concursos (Times New Roman 12, márgenes de 2,5 cm, interlineado 1,5 o doble, portada con el recuento de palabras,
  páginas numeradas y FIN; no hay un formato único: si una editorial pide otro, mandan sus normas). En Android,
  «Guardar en…» (cualquier carpeta o nube) o Compartir. Importar Word, Markdown o texto (los títulos abren capítulos).
  Todo se hace en el dispositivo: el texto no se envía a ningún sitio.
- **Seguir donde lo dejaste.** La biblioteca empieza por la última escena que tocaste (en cualquier dispositivo) y la
  portada de la revista tiene el bloque «Escribiendo» con «Seguir con…». El manuscrito se filtra por etiquetas.
- **Sin IA.** No hay nada que mande tu texto a un servicio de terceros.

El formato y las reglas de mezcla están en `docs/escribir.md`. Los datos van en `/users/<uid>/escritos` (un documento por
escena, fichas…) con un índice pequeño (`escritosIdx`) que escuchan todos los dispositivos. Ojo: las reglas de la base de
datos dejan a tu pareja **leer** `/users/<tu uid>` entero si la vinculas; Atrio solo le pide tus fichas, pero las obras
quedan al alcance de su cuenta (si lo quieres cerrar, hay que afinar las reglas en la consola de Firebase).

## Atrio en macOS (Apple Silicon)

La acción «Atrio (PC, macOS Apple Silicon)» (`.github/workflows/escritorio-mac.yml`) construye `Atrio.app` en un Mac
de GitHub, la firma «ad-hoc» (sin certificado de Apple Developer), la **prueba de verdad** (la abre con una cuenta de
prueba y la maneja por CDP: Escribir en directo, sin conexión, tipografía, deshacer, PDF y Word; deja capturas en el
artefacto «atrio-mac-pruebas») y deja **Atrio-mac-arm64.zip** y **Atrio-mac-arm64.dmg** (con el acceso a Aplicaciones
para arrastrar) en los artefactos de la ejecución.
Como no está firmada con un certificado de Apple ni notarizada, macOS avisará la primera vez: abre Atrio con clic
derecho → «Abrir», o ejecuta `xattr -dr com.apple.quarantine /Applications/Atrio.app`. Para quitar el aviso para
siempre hace falta una cuenta de Apple Developer (99 $ al año) con certificado y notarización.
Datos en `~/Library/Application Support/Atrio-datos`. En el Mac funcionan la revista, Escribir, la cuenta, el móvil,
las capturas (`screencapture`; pide el permiso de «Grabación de pantalla») y lo jugado en Steam o Heroic; no existen
Lutris ni Polaris (streaming). Cosas propias del Mac: menú Edición en español (Deshacer y Rehacer también en el editor
de Escribir), atajos con ⌘, juegos añadidos a mano como aplicación (`Juego.app`, con sus horas), abrirse al iniciar
sesión (si macOS pide aprobarlo, Ajustes → Comportamiento lleva a «Elementos de inicio») y un icono con la retícula de
macOS. **No lo he podido probar en un Mac físico**: solo en el Mac de GitHub (sin pantalla real ni permisos).

## Atrio en el escritorio (2.1): el lanzador y la revista

Atrio en el PC se abre siempre **a pantalla completa en el modo Juegos**: el lanzador, con la estética de la
revista. Arriba, «ATRIO» y tres secciones: **Valorados · Juegos · Revista** (LB y RB pasan de una a otra).

- **Juegos:** arriba, el escaparate: un juego a lo grande (su fondo, el titular, la descripción de la tienda y
  «Jugar →» ya enfocado: una pulsación de A y a jugar). Todos los títulos del modo Juegos van en mayúsculas. En las tarjetas de juego manda el nombre: grande, con la
  letra de la revista y un filete encima, y la portada pequeña al lado (como el índice de una revista). Debajo, «Seguir jugando» y «Favoritos» en filas de
  portadas que se recorren de lado: el escaparate pasa al juego que enfoques (la imagen se funde), subes y
  juegas. Cada fila recuerda dónde la dejaste. Después, toda la biblioteca en portadas (por recientes, A–Z u
  horas; filtrada por tienda); un juego sin imagen tiene una portada de tipografía. Cada juego tiene su
  página: Jugar, guías, favorito, sus datos (horas, última vez, estudio, género, Metacritic) y, si está en tu
  cuenta, tu nota y tu crítica o tu último apunte. Y abre las opciones (favorito, ocultar, su ficha, guías, **imágenes**: portada, horizontal, fondo y logo de
  SteamGridDB o desde un archivo, **cambiar el nombre** y «juego equivocado»). Y también funciona con los juegos
  que no están instalados (Valorados, la revista): favorito, nota, opinión, apuntes, estado, datos, borrar y
  **sus imágenes** (portada, fondo y logo de la ficha, de SteamGridDB o pegando una dirección; se guardan en la
  cuenta y se ven también en el móvil). En cada ficha de la revista hay un enlace «Imágenes».
- **Fotos de tu móvil, a máxima calidad:** si en el móvil pones de portada (o de fondo) una foto de tu galería, en
  el PC y en la tableta el selector de imágenes ofrece «Descargar de <tu móvil>». El móvil la sube por trozos, tal
  cual (el original, sin recomprimir) y el otro dispositivo la baja y la borra de la nube. El móvil tiene que tener
  Atrio abierto. Desde Atrio 6.13 el móvil guarda el original de cada foto que eliges; las de antes solo se pueden
  mandar como estaban guardadas (reducidas a 1400 px).
- **Valorados:** tus juegos terminados con la nota en grande y su portada; los instalados se abren para jugar.
- **Revista:** toda tu colección (libros, películas, series, el diario, las cifras, Descubrir…) como antes,
  ahora también con mando.
- **Mando** (también el Steam Controller con Steam Input), **teclado y ratón** a la vez. Cruceta o stick para
  moverse, A elegir, B volver, X buscar, Y opciones, LB/RB secciones, Vista (⧉) cambia entre Juegos y Revista,
  Menú (☰) abre el menú (ajustes, suspender, reiniciar, apagar —con confirmación—, salir) y el stick derecho
  desplaza. Con el teclado: flechas, Intro, Esc, /, RePág/AvPág, C y S (lo mismo que el Steam Controller en
  modo escritorio con la configuración de Ajustes → Mando). El marco del foco solo se ve con mando o teclado.
- **Guías** a pantalla completa (también las que mandas desde el móvil): B cierra, LB/RB atrás y adelante.
- **Ajustes**, todos con la misma estética: la revista (tema y acento), cuenta y SteamGridDB, bibliotecas,
  juegos añadidos, emuladores, juegos ocultos, mando (prueba en vivo y configurar botones), comportamiento
  (al abrir un juego, al iniciar sesión, animaciones), móvil y streaming.
- Animaciones ligeras (120–300 ms: el foco, las portadas, el escaparate, las secciones entran desde el lado al
  que vas con LB/RB, el cambio de modo); se quitan en Ajustes →
  Comportamiento o si el sistema pide menos movimiento.
- Pantallas grandes sin escalar (una tele 4K, un monitor de 1440p al 100 %): todo se amplía solo para verse como
  en 1920 px (el modo Juegos desde 2300 px de ancho; la revista desde 3000).
- El lanzador clásico ya no se ve; sigue dentro solo para las pruebas automáticas (`--lanzador`).
- Prueba sin Electron: `node herramientas/revista/prueba-revista.js carpeta` (Chromium con una cuenta de
  ejemplo y un mando simulado). Capturas para revisar el diseño a varios tamaños:
  `node herramientas/revista/escaparate.js carpeta 1920x1080,1280x800 claro,oscuro` (con `ATRIO_ARTE=carpeta`
  usa imágenes de verdad).

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

## El Diario (móvil 6.64 y PC 2.8.5)

Cada día como una página: lo que escribes y lo que hiciste con cada cosa, sin tener que apuntarlo.

- **Solo:** lo que terminas (con tu nota y tu opinión), empiezas o dejas; lo leído con el lector; lo jugado en el PC
  (Atrio ya sube las partidas a tu cuenta, en `/users/<uid>/horas`); tus apuntes, subrayados y capturas. Todo junto
  por ficha y por día, del más nuevo al más viejo, con el resumen de cada mes.
- **Escribir:** varias entradas por día, de qué fichas hablas, una foto de la galería (viaja por tu kDrive) y, con
  pareja, si quieres que la vea. Se guarda al salir. La lista `diario` viaja con la cuenta como las fichas.
- **Hoy arriba:** lo escrito, lo hecho y lo que llevas entre manos para apuntar algo en un toque; «tal día como
  hoy» de otros años y la racha de días seguidos.
- **Calendario:** lo terminado con su portada, ✎ si escribiste y el tono de cada día según lo que leíste y jugaste.
- **En la ficha:** la casilla «En tu diario» (lo que has escrito de ella) y «Escribir en el diario» en Enlaces.
  El buscador general también busca en lo escrito. Recordatorio opcional a la hora que elijas.
- **PC:** el Diario de la revista enseña lo mismo y deja escribir.

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
