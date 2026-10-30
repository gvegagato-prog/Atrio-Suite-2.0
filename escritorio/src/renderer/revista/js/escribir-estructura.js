'use strict';
/* global window, module */
// La estructura de una obra en Escribir (la misma que EscribirEstructura.kt del móvil): partes con sus capítulos, el
// orden del libro, el paso de la «parte» antigua (un texto en cada capítulo) a partes de verdad, lo vinculado a una parte
// y dónde va cada cosa en la línea temporal. Funciones puras: se prueban con node (herramientas/prueba-escribir-estructura.js)
// con los casos de herramientas/casos/estructura.json, los mismos que el móvil (EscribirEstructuraTest.kt).
//
// Documentos (ver docs/escribir.md): parte «a» { titulo, orden, color, resumen }; el capítulo lleva «aid» (su parte, o nada
// si va suelto). Las partes y los capítulos sueltos comparten el orden del manuscrito; los capítulos de una parte se ordenan
// entre ellos. Rama de la línea «r» { titulo, color, desde, hasta? }; hito «h» con «rama» (vacío: la línea principal).
(function (raiz) {
  const S = {};
  const num = (x) => { const n = Number(x); return Number.isFinite(n) ? n : 0; };
  const porOrden = (a, b) => (num(a.orden) - num(b.orden)) || (num(a.u) - num(b.u)) || (String(a.id) < String(b.id) ? -1 : String(a.id) > String(b.id) ? 1 : 0);

  // ---- Un id que sale igual en todos los dispositivos (FNV-1a de 32 bits dos veces, sobre las unidades UTF-16)
  const fnv = (s, semilla) => {
    let h = semilla >>> 0;
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) >>> 0; }
    return h >>> 0;
  };
  const hex = (n) => n.toString(16).padStart(8, '0');
  const normaNombre = (t) => String(t || '').trim().replace(/\s+/g, ' ').toLowerCase();
  /** El id de la parte «nombre» de la obra pid al pasar las partes antiguas (texto) a partes de verdad. */
  S.idParte = (pid, nombre) => { const k = `${pid}|${normaNombre(nombre)}`; return `a_${hex(fnv(k, 2166136261))}${hex(fnv(k, 3141592653)).slice(0, 4)}`; };

  /**
   * Lo que hay que hacer para pasar la «parte» de texto de los capítulos a partes de verdad: las partes que hay que crear
   * (si no existen ya) y a qué parte va cada capítulo. Cada parte toma el orden de su primer capítulo, así el manuscrito
   * queda igual. Los capítulos que ya tienen parte (aid) no se tocan.
   */
  S.planMigracion = (pid, caps, partesExistentes) => {
    const hay = new Set((partesExistentes || []).map((p) => p.id));
    const nuevas = new Map();
    const asigna = [];
    for (const c of [...(caps || [])].sort(porOrden)) {
      const t = String(c.parte || '').trim();
      if (!t || c.aid) continue;
      const id = S.idParte(pid, t);
      if (!hay.has(id) && !nuevas.has(id)) nuevas.set(id, { id, titulo: t.replace(/\s+/g, ' '), orden: num(c.orden) });
      asigna.push({ cid: c.id, aid: id });
    }
    return { partes: [...nuevas.values()], asigna };
  };

  /**
   * El manuscrito: lo de arriba (partes y capítulos sueltos, en su orden) y, dentro de cada parte, sus capítulos.
   * Un capítulo cuya parte no existe (borrada en otro sitio) va suelto. Devuelve { items: [{ tipo:'parte', parte, caps } |
   * { tipo:'cap', cap }], libro: [capítulos en el orden del libro] }.
   */
  S.estructura = (caps, partes) => {
    const ps = [...(partes || [])].sort(porOrden);
    const ids = new Set(ps.map((p) => p.id));
    const de = new Map(ps.map((p) => [p.id, []]));
    const sueltos = [];
    for (const c of caps || []) { if (c.aid && ids.has(c.aid)) de.get(c.aid).push(c); else sueltos.push(c); }
    for (const l of de.values()) l.sort(porOrden);
    const arriba = [...ps.map((p) => ({ tipo: 'parte', parte: p, orden: num(p.orden), u: num(p.u), id: p.id, caps: de.get(p.id) })),
      ...sueltos.map((c) => ({ tipo: 'cap', cap: c, orden: num(c.orden), u: num(c.u), id: c.id }))].sort(porOrden);
    const items = arriba.map((x) => (x.tipo === 'parte' ? { tipo: 'parte', parte: x.parte, caps: x.caps } : { tipo: 'cap', cap: x.cap }));
    const libro = items.flatMap((x) => (x.tipo === 'parte' ? x.caps : [x.cap]));
    return { items, libro };
  };

  /** Las notas y fichas vinculadas a un documento (una parte o un capítulo): las que llevan su id en «vin». */
  S.vinculados = (id, docs) => (docs || []).filter((d) => Array.isArray(d.vin) && d.vin.includes(id));

  /**
   * La línea temporal vertical: una fila por cosa (cada suceso, y el principio y el final de cada rama), de arriba abajo por
   * su orden (con el mismo orden: primero el suceso, luego la rama que sale de él y luego la que vuelve), y en qué columna va cada línea (0 = la principal; las ramas, a su derecha, sin pisarse). Una rama empieza en
   * «desde» (o en su primer suceso, si es antes) y acaba en «hasta» (vuelve a la principal) o, si no tiene, en su último
   * suceso. Devuelve { filas: [{ tipo: 'hito'|'inicio'|'fin', id, col, activas: [columnas con línea en esta fila] }], columnas }.
   */
  S.linea = (hitos, ramas) => {
    const rs = (ramas || []).map((r) => ({ ...r }));
    const porRama = new Map(rs.map((r) => [r.id, []]));
    for (const x of hitos || []) if (x.rama && porRama.has(x.rama)) porRama.get(x.rama).push(x);
    const filas = [];
    const tramos = [];
    for (const r of rs) {
      const hs = porRama.get(r.id).map((x) => num(x.orden));
      const ini = Math.min(num(r.desde), ...hs);
      const une = r.hasta != null && r.hasta !== '' && Number.isFinite(Number(r.hasta));
      const fin = une ? Math.max(num(r.hasta), ...hs) : (hs.length ? Math.max(...hs) : ini);
      tramos.push({ id: r.id, ini, fin, une });
      filas.push({ tipo: 'inicio', id: r.id, orden: ini, k: 1 });
      if (une) filas.push({ tipo: 'fin', id: r.id, orden: fin, k: 2 });
    }
    for (const x of hitos || []) filas.push({ tipo: 'hito', id: x.id, orden: num(x.orden), k: 0, rama: x.rama && porRama.has(x.rama) ? x.rama : '' });
    // (con el mismo orden: el suceso, luego la rama que nace de él y luego la que vuelve; pero los sucesos de una rama van
    // siempre después de que nazca)
    const k = (f, otro) => (f.tipo === 'hito' && otro.tipo === 'inicio' && f.rama === otro.id ? 2 : f.k);
    filas.sort((a, b) => (a.orden - b.orden) || (k(a, b) - k(b, a)) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
    // Columnas: cada rama en la primera que esté libre desde su principio hasta su final
    const col = new Map();
    const libreDesde = [];
    for (const t of [...tramos].sort((a, b) => (a.ini - b.ini) || (a.id < b.id ? -1 : 1))) {
      let c = 0;
      while (c < libreDesde.length && !(libreDesde[c] < t.ini)) c++;
      libreDesde[c] = t.fin;
      col.set(t.id, c + 1);
    }
    // Qué filas cruza cada rama: desde la fila en que nace hasta la de su vuelta (o la de su último suceso)
    const filasDe = new Map(tramos.map((t) => {
      const a = filas.findIndex((f) => f.tipo === 'inicio' && f.id === t.id);
      let b = a;
      filas.forEach((f, i) => { if ((f.tipo === 'fin' && f.id === t.id) || (f.tipo === 'hito' && f.rama === t.id)) b = Math.max(b, i); });
      return [t.id, [a, b]];
    }));
    const tramoDe = new Map(tramos.map((t) => [t.id, t]));
    const out = filas.map((f, i) => {
      const c = f.tipo === 'hito' ? (f.rama ? col.get(f.rama) : 0) : col.get(f.id);
      const activas = [0, ...tramos.filter((t) => { const [a, b] = filasDe.get(t.id); return i >= a && i <= b; }).map((t) => col.get(t.id))].filter((v, j, l) => l.indexOf(v) === j).sort((x, y) => x - y);
      return { tipo: f.tipo, id: f.id, col: c, activas };
    });
    return { filas: out, columnas: 1 + libreDesde.length, tramos: Object.fromEntries([...tramoDe].map(([k, t]) => [k, { ini: t.ini, fin: t.fin, une: t.une, col: col.get(k) }])) };
  };

  /** El orden para poner algo entre a y b (cualquiera de los dos puede faltar). */
  S.entre = (a, b) => (a == null && b == null ? 1000 : a == null ? b - 1000 : b == null ? a + 1000 : (a + b) / 2);

  if (typeof module !== 'undefined' && module.exports) module.exports = S;
  else raiz.RV = Object.assign(raiz.RV || {}, { EST: S });
})(typeof window !== 'undefined' ? window : globalThis);
