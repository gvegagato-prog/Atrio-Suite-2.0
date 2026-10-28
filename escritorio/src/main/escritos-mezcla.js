'use strict';
// Juntar dos versiones de un escrito que salen de la misma (la «base»): lo que solo cambió en una se queda, y solo cuando
// las dos cambian lo mismo (el mismo campo, el mismo párrafo, la misma palabra) manda la preferida. Igual que
// EscribirMezcla.kt del móvil; los dos se prueban con herramientas/casos/mezcla.json.

// Campos de texto largo: se juntan por párrafos
const TEXTOS = new Set(['texto', 'resumen', 'sinopsis', 'nota']);
const FUERA = new Set(['id', 't', 'u', 'pu']);
// Más de tantos párrafos distintos entre dos versiones (ya sin lo igual del principio y del final) se toma como reescrito
const LIMITE = 1500;
const igual = (x, y) => x.length === y.length && x.every((v, i) => v === y[i]);

// Myers: la memoria crece con lo que cambia, no con lo largo que sea el texto
function myers(a, b) {
  const n = a.length; const m = b.length;
  if (!n || !m) return [];
  const max = n + m; const off = max + 1;
  const v = new Int32Array(2 * max + 3);
  const traza = [];
  for (let d = 0; d <= Math.min(max, LIMITE); d++) {
    traza.push(v.slice(off - d - 1, off + d + 2));
    for (let k = -d; k <= d; k += 2) {
      let x = (k === -d || (k !== d && v[off + k - 1] < v[off + k + 1])) ? v[off + k + 1] : v[off + k - 1] + 1;
      let y = x - k;
      while (x < n && y < m && a[x] === b[y]) { x++; y++; }
      v[off + k] = x;
      if (x >= n && y >= m) return atras(traza, n, m);
    }
  }
  return [];
}
function atras(traza, n, m) {
  const out = [];
  let x = n; let y = m;
  for (let d = traza.length - 1; d >= 0; d--) {
    const v = traza[d];
    const at = (k) => v[k + d + 1];
    const k = x - y;
    const pk = (k === -d || (k !== d && at(k - 1) < at(k + 1))) ? k + 1 : k - 1;
    const px = at(pk); const py = px - pk;
    while (x > px && y > py && x > 0 && y > 0) { out.push([x - 1, y - 1]); x--; y--; }
    if (d > 0) { x = px; y = py; }
  }
  return out.reverse();
}

/** Índices emparejados de lo común a a y b, en orden (lo igual al principio y al final se aparta antes). */
function comunes(a, b) {
  let ini = 0;
  while (ini < a.length && ini < b.length && a[ini] === b[ini]) ini++;
  let fa = a.length; let fb = b.length;
  while (fa > ini && fb > ini && a[fa - 1] === b[fb - 1]) { fa--; fb--; }
  const out = [];
  for (let i = 0; i < ini; i++) out.push([i, i]);
  for (const [i, j] of myers(a.slice(ini, fa), b.slice(ini, fb))) out.push([ini + i, ini + j]);
  for (let k = 0; k < a.length - fa; k++) out.push([fa + k, fb + k]);
  return out;
}

// (los espacios, como en el móvil: solo los de siempre)
const PIEZA = /[ \t\n\x0B\f\r]+|[\p{L}\p{N}]+|[^ \t\n\x0B\f\r\p{L}\p{N}]/gu;
const piezas = (s) => String(s).match(PIEZA) || [];

// Un mismo párrafo cambiado en los dos: se junta por palabras si los cambios no se tocan (si no, null)
function porPalabras(a, o, t) {
  if (o === t || o === a) return t;
  if (t === a) return o;
  const [p, ch] = junta3(piezas(a), piezas(o), piezas(t), true, false, false);
  return ch === 0 ? p.join('') : null;
}

