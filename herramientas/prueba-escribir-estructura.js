#!/usr/bin/env node
'use strict';
// La estructura de Escribir (escribir-estructura.js) con los casos de herramientas/casos/estructura.json, los mismos que
// comprueba el móvil (EscribirEstructuraTest.kt). Uso (desde la raíz): node herramientas/prueba-escribir-estructura.js
const fs = require('fs');
const path = require('path');
const S = require('../escritorio/src/renderer/revista/js/escribir-estructura.js');
const j = JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'estructura.json'), 'utf8'));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const igual = (a, b) => JSON.stringify(a) === JSON.stringify(b);
for (const c of j.ids) ok(S.idParte(c.pid, c.nombre) === c.esperado, `id de la parte «${c.nombre}» (${c.pid}) → ${S.idParte(c.pid, c.nombre)}`);
ok(S.idParte('p_1', 'Parte I') === S.idParte('p_1', '  parte   i '), 'el mismo nombre con otros espacios o mayúsculas da la misma parte');
for (const c of j.migracion) ok(igual(S.planMigracion(c.pid, c.caps, c.partes), c.esperado), `migración: ${c.nombre}`);
for (const c of j.estructura) {
  const r = S.estructura(c.caps, c.partes);
  ok(igual({ items: r.items.map((i) => (i.tipo === 'parte' ? { parte: i.parte.id, caps: i.caps.map((x) => x.id) } : { cap: i.cap.id })), libro: r.libro.map((x) => x.id) }, c.esperado), `estructura: ${c.nombre}`);
}
for (const c of j.linea) {
  const L = S.linea(c.hitos, c.ramas);
  ok(igual(L.filas.map((x) => `${x.tipo}:${x.id}@${x.col}[${x.activas.join(',')}]`), c.esperado) && L.columnas === c.columnas, `línea: ${c.nombre}`);
}
ok(igual(S.vinculados('a1', [{ id: 'n1', vin: ['a1', 'c2'] }, { id: 'n2' }, { id: 'f1', vin: ['a2'] }]).map((x) => x.id), ['n1']), 'vinculados a una parte');
console.log(fallos ? `\n${fallos} fallos` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
