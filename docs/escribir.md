# Escribir (Atrio): formato y sincronización

Lo comparten el móvil (`Escritos.kt`), el PC (`escritorio/src/main/escritos.js`) y las pruebas.
Pensado para novelas largas: **un documento por escena** (no un solo bloque con todo), así que subir
un cambio es subir unos pocos KB, y se puede escribir sin conexión.

## En Firebase (solo tu cuenta; las reglas ya lo permiten: `/users/$uid` es tuyo)

```
/users/<uid>/escritos/<id>      el documento (objeto JSON)
/users/<uid>/escritosIdx/<id>   su hora de edición «u» (número): el índice pequeño que se escucha en directo
```

Subir = un solo `PATCH /users/<uid>` con `{"escritos/<id>": doc, "escritosIdx/<id>": u, …}` (atómico).

## Documentos

Todos: `id` (texto), `t` (tipo), `pid` (id de la obra; en la obra, el suyo), `u` (ms de la última edición),
`pu` (la `u` de la versión de la nube de la que parte esta; 0 si es nuevo; no en los registros del día).

Borrado: `d` = 1, `dt` = ms en que se borró y, si se fue con otro (las escenas de un capítulo, lo de una obra), `dc` = el id
de ese. Mientras está en la **papelera** (30 días) conserva todo lo suyo y se puede recuperar; después, o al vaciarla, queda
solo la marca `id,t,pid,u,pu,d` (y esa se quita a los 180 días). Las versiones anteriores a la 6.82 / PC 3.1 dejaban
directamente la marca sola. Los registros del día no se borran con la obra.