function junta3(a, o, t, pref, lineas, ambos) {
  const mo = new Map(comunes(a, o)); const mt = new Map(comunes(a, t));
  const out = [];
  let choques = 0;
  let pa = -1; let po = -1; let pt = -1;
  const empieza = (x, c) => c.length > 0 && x.length > c.length && igual(x.slice(0, c.length), c);
  const acaba = (x, c) => c.length > 0 && x.length > c.length && igual(x.slice(x.length - c.length), c);
  const trozo = (ia, io, it) => {
    const ca = a.slice(pa + 1, ia); const co = o.slice(po + 1, io); const ct = t.slice(pt + 1, it);
    const [pri, seg] = pref ? [co, ct] : [ct, co];
    if (igual(co, ca)) out.push(...ct);
    else if (igual(ct, ca)) out.push(...co);
    else if (igual(co, ct)) out.push(...co);
    // Los dos añaden en el mismo sitio: párrafos nuevos se quedan los dos; palabras, no (chocan)
    else if (!ca.length && lineas) out.push(...pri, ...seg);
    // Uno solo añadió detrás (o delante) de lo que había y el otro lo cambió: lo cambiado y lo añadido
    else if (empieza(ct, ca)) out.push(...co, ...ct.slice(ca.length));
    else if (acaba(ct, ca)) out.push(...ct.slice(0, ct.length - ca.length), ...co);
    else if (empieza(co, ca)) out.push(...ct, ...co.slice(ca.length));
    else if (acaba(co, ca)) out.push(...co.slice(0, co.length - ca.length), ...ct);
    // Los mismos párrafos cambiados en los dos: se juntan por palabras, párrafo a párrafo
    else if (lineas && ca.length === co.length && ca.length === ct.length) {
      for (let j = 0; j < ca.length; j++) {
        const p = porPalabras(ca[j], co[j], ct[j]);
        if (p != null) out.push(p);
        else if (ambos) { out.push(pri[j]); if (seg[j] !== pri[j]) out.push(seg[j]); }
        else { choques++; out.push(pri[j]); }
      }
    } else if (ambos) out.push(...pri, ...seg.filter((x) => !pri.includes(x)));
    else { choques++; out.push(...pri); }
  };
  for (let i = 0; i < a.length; i++) {
    const io = mo.get(i); const it = mt.get(i);
    if (io == null || it == null || io <= po || it <= pt) continue;
    trozo(i, io, it);
    out.push(a[i]);
    pa = i; po = io; pt = it;
  }
  trozo(a.length, o.length, t.length);
  return [out, choques];
}

/**
 * Mezcla a tres bandas de un texto por párrafos (y por palabras dentro de un párrafo). Con choque, de ese trozo se queda el
 * preferido, o los dos seguidos si «ambos» (en una nota no se pierde nada; en una escena lo otro se guarda como borrador).
 */
function texto(base, nuestro, suyo, prefiereNuestro, ambos = false) {
  if (nuestro === suyo) return { texto: nuestro, choques: 0 };
  if (nuestro === base) return { texto: suyo, choques: 0 };
  if (suyo === base) return { texto: nuestro, choques: 0 };
  const [p, choques] = junta3(String(base).split('\n'), String(nuestro).split('\n'), String(suyo).split('\n'), prefiereNuestro, true, ambos);
  return { texto: p.join('\n'), choques };
}

/** Texto canónico de un valor (claves en orden) para comparar. */
function canon(v) {
  if (v === null || v === undefined) return 'null';
  if (Array.isArray(v)) return `[${v.map(canon).join(',')}]`;
  if (typeof v === 'object') return `{${Object.keys(v).filter((k) => v[k] !== undefined).sort().map((k) => `${JSON.stringify(k)}:${canon(v[k])}`).join(',')}}`;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return JSON.stringify(String(v));
}

/** El contenido de un documento, sin sus horas («u», «pu»), en texto canónico. */
const contenido = (o) => { const { u, pu, ...r } = o || {}; return canon(r); };

/** Junta dos versiones de un documento a partir de base (null si no se tiene: entonces cada campo distinto choca). */
function docs(base, nuestro, suyo, prefiereNuestro, palabras) {
  const out = {};
  let choques = 0;
  const chocan = new Set();
  const tipo = nuestro.t || suyo.t;
  const claves = [...new Set([...Object.keys(nuestro), ...Object.keys(suyo), ...Object.keys(base || {})])].sort();
  for (const k of claves) {
    if (FUERA.has(k) || (tipo === 'e' && k === 'palabras')) continue;
    const o = nuestro[k]; const t = suyo[k]; const a = base ? base[k] : undefined;
    const co = canon(o); const ct = canon(t); const ca = base ? canon(a) : null;
    let v;
    if (co === ct) v = o;
    else if (ca != null && co === ca) v = t;
    else if (ca != null && ct === ca) v = o;
    else if (TEXTOS.has(k) && (typeof o === 'string' || o == null) && (typeof t === 'string' || t == null)) {
      const m = texto(typeof a === 'string' ? a : '', o || '', t || '', prefiereNuestro, !(tipo === 'e' && k === 'texto'));
      if (m.choques > 0) { choques += m.choques; chocan.add(k); }
      v = m.texto;
    } else { choques++; chocan.add(k); v = prefiereNuestro ? o : t; }
    if (v !== undefined && v !== null) out[k] = v;
  }
  out.id = nuestro.id || suyo.id; out.t = tipo;
  if (tipo === 'e' && palabras) out.palabras = palabras(out.texto || '');
  return { o: out, choques, chocan };
}

module.exports = { texto, docs, canon, contenido, comunes, TEXTOS };
