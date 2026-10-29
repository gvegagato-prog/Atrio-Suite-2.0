#!/usr/bin/env node
'use strict';
// La lógica de lo leído en el ordenador (escritorio/src/main/lector-estado.js): el tiempo por dispositivo, qué días cuentan
// como leídos, por dónde se abre el libro y cómo se aplica lo que llega de otro dispositivo. Los casos de herramientas/casos/lectura.json
// los pasa también el móvil (EncuadreTest.kt). Uso: node herramientas/prueba-lector.js
const fs = require('fs');
const path = require('path');
const E = require('../escritorio/src/main/lector-estado.js');
const j = JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'lectura.json'), 'utf8'));
let fallos = 0;
const ok = (c, m) => { console.log(`${c ? '  ✔' : '  ✘'} ${m}`); if (!c) fallos++; };
const igual = (a, b) => JSON.stringify(a) === JSON.stringify(b);

console.log('El tiempo leído, por dispositivo (casos compartidos con el móvil)');
for (const c of j.mezcla) ok(igual(E.mezclaStats(c.a, c.b, c.aKey, c.bKey), c.esperado), c.nombre);
console.log('Qué días cuentan como leídos');
for (const c of j.cuenta) ok(E.cuentaDia(c.st, c.dia) === c.esperado, c.nombre);

console.log('Por dónde se abre el libro');
ok(igual(E.empiezaEn({ cfi: 'a', fraction: 0.2 }), ['a', 0.2]), 'sin «más lejos»: donde lo dejaste aquí');
ok(igual(E.empiezaEn({ cfi: 'a', fraction: 0.2, bestCfi: 'b', bestFrac: 0.6 }), ['b', 0.6]), 'si en otro dispositivo llegaste más lejos, ahí');
ok(igual(E.empiezaEn({ cfi: 'a', fraction: 0.7, bestCfi: 'b', bestFrac: 0.6 }), ['a', 0.7]), 'si aquí has llegado más lejos, aquí');
ok(igual(E.empiezaEn({ cfi: 'a', fraction: 0.2, bestFrac: 0.6 }), ['a', 0.2]), 'sin el punto exacto de lo más lejano, no vale');

console.log('Lo que sube a la cuenta');
const local = { uri: '/Users/yo/libro.epub', name: 'libro.epub', added: 5, syncAt: 9, cfi: 'c', fraction: 0.5, settings: { theme: 'sepia', size: 120, brightness: 0.3, flow: 'paginated' }, notes: [{ value: 'v', text: 't' }] };
const sube = E.paraLaCuenta(local);
ok(!('uri' in sube) && !('name' in sube) && !('added' in sube) && !('syncAt' in sube), 'sin el archivo de este equipo');
ok(!('brightness' in sube.settings) && !('size' in sube.settings) && sube.settings.theme === 'sepia', 'sin el brillo ni el tamaño de letra (son de cada pantalla)');
ok(local.uri && local.settings.size === 120, 'y sin tocar lo de aquí');

console.log('Lo que llega de otro dispositivo');
const aqui = { uri: '/x/libro.epub', name: 'libro.epub', added: 1, syncAt: 100, cfi: 'aqui', fraction: 0.3, label: 'Cap. 3', read: 50, bestCfi: 'aqui', bestFrac: 0.3, settings: { size: 130, brightness: 0.4, theme: 'papel' }, stats: { dev: { mac: { total: 600000, days: { d1: 600000 } } }, total: 600000, days: { d1: 600000 } } };
const movil = { s: { cfi: 'movil', fraction: 0.8, label: 'Cap. 9', bestCfi: 'movil', bestFrac: 0.8, bestLabel: 'Cap. 9', settings: { size: 90, brightness: 0.9, theme: 'sepia' }, notes: [{ value: 'n1', text: 'uno' }], marks: [], stats: { dev: { movil: { total: 300000, days: { d1: 300000 } } }, total: 300000, days: { d1: 300000 } } }, updated: 200 };
const r = E.aplicaRemoto(aqui, movil, 'mac');
ok(r && r.bestFrac === 0.8 && r.cfi === 'movil' && r.fraction === 0.8 && r.label === 'Cap. 9', 'si en el otro llegaste más lejos, te abre ahí');
ok(r && r.notes.length === 1 && r.settings.theme === 'sepia', 'trae sus subrayados y ajustes');
ok(r && r.settings.size === 130 && r.settings.brightness === 0.4, 'pero el tamaño de letra y el brillo se quedan los de aquí');
ok(r && r.uri === '/x/libro.epub' && r.name === 'libro.epub' && r.added === 1, 'y el archivo es el de aquí');
ok(r && r.stats.total === 900000 && r.stats.dev.mac.total === 600000 && r.stats.dev.movil.total === 300000, 'el tiempo leído se suma (el de cada dispositivo)');
ok(r && r.syncAt === 200, 'apunta de cuándo es lo aplicado');
ok(E.aplicaRemoto(r, movil, 'mac') === null, 'lo mismo otra vez no cambia nada');
const atras = { s: { ...movil.s, bestFrac: 0.1, bestCfi: 'x', stats: aqui.stats }, updated: 50 };
ok(E.aplicaRemoto(aqui, atras, 'mac') === null, 'algo más viejo y sin más lejos ni más tiempo: se ignora');
const mismoMomento = { s: { cfi: 'zz', fraction: 0.1, bestFrac: 0, stats: { dev: { movil: { total: 700000, days: { d1: 700000 } } }, total: 700000, days: { d1: 700000 } } }, updated: 50 };
const r2 = E.aplicaRemoto(aqui, mismoMomento, 'mac');
ok(r2 && r2.cfi === 'aqui' && r2.stats.total === 1300000, 'más viejo pero con más tiempo leído: se aplica el tiempo y se queda donde estás');
ok(E.aplicaRemoto(null, { s: { cfi: 'a', fraction: 0.4, bestFrac: 0.4, bestCfi: 'a' }, updated: 5 }, 'mac').cfi === 'a', 'un libro que no estaba aquí: se toma tal cual');
ok(E.aplicaRemoto(aqui, null, 'mac') === null && E.aplicaRemoto(aqui, {}, 'mac') === null, 'sin nada que aplicar');

// Lo que queda por leer a tu ritmo (los mismos casos que prueba el móvil)
for (const c of require('./casos/lectura.json').queda) {
  const r = E.quedaPorLeer(c.st, c.hoy);
  ok(JSON.stringify(r) === JSON.stringify(c.esperado), `queda: ${c.nombre}`);
}

console.log(fallos ? `\n${fallos} fallos` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