| t | qué | campos propios |
|---|-----|----------------|
| `p` | obra | titulo, autor, genero, sinopsis, color (#rrggbb), meta (palabras de la obra), diaria (palabras al día), fin (fecha tope, día), orden, arch (1 = archivada), creado; de la guía de la novela: idea (la frase), gOn (1 = guía activada), g1…g11 (1 = paso hecho a mano, 2 = saltado) y g7t (ms en que empezó el reposo) |
| `a` | parte (acto, libro…) | titulo, orden (en el manuscrito, junto a los capítulos sueltos), color, resumen |
| `c` | capítulo | titulo, orden (decimal; dentro de su parte o, si va suelto, en el manuscrito), aid (id de su parte; vacío = suelto), color (el de su cuadrado), resumen; parte (texto: las partes de antes, ver abajo) |
| `e` | escena | cid, titulo, orden, texto (Markdown ligero), resumen, estado (0 borrador, 1 revisión, 2 final), pov (id de ficha), lugar (id de ficha), cuando (texto), etq (lista), palabras, meta |
| `f` | ficha | tipo (`personaje` `lugar` `objeto` `mundo` `otro`), nombre, alias (texto, separado por comas), resumen, texto, campos ([{k,v}]), color, orden, vin (ids de partes o capítulos a los que está vinculada), tam (tamaño en el árbol: 1 pequeña, 2 mediana, 3 grande; sin él, según el tipo), ax, ay (su sitio en el árbol) |
| `n` | nota | tipo (`nota` `idea` `resumen` `esquema` `investigacion` `plantilla`), titulo, texto, color, fija (1), orden, vin (ids de partes o capítulos) |
| `v` | unión del árbol | a, b (ids de las dos fichas), titulo (la palabra de la línea), texto (todo lo que se quiera contar), flecha (1: de a hacia b), color |
| `r` | rama de la línea temporal | titulo, texto, color, desde (el orden de la línea en que nace), hasta (en el que vuelve a la principal; sin él, acaba en su último suceso) |
| `b` | borrador | eid (escena), nombre, texto, conflicto (1 = lo guardó una mezcla) |
| `h` | suceso (línea temporal) | titulo, cuando (texto), orden (el de la historia, no el del libro), eid (escena enlazada), nota, rama (id de su rama; vacío = la principal), color |
| `l` | registro del día | dia (días desde 1970), dev: {dispositivo: palabras escritas} |

**Partes** (6.89 / PC 3.3): antes la parte era un texto en cada capítulo. Al abrir una obra, cada texto distinto pasa a ser una
parte de verdad, con un id que sale igual en todos los dispositivos (`a_` + FNV-1a de `pid|nombre en minúsculas`, ver
`escribir-estructura.js` / `EscribirEstructura.kt`): si dos dispositivos lo hacen a la vez salen las mismas partes, no el doble.
El orden del libro: lo de arriba (partes y capítulos sueltos) por su orden y, dentro de cada parte, sus capítulos. Borrar una
parte se lleva sus capítulos (y sus escenas); una rama, sus sucesos; una ficha, sus uniones. Casos compartidos:
`herramientas/casos/estructura.json`.

Texto: **Markdown ligero** — `# `, `## ` títulos, `> ` cita, `*cursiva*`, `**negrita**`, `***` línea sola = salto de escena.
El historial de versiones **no viaja**: se guarda en cada dispositivo (cada escena guarda hasta 40 instantáneas).

## Mezcla (igual en todos los dispositivos)

Cada dispositivo apunta, de cada documento, la última hora que vio en el servidor (`acked[id]`).
Un documento está **pendiente** si su `u` ≠ `acked[id]`. Al empezar a cambiar algo que estaba igual que en la nube, esa
versión se guarda aparte (las 3 últimas, en `b/<id>.json`, solo en el dispositivo): es la **base** para juntar.

- Bajar: se lee `escritosIdx`; de lo que tenga una hora distinta a `acked`, se baja el documento (también si la hora es la
  misma que la de aquí: dos dispositivos pueden guardar en el mismo milisegundo; entonces se mira el contenido).
- Si aquí no había cambios y lo de fuera parte de lo de aquí (`pu` = `u` de aquí) → se queda lo de fuera.
- Si se cambió en los dos sitios a la vez, o lo de fuera se subió sin haber visto lo de aquí (`pu` distinto) → se **juntan**
  a tres bandas desde la base (la versión `pu` de lo de fuera o, si no está, la `acked`):
  - campo a campo: lo que cambió en uno solo se queda; si los dos cambiaron el mismo, manda el más nuevo (`u`);
  - los textos largos (`texto`, `resumen`, `sinopsis`, `nota`) por párrafos: párrafos distintos se juntan, párrafos nuevos
    en el mismo sitio se quedan los dos, y un mismo párrafo cambiado en los dos se junta **por palabras** si los cambios no
    se tocan. Si de verdad chocan (la misma frase, la misma palabra): en una escena manda el más nuevo y **el texto entero
    del otro se guarda como borrador** (`b`, `conflicto` = 1, id `bc_<escena>_<u del otro>`); en lo demás se quedan los dos trozos;
  - `palabras` de la escena se vuelve a contar del texto juntado.
  El resultado lleva `u` = la mayor + 1 y `pu` = la `u` de lo de fuera, y se sube.
- Registro del día: por dispositivo, y al mezclar de cada uno se queda el mayor; el total es la suma.
- Una marca de borrado sin contenido (vaciada, o de una versión antigua) no se junta: manda la más nueva.
- Los casos de prueba de la mezcla están en `herramientas/casos/mezcla.json` y los pasan el móvil y el PC.

## Dónde está cada cosa

| | Móvil | PC |
|---|-------|----|
| Núcleo (archivos, papelera, subida) | `Escritos.kt` | `escritorio/src/main/escritos.js` |
| Mezcla a tres bandas | `EscribirMezcla.kt` | `escritorio/src/main/escritos-mezcla.js` |
| Texto (formato, deshacer, diferencias) | `EscribirTexto.kt`, `EscribirDiff.kt` | `revista/js/escribir-texto.js` |
| Exportar / importar | `EscribirExport.kt` | `escritorio/src/main/escritos-export.js` |
| Pantallas | `EscribirScreens.kt`, `EscribirMas.kt`, `EscribirEditor.kt`, `EscribirDialogos.kt` | `revista/js/escribir.js`, `escribir-editor.js` |
| Escucha en directo del índice | `Sincro.kt` (stream «escritos») | `escritos-cuenta.js` |
| Papelera | `EscribirPapelera.kt` | `revista/js/escribir.js` (dlgPapelera) |
| PDF | `EscribirPdf.kt` (PdfDocument) | `escritorio/src/main/escritos-pdf.js` (Chromium) |
| Tipografía automática | `EscribirTexto.kt` (Tipografia) | `revista/js/escribir-texto.js` (ET.tipografia) |
| Sinónimos (tesauro RLA-ES, LGPL 2.1) | `EscribirSinonimos.kt` + `assets/sinonimos` | `escritorio/src/main/sinonimos.js` |
| Sprints | `EscribirSprint.kt` | `revista/js/escribir.js` (E.sprint) |
| Guía de la novela (pasos, textos y qué se da por hecho) | `EscribirGuia.kt` | `revista/js/escribir-guia.js` (la lógica) y `escribir.js` (las pantallas) |

Casos compartidos (los pasan el móvil y el PC): `herramientas/casos/mezcla.json`, `tipografia.json`, `sinonimos.json` y `guia.json`.

Pruebas cruzadas: `EscritosInteropTest.kt` hace trabajar al núcleo del móvil con el módulo de JavaScript del PC contra el
mismo Firebase de mentira (leer y escribir, y cambiar a la vez párrafos distintos, borrar y recuperar).

## Copias de seguridad (PC y Mac, 3.2.5)

Una novela no se puede volver a escribir, así que el escritorio guarda **copias completas** en una carpeta de este equipo,
aparte de la cuenta (`<datos>/atrio/escritos-copias`, en el Mac `~/Library/Application Support/Atrio-datos/data/atrio/escritos-copias`):
la primera a los 20 s de abrir, luego cada 15 minutos si algo ha cambiado, y al cerrar la ventana o salir (⌘W, ⌘Q). Se
conservan las últimas 40. Cada archivo (`copia-AAAA-MM-DD_HHMMSS-<n>obras-<p>pal.json`) lleva todos los documentos, también la
papelera. En Escribir → «Copias de seguridad» se ven, se abre la carpeta y de cualquier copia se saca una obra como **obra
nueva** (ids nuevos y enlaces entre capítulos, escenas, fichas, hitos y borradores intactos; nunca pisa lo que hay).
Los archivos se escriben con `fsync` (guardado durable). Código: `escritos-copias.js`; pruebas: `prueba-escribir-copias.js` y
`prueba-escribir-cierre.js` (Atrio de verdad: teclear y cerrar al momento, ⌘W y ⌘Q). Pendiente: el móvil no tiene aún estas copias.
