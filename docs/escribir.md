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
`d` = 1 si está borrado (queda solo `id,t,pid,u,d`; se limpia a los 180 días).

| t | qué | campos propios |
|---|-----|----------------|
| `p` | obra | titulo, autor, genero, sinopsis, color (#rrggbb), meta (palabras de la obra), diaria (palabras al día), fin (fecha tope, día), orden, arch (1 = archivada), creado |
| `c` | capítulo | titulo, orden (decimal), parte (texto), resumen |
| `e` | escena | cid, titulo, orden, texto (Markdown ligero), resumen, estado (0 borrador, 1 revisión, 2 final), pov (id de ficha), lugar (id de ficha), cuando (texto), etq (lista), palabras, meta |
| `f` | ficha | tipo (`personaje` `lugar` `objeto` `mundo` `otro`), nombre, alias (texto, separado por comas), resumen, texto, campos ([{k,v}]), color, orden |
| `n` | nota | tipo (`nota` `idea` `investigacion` `plantilla`), titulo, texto, color, fija (1), orden |
| `b` | borrador | eid (escena), nombre, texto, conflicto (1 = lo guardó una mezcla) |
| `h` | hito (línea temporal) | titulo, cuando (texto), orden, eid (escena enlazada), nota |
| `l` | registro del día | dia (días desde 1970), dev: {dispositivo: palabras escritas} |

Texto: **Markdown ligero** — `# `, `## ` títulos, `> ` cita, `*cursiva*`, `**negrita**`, `***` línea sola = salto de escena.
El historial de versiones **no viaja**: se guarda en cada dispositivo (cada escena guarda hasta 40 instantáneas).

## Mezcla (igual en todos los dispositivos)

Cada dispositivo apunta, de cada documento, la última hora que vio en el servidor (`acked[id]`).
Un documento está **pendiente** si su `u` ≠ `acked[id]`.

- Bajar: se lee `escritosIdx`; de lo que tenga una hora distinta a `acked`, se baja el documento.
- Si aquí no estaba pendiente → gana el de fuera si es más nuevo.
- Si aquí **también** había cambios (se editó en dos sitios a la vez) → gana el más nuevo (`u`), y **el texto del que pierde
  no se pierde**: se guarda como borrador de esa escena (`b`, `conflicto` = 1, id `bc_<escena>_<u del perdedor>`, igual en todos los dispositivos).
- Registro del día: por dispositivo, y al mezclar de cada uno se queda el mayor; el total es la suma.
- Borrados: marca `d` (gana si es más nueva que la edición).
