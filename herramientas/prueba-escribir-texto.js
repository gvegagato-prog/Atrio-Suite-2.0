#!/usr/bin/env node
'use strict';
// La lógica de texto de «Escribir» en el PC (escribir-texto.js): los mismos casos que EscribirTextoTest.kt del móvil.
const assert = require('assert');
const ET = require('../escritorio/src/renderer/revista/js/escribir-texto.js');
let fallos = 0;
const t = (nombre, f) => { try { f(); console.log('  ✔ ' + nombre); } catch (e) { fallos++; console.log('  ✘ ' + nombre + '\n    ' + e.message); } };
const v = (text, a = text.length, b = a) => ({ text, a, b });

t('envuelve con negrita y cursiva', () => {
  assert.deepStrictEqual(ET.envuelve(v('hola mundo ya', 5, 10), '**'), v('hola **mundo** ya', 7, 12));
  assert.strictEqual(ET.envuelve(v('hola **mundo** ya', 7, 12), '**').text, 'hola mundo ya');
  assert.strictEqual(ET.envuelve(v('hola **mundo** ya', 5, 14), '**').text, 'hola mundo ya');
  const vacio = ET.envuelve(v('a  b', 2), '**');
  assert.strictEqual(vacio.text, 'a **** b'); assert.strictEqual(vacio.a, 4);
  assert.strictEqual(ET.envuelve(v('*a* b', 4, 5), '**').text, '*a* **b**');
  assert.strictEqual(ET.envuelve(v('hola', 0, 4), '*').text, '*hola*');
  assert.strictEqual(ET.envuelve(v('**hola**', 2, 6), '*').text, '***hola***');
  assert.strictEqual(ET.envuelve(v('***hola***', 3, 7), '*').text, '**hola**');
});
t('prefijos de línea', () => {
  assert.strictEqual(ET.prefijoLinea(v('Capítulo\ntexto', 3), '# ').text, '# Capítulo\ntexto');
  assert.strictEqual(ET.prefijoLinea(v('# Capítulo\ntexto', 5), '# ').text, 'Capítulo\ntexto');
  assert.strictEqual(ET.prefijoLinea(v('# Capítulo', 4), '## ').text, '## Capítulo');
  assert.strictEqual(ET.prefijoLinea(v('> dicho', 3), '# ').text, '# dicho');
  assert.strictEqual(ET.prefijoLinea(v('uno\ndos\ntres', 1, 6), '> ').text, '> uno\n> dos\ntres');
  assert.strictEqual(ET.prefijoLinea(v('uno\ndos\ntres', 5), '> ').text, 'uno\n> dos\ntres');
});
t('salto, comillas y raya', () => {
  assert.strictEqual(ET.salto(v('fin\nOtra', 4)).text, 'fin\n\n***\n\nOtra');
  assert.strictEqual(ET.salto(v('Otra', 0)).text, '***\n\nOtra');
  assert.strictEqual(ET.comillas(v('dicho', 0, 5)).text, '«dicho»');
  assert.strictEqual(ET.comillas(v('', 0)).a, 1);
  assert.strictEqual(ET.inserta(v('hola  adiós', 5), '—').text, 'hola — adiós');
});
t('pinta el formato', () => {
  const x = '# Título\nAldara subió la **torre** y *miró*.\n***\n> dicho';
  const segs = ET.segmentos(x, { fichas: /\bAldara\b/g, busca: /torre/gi });
  const en = (i) => segs.find((s) => i >= s.desde && i < s.hasta).cls;
  assert.ok(en(3).includes('h1'), 'título');
  assert.ok(en(x.indexOf('orre')).includes('neg'), 'negrita');
  assert.ok(en(x.indexOf('miró') + 1).includes('cur'), 'cursiva');
  assert.ok(en(x.indexOf('**torre')).includes('m'), 'signo apagado');
  assert.ok(en(x.indexOf('Aldara')).includes('ficha'), 'ficha');
  assert.ok(en(x.indexOf('torre') + 1).includes('busca'), 'búsqueda');
  assert.ok(en(x.indexOf('dicho')).includes('cita'), 'cita');
  assert.ok(en(x.indexOf('***')).includes('salto'), 'salto de escena');
  assert.strictEqual(segs.map((s) => x.slice(s.desde, s.hasta)).join(''), x, 'los segmentos cubren todo el texto');
});
t('deshacer y rehacer', () => {
  const h = new ET.Historial(v(''));
  h.apunta(v('Hola'), 0); h.apunta(v('Hola m'), 100); h.apunta(v('Hola mu'), 200);
  h.apunta(v('Hola mundo, esto sigue después de una pausa'), 5000);
  assert.strictEqual(h.deshace().text, 'Hola mu');
  assert.strictEqual(h.deshace().text, '');
  assert.strictEqual(h.deshace(), null);
  assert.strictEqual(h.rehace().text, 'Hola mu');
  h.apunta(v('otra cosa'), 20000);
  assert.strictEqual(h.puedeRehacer, false);
});
t('comparar y mezclar', () => {
  const p = ET.compara('Uno.\n\nDos.\n\nTres.', 'Uno.\n\nDos cambiado.\n\nTres.\n\nCuatro.');
  assert.strictEqual(p.filter((x) => x.aqui).length, 2);
  assert.strictEqual(ET.mezcla(p, [true, false]), 'Uno.\n\nDos cambiado.\n\nTres.');
  assert.strictEqual(ET.mezcla(p, [false, true]), 'Uno.\n\nDos.\n\nTres.\n\nCuatro.');
  assert.strictEqual(ET.compara('Igual.', 'Igual.').filter((x) => x.aqui).length, 0);
});
const fs = require('fs');
const path = require('path');
t('tipografía automática (casos de herramientas/casos/tipografia.json, los mismos que el móvil)', () => {
  for (const c of JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'tipografia.json'), 'utf8')).casos) {
    const r = ET.tipografia(c.texto, c.cursor, c.ch);
    if (c.esperado == null) assert.strictEqual(r, null, c.nombre);
    else assert.deepStrictEqual(r, { texto: c.esperado, cursor: c.cursorEsperado }, c.nombre);
  }
});
t('la tipografía se deshace sola', () => {
  const h = new ET.Historial(v('Hola'));
  h.apunta(v('Hola -'), 1000); h.apunta(v('Hola --'), 1100); h.corte(); h.apunta(v('Hola —'), 1100); h.corte();
  assert.strictEqual(h.deshace().text, 'Hola --');
  assert.strictEqual(h.deshace().text, 'Hola');
});
t('sinónimos (casos de herramientas/casos/sinonimos.json, los mismos que el móvil)', () => {
  const S = require('../escritorio/src/main/sinonimos');
  assert.ok(S.carga() > 20000, 'el tesauro se encuentra y tiene más de 20.000 palabras');
  for (const c of JSON.parse(fs.readFileSync(path.join(__dirname, 'casos', 'sinonimos.json'), 'utf8')).casos) {
    const r = S.busca(c.palabra);
    assert.deepStrictEqual(r.slice(0, c.grupos.length), c.grupos, c.palabra);
    if (!c.grupos.length) assert.strictEqual(r.length, 0);
  }
});
t('menú del botón derecho: correcciones, sinónimos y copiar', () => {
  const { entradas } = require('../escritorio/src/main/menu-contextual');
  const hecho = [];
  const wc = { replaceMisspelling: (x) => hecho.push(['corrige', x]), replace: (x) => hecho.push(['cambia', x]), session: { addWordToSpellCheckerDictionary: (x) => hecho.push(['añade', x]) } };
  const mal = entradas({ isEditable: true, misspelledWord: 'csa', dictionarySuggestions: ['casa', 'cosa'], selectionText: '', editFlags: { canCut: false, canCopy: false, canPaste: true } }, wc);
  assert.deepStrictEqual(mal.slice(0, 3).map((x) => x.label), ['casa', 'cosa', 'Añadir «csa» al diccionario']);
  mal[0].click(); mal[2].click();
  assert.deepStrictEqual(hecho, [['corrige', 'casa'], ['añade', 'csa']]);
  const sin = entradas({ isEditable: true, misspelledWord: '', selectionText: 'casa', editFlags: { canCut: true, canCopy: true, canPaste: true } }, wc);
  const sub = sin.find((x) => x.label === 'Sinónimos de «casa»');
  assert.ok(sub && sub.submenu.some((x) => x.label === 'vivienda'), 'sinónimos de la palabra elegida');
  sub.submenu.find((x) => x.label === 'vivienda').click();
  assert.deepStrictEqual(hecho[2], ['cambia', 'vivienda']);
  assert.ok(sin.some((x) => x.role === 'paste') && sin.some((x) => x.role === 'cut'), 'cortar, copiar y pegar');
  const leer = entradas({ isEditable: false, selectionText: 'algo', editFlags: {} }, wc);
  assert.deepStrictEqual(leer.map((x) => x.role), ['copy'], 'fuera de un campo, solo copiar');
  assert.strictEqual(entradas({ isEditable: false, selectionText: '', editFlags: {} }, wc).length, 0, 'sin nada, no hay menú');
});
console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
process.exit(fallos ? 1 : 0);
