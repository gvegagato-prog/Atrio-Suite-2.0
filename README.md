# Atrio

Una sola app en dos sitios:

- **Atrio en el móvil** (`movil/`, Android): tus juegos, libros, películas, series y categorías propias; valoraciones,
  apuntes, calendario, estadísticas, «Siguiente», modo pareja, copias en Drive… y el **mando del PC**.
  Es la app que antes se llamaba Ronrón: mismo paquete y misma firma, se instala encima sin perder datos.
- **Atrio en el PC** (`escritorio/`, Linux y Windows): el launcher de Steam, Heroic, Lutris, emuladores y juegos
  propios, con arte de SteamGridDB, mando, horas de juego y tus fichas… **conectado al móvil en directo**.

Las dos usan la misma cuenta de Atrio (Firebase) y se hablan en **tiempo real** (streaming de Firebase).

## Lo nuevo

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

**Arreglos**
59. Apagar, reiniciar o suspender desde el móvil prueba varias vías (systemctl, logind, Plasma, GNOME); si algo lo
    impide, el móvil te dice qué (por ejemplo, el programa que lo bloquea). La orden ya no puede repetirse al encender.

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
[Catppuccin Latte](docs/capturas/16-estilo-latte.png) y [Fantasía](docs/capturas/17-estilo-fantasia.png)
