#!/usr/bin/env node
'use strict';
// Copias de seguridad de Escribir (escritorio/src/main/escritos-copias.js): se hacen solo si algo ha cambiado, se conservan
// las últimas 40, y de una copia se saca una obra como OBRA NUEVA (ids nuevos, enlaces entre capítulos, escenas y fichas
// intactos) sin tocar lo que hay ahora. Uso: node herramientas/prueba-escribir-copias.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const { crear } = require('../escritorio/src/main/escritos');
const { iniciar } = require('../escritorio/src/main/escritos-copias');

let fallos = 0;
const ok = (c, m) => { console.log(`${c ? '  ✔' : '  ✘'} ${m}`); if (!c) fallos++; };
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'copias-'));
const escr = crear({ dir: path.join(tmp, 'escritos'), dev: 'prueba', net: { uid: () => null, db: async () => null } });
let reloj = new Date(2026, 9, 28, 10, 0, 0).getTime();
const cop = iniciar({ escr, carpeta: path.join(tmp, 'copias'), ahora: () => reloj });
cop.parar();

console.log('Sin nada escrito no hay copia');
ok(cop.copia() === null && cop.lista().length === 0, 'no se guarda una copia vacía');

// Una novela con de todo: capítulos, escenas, fichas enlazadas, un hito, un borrador, una nota, una escena borrada
const obra = escr.nuevo('p', '', { titulo: 'La novela', meta: 50000 });
const c1 = escr.nuevo('c', obra.id, { titulo: 'Capítulo 1' });
const c2 = escr.nuevo('c', obra.id, { titulo: 'Capítulo 2' });
const aldara = escr.nuevo('f', obra.id, { tipo: 'personaje', nombre: 'Aldara' });
const torre = escr.nuevo('f', obra.id, { tipo: 'lugar', nombre: 'La torre' });
const e1 = escr.nuevo('e', obra.id, { cid: c1.id, titulo: 'Escena 1', texto: 'Aldara subió a la torre con ñ y «comillas».', pov: aldara.id, lugar: torre.id });
const e2 = escr.nuevo('e', obra.id, { cid: c2.id, titulo: 'Escena 2', texto: 'Segunda escena, con más palabras todavía.' });
const hito = escr.nuevo('h', obra.id, { titulo: 'La subida', eid: e1.id });
const borr = escr.nuevo('b', obra.id, { eid: e1.id, nombre: 'Primer intento', texto: 'Otro texto.' });
escr.nuevo('n', obra.id, { tipo: 'nota', titulo: 'Idea', texto: 'Algo.' });
const borrada = escr.nuevo('e', obra.id, { cid: c2.id, titulo: 'Se borró', texto: 'Esto estaba en la papelera.' });
escr.borra(borrada.id);
escr.nuevo('p', '', { titulo: 'Otra obra' });

console.log('Hacer una copia');
const a = cop.copia();
ok(a && /^copia-2026-10-28_100000-2obras-\d+pal\.json$/.test(a.archivo), `se guarda con fecha, obras y palabras: ${a && a.archivo}`);
const pal = escr.copiaTodo().filter((o) => o.t === 'e' && o.d !== 1).reduce((s, o) => s + (o.palabras || 0), 0);
ok(a && a.palabras === pal && a.obras === 2, `cuenta las palabras (${pal}) y las obras (2)`);
const j = JSON.parse(fs.readFileSync(path.join(tmp, 'copias', a.archivo), 'utf8'));
ok(j.docs.some((o) => o.id === borrada.id && o.d === 1), 'incluye también lo de la papelera');
ok(cop.copia() === null, 'si no ha cambiado nada, no hace otra igual');
const f1 = cop.copia(true);
ok(f1 && f1.archivo === a.archivo, 'se puede forzar (aquí en el mismo segundo: sobrescribe la misma, no suma otra)');
ok(cop.lista().length === 1, 'sigue habiendo una sola');

console.log('Una copia nueva solo si algo cambia');
reloj += 3600 * 1000;
escr.escribe(e2.id, 'Segunda escena, con más palabras todavía y una frase nueva.');
const b = cop.copia();
ok(b && b.archivo !== a.archivo && cop.lista()[0].archivo === b.archivo, 'cambia algo → copia nueva, la más reciente la primera');

console.log('Sacar una obra de una copia como obra nueva');
const obras = cop.obrasDe(a.archivo);
ok(obras.length === 2 && obras.some((o) => o.titulo === 'La novela' && o.escenas === 2), `la copia lleva las obras (${obras.map((o) => o.titulo).join(', ')}) y no cuenta lo borrado`);
const antesTodos = JSON.stringify(escr.copiaTodo().map((o) => o.id).sort());
const r = cop.restaura(a.archivo, obra.id);
ok(r && r.id && r.id !== obra.id && /La novela \(copia del 28\/10\/2026\)/.test(r.titulo) && r.escenas === 2, `queda como obra nueva: ${r && r.titulo}`);
const nueva = escr.get(r.id);
const caps = escr.capitulos(r.id);
const escs = escr.escenasDe(r.id);
ok(nueva && nueva.pid === r.id && caps.length === 2 && escs.length === 2, 'con sus 2 capítulos y 2 escenas');
const fichas = escr.fichas(r.id);
const eNueva = escs.find((x) => x.titulo === 'Escena 1');
ok(eNueva && fichas.some((f) => f.id === eNueva.pov && f.nombre === 'Aldara') && fichas.some((f) => f.id === eNueva.lugar && f.nombre === 'La torre'), 'la escena apunta a las fichas NUEVAS (punto de vista y lugar)');
ok(caps.some((c) => c.id === eNueva.cid), 'y a su capítulo nuevo');
ok(escr.hitos(r.id).length === 1 && escr.hitos(r.id)[0].eid === eNueva.id, 'el hito apunta a la escena nueva');
ok(escr.borradores(eNueva.id).length === 1, 'el borrador va con su escena');
ok(!escr.copiaTodo().some((o) => o.d !== 1 && o.t === 'e' && o.titulo === 'Se borró' && o.pid === r.id), 'lo que ya estaba borrado no vuelve');
const originales = JSON.parse(antesTodos);
ok(originales.every((id) => escr.copiaTodo().some((o) => o.id === id)), 'lo que había antes sigue ahí, intacto');
ok(escr.get(e2.id).texto.endsWith('una frase nueva.'), 'y el texto actual no se ha pisado con el de la copia');
let error = '';
try { cop.restaura('../../etc/passwd', obra.id); } catch (e) { error = e.message; }
ok(/No encuentro/.test(error), 'solo abre archivos de la carpeta de copias');

console.log('Se conservan las últimas 40');
for (let i = 0; i < 45; i++) { reloj += 60000; escr.escribe(e1.id, `Texto ${i}`); cop.copia(); }
ok(cop.lista().length === 40, `quedan 40 (hay ${cop.lista().length})`);
ok(cop.lista()[0].t > cop.lista()[39].t, 'las más nuevas');
fs.rmSync(tmp, { recursive: true, force: true });
console.log(fallos ? `\n${fallos} fallos` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
