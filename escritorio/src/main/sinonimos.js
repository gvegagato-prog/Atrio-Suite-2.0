'use strict';
// Sinónimos del español para el editor de Escribir: el tesauro libre de RLA-ES (th_es_v2.dat, LGPL 2.1, sin cambios; ver
// movil/app/src/main/assets/sinonimos/LEEME.txt). Es el mismo archivo que usa el móvil (Sinonimos.kt) y se busca igual:
// la palabra, o su singular / masculino, y lo encontrado se adapta (plural, femenino, mayúscula) a la palabra escrita.
const fs = require('fs');
const path = require('path');

let indice = null; // palabra → número de línea
let lineas = null;

function dondeEsta() {
  const c = [
    process.resourcesPath && path.join(process.resourcesPath, 'sinonimos', 'th_es_v2.dat'),
    path.join(__dirname, '..', '..', '..', 'movil', 'app', 'src', 'main', 'assets', 'sinonimos', 'th_es_v2.dat'),
  ].filter(Boolean);
  return c.find((f) => { try { return fs.statSync(f).isFile(); } catch { return false; } }) || null;
}

/** Lee el tesauro (de un archivo o de un texto ya leído). Devuelve cuántas palabras tiene. */
function carga(t) {
  if (t == null) { const f = dondeEsta(); if (!f) return 0; t = fs.readFileSync(f, 'latin1'); }
  indice = new Map();
  lineas = t.split('\n');
  for (let i = 1; i < lineas.length;) {
    const l = lineas[i];
    const bar = l.lastIndexOf('|');
    const n = bar > 0 ? parseInt(l.slice(bar + 1), 10) : NaN;
    if (!(n > 0)) { i++; continue; }
    const clave = l.slice(0, bar).toLowerCase();
    if (!indice.has(clave)) indice.set(clave, i);
    i += n + 1;
  }
  return indice.size;
}

function grupos(i) {
  const l = lineas[i];
  const n = parseInt(l.slice(l.lastIndexOf('|') + 1), 10);
  const out = [];
  for (let k = 1; k <= n && i + k < lineas.length; k++) out.push(lineas[i + k].split('|').slice(1).map((s) => s.trim()).filter(Boolean));
  return out;
}

const mayus = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const SIN_TILDE = { á: 'a', é: 'e', í: 'i', ó: 'o', ú: 'u' };
const plural = (s) => {
  if (/[aeiouáéó]$/.test(s)) return `${s}s`;
  if (/z$/.test(s)) return `${s.slice(0, -1)}ces`;
  if (/[áéíóú][ns]$/.test(s)) return `${s.slice(0, -2)}${SIN_TILDE[s[s.length - 2]]}${s[s.length - 1]}es`; // canción → canciones
  if (/s$/.test(s)) return s;
  return `${s}es`;
};
const femenino = (s) => (/o$/.test(s) ? `${s.slice(0, -1)}a` : s);

/** Los sinónimos de una palabra, en grupos (cada grupo, un sentido), ya adaptados a cómo está escrita. */
function busca(palabra) {
  if (!indice) carga();
  if (!indice || !indice.size) return [];
  const w = String(palabra || '').trim();
  const p = w.toLowerCase();
  if (!p) return [];
  const cands = [[p, false, false]];
  if (p.endsWith('ces')) cands.push([`${p.slice(0, -3)}z`, true, false]); // veloces → veloz
  if (p.endsWith('iones')) cands.push([`${p.slice(0, -5)}ión`, true, false]); // canciones → canción
  if (p.endsWith('es')) cands.push([p.slice(0, -2), true, false]);
  if (p.endsWith('s')) cands.push([p.slice(0, -1), true, false]);
  if (p.endsWith('as')) cands.push([`${p.slice(0, -2)}o`, true, true]);
  if (p.endsWith('a')) cands.push([`${p.slice(0, -1)}o`, false, true]);
  for (const [c, pl, fe] of cands) {
    const i = indice.get(c);
    if (i == null) continue;
    const visto = new Set([p, c]);
    const out = [];
    for (const g of grupos(i)) {
      // (buscando por el singular o el masculino, los sentidos de adverbio no encajan)
      if ((pl || fe) && g.some((x) => /mente$/.test(x))) continue;
      const r = [];
      for (let s of g) {
        if (!s.includes(' ')) { if (fe) s = femenino(s); if (pl) s = plural(s); }
        if (w[0] && w[0] !== w[0].toLowerCase()) s = mayus(s);
        const k = s.toLowerCase();
        if (visto.has(k)) continue;
        visto.add(k); r.push(s);
      }
      if (r.length) out.push(r);
    }
    return out;
  }
  return [];
}

module.exports = { carga, busca, dondeEsta };
