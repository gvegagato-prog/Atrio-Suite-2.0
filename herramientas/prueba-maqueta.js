#!/usr/bin/env node
'use strict';
// Las maquetas de las fichas en el PC: qué maqueta vale para una ficha y dónde se dibuja una imagen encuadrada.
// Mismos casos que el móvil (herramientas/casos/maqueta.json). Uso: node herramientas/prueba-maqueta.js
const fs = require('fs');
const path = require('path');
const M = require('../escritorio/src/renderer/revista/js/maqueta.js');
const j = JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'maqueta.json'), 'utf8'));
let fallos = 0;
const ok = (c, m) => { console.log(`${c ? '  ✔' : '  ✘'} ${m}`); if (!c) fallos++; };
const cerca = (a, b) => Math.abs(a - b) < 1e-3;

console.log('Dónde se dibuja una imagen encuadrada');
for (const c of j.lugar) {
  const r = M.lugar({ zoom: c.c.zoom, ox: c.c.ox, oy: c.c.oy, entera: c.c.fit }, c.caja[0], c.caja[1], c.ratio);
  ok(cerca(r.z, c.esperado.z) && cerca(r.tx, c.esperado.tx) && cerca(r.ty, c.esperado.ty), `${c.nombre}: zoom ${r.z.toFixed(2)}, ${r.tx.toFixed(1)}, ${r.ty.toFixed(1)}`);
}
// Nunca deja hueco: la foto, movida como se mueva, cubre toda la casilla
let hueco = 0;
for (const ratio of [0.5, 0.8, 1, 1.6, 2.5]) for (const oy of [-5, -0.4, 0, 0.4, 5]) for (const ox of [-5, 0, 5]) for (const zoom of [0.3, 1, 2]) {
  const [bw, bh] = [320, 200];
  const r = M.lugar({ zoom, ox, oy, entera: false }, bw, bh, ratio);
  const w = r.dw * r.z; const h = r.dh * r.z;
  if (r.tx - w / 2 > -bw / 2 + 1e-6 || r.tx + w / 2 < bw / 2 - 1e-6 || r.ty - h / 2 > -bh / 2 + 1e-6 || r.ty + h / 2 < bh / 2 - 1e-6) hueco++;
}
ok(hueco === 0, 'Con cualquier zoom y desplazamiento, la foto cubre toda la casilla (sin huecos)');

console.log('Qué maqueta vale para una ficha');
for (const c of j.resolver.casos) {
  const r = M.resolver(j.resolver.lista, c.t, c.kind, c.id);
  if (c.filas === 0) { ok(r === null, c.nombre); continue; }
  ok(r && r.length === c.filas && JSON.stringify(r[0].celdas.map((x) => x.tipo)) === JSON.stringify(c.primera) && (c.split === undefined || r[0].split === c.split), c.nombre);
}
const r0 = M.resolver(j.resolver.lista, 'g', 1, 7);
ok(r0[0].celdas[0].asp === 1.2 && r0[1].celdas[0].titulo === 'Mi veredicto' && r0[1].celdas[0].texto === 'Genial', 'Lee el encuadre guardado («as»), el título y el texto de las casillas libres');
console.log('Dónde verla (lo de España en la respuesta de TMDB)');
for (const c of j.proveedores) {
  const r = M.proveedores(c.entrada);
  ok(JSON.stringify(r) === JSON.stringify(c.esperado), c.nombre);
}
console.log(fallos ? `\n${fallos} fallos` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
