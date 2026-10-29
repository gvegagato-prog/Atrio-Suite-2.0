'use strict';
// El significado de una palabra del lector, del Wikcionario en español (igual que Diccionario.kt del móvil): las acepciones
// de la sección del idioma y, si la palabra es solo una forma de otra (faldas = «plural de falda»), el significado de la
// palabra de verdad. Las funciones de texto son puras (se prueban con herramientas/prueba-diccionario.js).
const UA = 'Atrio/6 (lector de libros personal; https://github.com/gvegagato-prog/Atrio-Suite-2.0)';

const SALTAR = /^(Sinónimos?|Antónimos?|Hiperónimos?|Hipónimos?|Derivados?|Relacionados?|Véase|Ejemplos?|Uso|Ámbito|Variantes?|Información|Traducciones|Locuciones|Refranes|Parónimos?|Compuestos?|Abreviaturas?)\b.*/;
const FORMA = new RegExp('^(?:Forma\\s+(?:flexiva\\s+|verbal\\s+|sustantiva\\s+|adjetiva\\s+|conjugada\\s+)?(?:(?:del|de la)\\s+)?(?:plural|singular|femenin\\w*|masculin\\w*|diminutiv\\w*|aumentativ\\w*|superlativ\\w*|flexiva|verbal|sustantiva|adjetiva|conjugada|enclítica|apocopada)'
  + '|Plural|Singular|Femenino|Masculino|Superlativo|Diminutivo|Aumentativo|Participio|Gerundio|Infinitivo|Imperativo|(?:Primera|Segunda|Tercera)\\s+persona)'
  + '\\b.{0,120}?\\bde(?:l)?\\s+(?:verbo\\s+|la\\s+|lo\\s+)?([\\p{L}]+)\\.?$', 'u');
const VERBAL = /^(?:Primera|Segunda|Tercera|Participio|Gerundio|Infinitivo|Imperativo|Forma verbal)\b.*/;
const CABECERA = /^(=+)\s*(.+?)\s*=+$/;

const plano = (s) => String(s).normalize('NFD').replace(/\p{Mn}+/gu, '');

/** Si la acepción solo dice «forma del plural de falda», la palabra de la que es forma (falda); si no, null. */
function lemmaDe(def) {
  const m = FORMA.exec(String(def).trim().replace(/\s*\(.*?\)\s*$/, ''));
  return m && m[1].length > 1 ? m[1] : null;
}

/** Todas las acepciones (las líneas que empiezan por número) de la sección del idioma. */
function parseAll(ex, langName = 'Español') {
  const lines = String(ex).split('\n').map((l) => l.replace(/ /g, ' ').replace(/¦/g, '').trim());
  const dos = (l) => { const m = CABECERA.exec(l); return m && m[1].length === 2 ? m : null; };
  let i = lines.findIndex((l) => { const m = dos(l); return m && m[2].toLowerCase().startsWith(langName.toLowerCase()); });
  if (i < 0) i = lines.findIndex((l) => dos(l));   // la primera lengua que haya
  if (i < 0) return [];
  const out = [];
  for (let j = i + 1; j < lines.length && out.length < 14; j++) {
    const l = lines[j];
    const m = CABECERA.exec(l);
    if (m && m[1].length === 2) break;               // empieza otra lengua
    const num = /^(\d+)\s*(.*)$/.exec(l);
    if (num && !m) {
      let rest = num[2].trim();
      // A veces el número va solo (o con una etiqueta de ámbito) y la definición en la línea siguiente
      if (rest.length < 25 && j + 1 < lines.length) {
        const next = lines[j + 1];
        if (next && !CABECERA.test(next) && !/^\d+\b.*/.test(next) && !SALTAR.test(next)) { rest = rest ? `(${rest}) ${next}` : next; j++; }
      }
      if (rest.length >= 3 && !SALTAR.test(rest)) out.push(rest);
    }
  }
  return out;
}

/** Las palabras que se prueban, por orden: tal cual, en minúscula, sin pronombres pegados y en singular. */
function candidatas(palabra) {
  const w = String(palabra).trim();
  const low = w.toLocaleLowerCase('es');
  const out = new Set([low, w]);
  const cl = /^(.+?)((?:me|te|se|nos|os|lo|la|le|los|las|les){1,2})$/.exec(low);
  if (cl) {
    const stem = cl[1];
    if (/.*(ar|er|ir|ando|iendo|yendo)$/.test(plano(stem))) { out.add(plano(stem)); out.add(stem); }
  }
  if (low.length > 4 && low.endsWith('es')) out.add(low.slice(0, -2));
  if (low.length > 3 && low.endsWith('s')) out.add(low.slice(0, -1));
  return [...out];
}

async function extracto(titulo, fetchImpl) {
  const u = `${process.env.ATRIO_WIKT_BASE || 'https://es.wiktionary.org'}/w/api.php?action=query&prop=extracts&explaintext=1&format=json&redirects=1&titles=${encodeURIComponent(titulo)}`;
  let r = await fetchImpl(u, { headers: { 'User-Agent': UA } });
  if (r.status === 429) {   // Wikcionario pide ir más despacio: se espera un momento y se reintenta una vez
    const s = Math.min(4, Math.max(1, Number(r.headers.get('retry-after')) || 2));
    await new Promise((ok) => setTimeout(ok, s * 1000));
    r = await fetchImpl(u, { headers: { 'User-Agent': UA } });
  }
  if (!r.ok) throw new Error(`Wikcionario respondió ${r.status}`);
  const pages = ((await r.json()).query || {}).pages || {};
  const p = pages[Object.keys(pages)[0]] || {};
  return 'missing' in p ? null : (p.extract || null);
}

/** Las acepciones de una palabra (hasta 4) y, si era una forma de otra, de cuál y qué forma. */
async function define(palabra, fetchImpl, langName = 'Español', prof = 0) {
  for (const t of candidatas(palabra).slice(0, prof === 0 ? 5 : 2)) {
    const ex = await extracto(t, fetchImpl);
    if (!ex) continue;
    const todas = parseAll(ex, langName);
    if (!todas.length) continue;
    const reales = todas.filter((d) => lemmaDe(d) === null);
    if (reales.length) return { palabra: t, defs: reales.slice(0, 4), nota: '' };
    if (prof < 2) {
      // Primero las formas de sustantivo o adjetivo; las verbales, al final
      const formas = [...todas].sort((a, b) => (VERBAL.test(a) ? 1 : 0) - (VERBAL.test(b) ? 1 : 0));
      const lemas = [...new Set(formas.map(lemmaDe).filter(Boolean))].filter((l) => l.toLowerCase() !== t.toLowerCase()).slice(0, 2);
      const sacados = [];
      for (const l of lemas) { try { const d = await define(l, fetchImpl, langName, prof + 1); if (d.defs.length) sacados.push(d); } catch { /* */ } }
      if (sacados.length) {
        const defs = sacados.length === 1 ? sacados[0].defs : sacados.flatMap((d) => d.defs.slice(0, 3).map((x) => `(${d.palabra}) ${x}`));
        const nota = formas.find((f) => (lemmaDe(f) || '').toLowerCase() === lemas[0].toLowerCase()) || '';
        return { palabra: sacados[0].palabra, defs, nota };
      }
    }
    return { palabra: t, defs: todas.slice(0, 4), nota: '' };
  }
  return { palabra, defs: [], nota: '' };
}

module.exports = { define, parseAll, lemmaDe, candidatas };
