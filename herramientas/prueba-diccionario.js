'use strict';
// Diccionario del lector: las acepciones se sacan del texto del Wikcionario y las formas («faldas») llevan a su palabra.
const assert = require('assert');
const D = require('../escritorio/src/main/diccionario');

const EXT = {
  casa: '== Español ==\n=== Sustantivo ===\n1 Edificio para habitar.\n2 Linaje.\nSinónimos: hogar\n== Catalán ==\n1 casa en catalán.',
  falda: '== Español ==\n=== Sustantivo femenino ===\n1 Prenda de vestir femenina.\n2\nParte baja de un monte.',
  faldas: '== Español ==\n=== Forma ===\n1 Forma del plural de falda.',
};
const fakeFetch = async (u) => {
  const t = decodeURIComponent(/titles=([^&]+)/.exec(u)[1]);
  const ex = EXT[t];
  return { ok: true, status: 200, headers: { get: () => null }, json: async () => ({ query: { pages: { 1: ex ? { extract: ex } : { missing: '' } } } }) };
};

(async () => {
  assert.deepStrictEqual(D.parseAll(EXT.casa), ['Edificio para habitar.', 'Linaje.']);
  assert.deepStrictEqual(D.parseAll(EXT.casa, 'Catalán'), ['casa en catalán.']);
  assert.strictEqual(D.lemmaDe('Forma del plural de falda.'), 'falda');
  assert.strictEqual(D.lemmaDe('Prenda de vestir femenina.'), null);
  assert.deepStrictEqual(D.candidatas('Casas').slice(0, 3), ['casas', 'Casas', 'casa']);
  assert.ok(D.candidatas('dándole').includes('dando'));
  let d = await D.define('casa', fakeFetch);
  assert.deepStrictEqual(d, { palabra: 'casa', defs: ['Edificio para habitar.', 'Linaje.'], nota: '' });
  d = await D.define('faldas', fakeFetch);
  assert.strictEqual(d.palabra, 'falda'); assert.strictEqual(d.nota, 'Forma del plural de falda.');
  assert.strictEqual(d.defs[0], 'Prenda de vestir femenina.');
  d = await D.define('zzzz', fakeFetch);
  assert.deepStrictEqual(d.defs, []);
  console.log('Todo bien (diccionario)');
})().catch((e) => { console.error(e); process.exit(1); });
