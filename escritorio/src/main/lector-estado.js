'use strict';
// El estado de lo leído de cada libro en el ordenador, con la misma lógica que el móvil (ReadStats y Store.applyLecturas /
// saveLectura de Data.kt, y ReaderActivity.empiezaEn). Funciones puras: se prueban con node (herramientas/prueba-lector.js)
// con los casos de herramientas/casos/lectura.json, los mismos que prueba el móvil (EncuadreTest.kt).
//
// Lo que viaja con la cuenta (lista «lectura»): { id, s, updated }, donde s es el estado del libro SIN la dirección del
// archivo de este dispositivo: { cfi, fraction, label, read, bestCfi, bestFrac, bestLabel, settings, notes, marks, stats }.
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const clon = (o) => JSON.parse(JSON.stringify(o === undefined ? null : o));

const SEGUIDO_MS = 5 * 60 * 1000;

// ---- ReadStats: el tiempo leído va por dispositivo (dev: { <dispositivo>: { total, days, best } }) ----
function dev(st, claveAntigua) {
  if (!st) return {};
  if (st.dev && typeof st.dev === 'object') return st.dev;
  // Antes del reparto por dispositivo: todo el tiempo era de un solo cubo
  const total = num(st.total);
  return total > 0 ? { [claveAntigua]: { total, days: st.days || {} } } : {};
}

/** total y days = la suma de todos los dispositivos; best (la racha más larga de un día) = la mejor, sin sumar. */
function derivado(d) {
  let total = 0; const days = {}; const best = {};
  for (const b of Object.values(d)) {
    total += num(b.total);
    for (const [k, v] of Object.entries(b.days || {})) days[k] = num(days[k]) + num(v);
    for (const [k, v] of Object.entries(b.best || {})) best[k] = Math.max(num(best[k]), num(v));
  }
  const out = { dev: d, total, days };
  if (Object.keys(best).length) out.best = best;
  return out;
}

/** Une dos estados de tiempo. aKey/bKey: de qué dispositivo es un tiempo antiguo sin repartir (el de aquí, o «legacy»). */
function mezclaStats(a, b, aKey, bKey = 'legacy') {
  const da = dev(a, aKey); const db = dev(b, bKey);
  const hayReal = !!((a && a.dev) || (b && b.dev));
  const out = {};
  for (const src of [da, db]) {
    for (const id of Object.keys(src)) {
      if (hayReal && id === 'legacy') continue;   // lo antiguo ya está dentro del cubo de su dispositivo
      const x = src[id];
      const cur = out[id];
      if (!cur) { out[id] = clon(x); continue; }
      const days = clon(cur.days || {});
      for (const [k, v] of Object.entries(x.days || {})) days[k] = Math.max(num(days[k]), num(v));
      const best = clon(cur.best || {});
      for (const [k, v] of Object.entries(x.best || {})) best[k] = Math.max(num(best[k]), num(v));
      out[id] = { total: Math.max(num(cur.total), num(x.total)), days };
      if (Object.keys(best).length) out[id].best = best;
    }
  }
  return derivado(out);
}

/** ¿Cuenta ese día como leído? Hace falta una racha de 5 minutos seguidos (los días antiguos, sin racha, con 5 en total). */
function cuentaDia(st, dia) {
  if (!st) return false;
  const total = num((st.days || {})[dia]);
  if (total <= 0) return false;
  return st.best && dia in st.best ? num(st.best[dia]) >= SEGUIDO_MS : total >= SEGUIDO_MS;
}
const totalDe = (st) => num(st && st.total);

/** Dónde se abre el libro: lo más lejos que has llegado en cualquier dispositivo (o, si no hay, donde lo dejaste aquí). */
function empiezaEn(o) {
  const best = num(o.bestFrac);
  return best > 0 && best >= num(o.fraction) && String(o.bestCfi || '').trim() ? [String(o.bestCfi), best] : [String(o.cfi || ''), num(o.fraction)];
}

/** Lo que sube a la cuenta: el estado sin lo propio de este dispositivo (archivo, nombre, fecha de adjuntar, brillo y tamaño). */
function paraLaCuenta(st) {
  const s = clon(st);
  for (const k of ['uri', 'name', 'added', 'syncAt']) delete s[k];
  if (s.settings) { delete s.settings.brightness; delete s.settings.size; }
  return s;
}

/**
 * Lo que llega de otro dispositivo (remoto: { id, s, updated }) sobre lo de aquí (cur): devuelve el estado nuevo o null si no
 * hay nada más reciente. Por dónde vas aquí se queda, pero si en otro dispositivo has llegado más lejos, se pone ahí; el
 * tiempo leído se suma; el archivo, el brillo y el tamaño de letra son de cada dispositivo.
 */
function aplicaRemoto(cur, remoto, dispositivo) {
  cur = cur || {};
  const s = clon(remoto && remoto.s);
  if (!s) return null;
  if (num(remoto.updated) <= num(cur.syncAt) && num(s.bestFrac) <= num(cur.bestFrac) && totalDe(s.stats) <= totalDe(cur.stats)) return null;
  s.stats = mezclaStats(cur.stats, s.stats, dispositivo, 'legacy');
  for (const k of ['cfi', 'fraction', 'label', 'read']) if (k in cur) s[k] = cur[k];
  if (num(s.bestFrac) > num(cur.bestFrac) && String(s.bestCfi || '').trim()) { s.cfi = s.bestCfi; s.fraction = s.bestFrac; s.label = s.bestLabel || ''; }
  for (const k of ['uri', 'name', 'added']) if (k in cur) s[k] = cur[k];
  const loc = cur.settings;
  if (loc && s.settings) for (const k of ['brightness', 'size']) if (k in loc) s.settings[k] = loc[k];
  s.syncAt = num(remoto.updated);
  return s;
}

// ---- Lo que queda por leer, a tu ritmo (quedaPorLeer del móvil) ----
const DIA_MS = 86400000;
const iso = (t) => new Date(t).toISOString().slice(0, 10);
const suma = (dia, n) => iso(Date.parse(`${dia}T00:00:00Z`) + n * DIA_MS);

/**
 * Lo que te queda de un libro a tu ritmo (minutos), sabiendo lo que has tardado en leer lo que llevas, y el día (AAAA-MM-DD) en que
 * lo acabarías si sigues como en las dos últimas semanas (null si estas semanas no lo has tocado). null si aún no se puede saber.
 * hoy: AAAA-MM-DD.
 */
function quedaPorLeer(st, hoy) {
  const f = num(st && st.fraction);
  const stats = st && st.stats;
  if (!stats) return null;
  const total = num(stats.total);
  if (f < 0.03 || f >= 0.99 || total < 10 * 60000) return null;
  const queda = Math.floor(total * (1 - f) / f);
  let reciente = 0;
  for (let d = 0; d < 14; d++) reciente += num((stats.days || {})[suma(hoy, -d)]);
  const porDia = reciente / 14;
  const fin = porDia >= 60000 ? suma(hoy, Math.min(3650, Math.max(0, Math.ceil(queda / porDia)))) : null;
  return { min: Math.floor(queda / 60000), fin };
}

module.exports = { quedaPorLeer, mezclaStats, derivado, cuentaDia, totalDe, empiezaEn, paraLaCuenta, aplicaRemoto, SEGUIDO_MS };
