'use strict';
/* global window, module */
// Las maquetas de las fichas (Maqueta.kt del móvil): qué casilla va en cada fila, cuánto mide cada una y cómo se
// encuadran las imágenes. Funciones puras, que se prueban con node (herramientas/prueba-maqueta.js) con los casos de
// herramientas/casos/maqueta.json, los mismos que prueba el móvil (EncuadreTest.kt).
//
// Una maqueta viaja con la cuenta en la lista «maquetas»: { id, k, j, updated }, donde k es «v:<tipo>:<id>» (ficha de
// algo terminado), «p:<tipo>:<id>» (ficha de algo en curso) o «tpl:v:<tipo>» / «tpl:p:<tipo>» (la de toda la categoría),
// y j el texto JSON de las filas: [{ s: línea (0–1), a: alto en px (0 = el que pida lo de dentro), c: [casilla, casilla] }].
// Una casilla: { t: tipo, h: título, x: texto, i: imagen, f: foto entera, z: zoom, ox, oy: desplazamiento (fracción del
// tamaño de la casilla), sh: forma antigua, as: ancho/alto que dejó el encuadre }.
(function (raiz) {
  const M = {};
  const num = (x, d = 0) => { const n = Number(x); return Number.isFinite(n) ? n : d; };
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  // La maqueta de una ficha: la suya o, si no tiene, la de su categoría (la más reciente de cada una). null: la de siempre.
  // t: 'g' (terminado) o 'p' (en curso).
  M.resolver = (lista, t, kindId, id) => {
    const pref = t === 'p' ? 'p' : 'v';
    const leer = (k) => {
      const o = (Array.isArray(lista) ? lista : []).filter((x) => x && x.k === k && String(x.j || '').trim())
        .sort((p, q) => num(q.updated) - num(p.updated))[0];
      if (!o) return null;
      let a;
      try { a = JSON.parse(o.j); } catch { return null; }
      return Array.isArray(a) ? a : null;
    };
    // (como en el móvil: la de la ficha y, si no hay o no se entiende, la de su categoría)
    const a = leer(`${pref}:${kindId}:${id}`) || leer(`tpl:${pref}:${kindId}`);
    if (!a) return null;
    return a.map((r) => ({
      split: clamp(num(r && r.s, 0.5), 0.3, 0.7),
      alto: Math.max(0, num(r && r.a, 0)),
      celdas: (Array.isArray(r && r.c) ? r.c : []).slice(0, 2).map((c) => ({
        tipo: String((c && c.t) || 'vacio'), titulo: String((c && c.h) || ''), texto: String((c && c.x) || ''),
        imagen: String((c && c.i) || ''), entera: !!(c && c.f), zoom: num(c && c.z, 1), ox: num(c && c.ox), oy: num(c && c.oy),
        forma: String((c && c.sh) || ''), asp: num(c && c.as),
      })),
    }));
  };

  // Dónde y a qué tamaño se dibuja una imagen en una casilla de bw×bh (ratio = ancho/alto de la foto): la foto entera se
  // dibuja a su tamaño de «llenar» (dw×dh) y la casilla la recorta DESPUÉS de moverla; nunca queda un hueco (no se aleja de
  // «llenar» ni se desplaza más allá de su borde). Las «enteras» (logos) se dejan como están. Igual que magPlace del móvil.
  M.lugar = (c, bw, bh, ratio) => {
    const z0 = num(c.zoom, 1); const ox = num(c.ox); const oy = num(c.oy);
    if (!(bw > 0) || !(bh > 0) || !(ratio > 0)) return { z: z0, tx: ox * bw, ty: oy * bh, dw: bw, dh: bh };
    const ancha = ratio > bw / bh;
    if (c.entera) {
      const dw = ancha ? bw : bh * ratio; const dh = ancha ? bw / ratio : bh;
      return { z: z0, tx: ox * bw, ty: oy * bh, dw, dh };
    }
    const dw = ancha ? bh * ratio : bw; const dh = ancha ? bh : bw / ratio;
    const z = Math.max(z0, 1);
    const sx = (dw * z - bw) / 2; const sy = (dh * z - bh) / 2;
    return { z, tx: clamp(ox * bw, -sx, sx), ty: clamp(oy * bh, -sy, sy), dw, dh };
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = M;
  else raiz.RV = Object.assign(raiz.RV || {}, { M });
})(typeof window !== 'undefined' ? window : globalThis);
