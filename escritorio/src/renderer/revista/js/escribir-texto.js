'use strict';
/* global window, module */
// La lógica de texto de «Escribir» (la misma que EscribirTexto.kt y EscribirDiff.kt del móvil): pintar el Markdown ligero,
// los cambios de los botones del formato (con la selección), deshacer/rehacer y comparar dos textos párrafo a párrafo.
// Funciones puras: se prueban con node (herramientas/prueba-escribir-texto.js) y se usan en la revista.
(function (raiz) {
  const ET = {};

  // ---------------------------------------------------------------- Pintar el formato
  // Devuelve trozos { desde, hasta, cls } con las clases de cada tramo. Clases: h1 h2 h3 cita salto (de la línea),
  // m (signo del formato, apagado), neg, cur, ficha (nombre de una ficha), busca (lo que se busca)
  ET.estilos = (t, { fichas = null, busca = null } = {}) => {
    const tramos = [];
    const add = (desde, hasta, cls) => { if (hasta > desde) tramos.push({ desde, hasta, cls }); };
    let i = 0;
    while (i <= t.length) {
      let fin = t.indexOf('\n', i); if (fin < 0) fin = t.length;
      const l = t.slice(i, fin);
      const s = l.trim();
      const hasta = Math.min(t.length, fin + 1);
      if (s === '***' || s === '* * *') { add(i, hasta, 'salto'); } else {
        if (l.startsWith('### ')) { add(i, hasta, 'h3'); add(i, i + 4, 'm'); } else if (l.startsWith('## ')) { add(i, hasta, 'h2'); add(i, i + 3, 'm'); } else if (l.startsWith('# ')) { add(i, hasta, 'h1'); add(i, i + 2, 'm'); } else if (l.startsWith('> ')) { add(i, hasta, 'cita'); add(i, i + 2, 'm'); }
        let neg = -1; let cur = -1;
        for (let k = 0; k < l.length; k++) {
          const c = l[k];
          if (c === '\\' && k + 1 < l.length) { k++; continue; }
          if (c !== '*') continue;
          if (l[k + 1] === '*') {
            add(i + k, i + k + 2, 'm');
            if (neg < 0) neg = k + 2; else { add(i + neg, i + k, 'neg'); neg = -1; }
            k++; continue;
          }
          add(i + k, i + k + 1, 'm');
          if (cur < 0) cur = k + 1; else { add(i + cur, i + k, 'cur'); cur = -1; }
        }
      }
      i = fin + 1;
    }
    if (fichas) for (const m of t.matchAll(fichas)) add(m.index, m.index + m[0].length, 'ficha');
    if (busca) for (const m of t.matchAll(busca)) add(m.index, m.index + m[0].length, 'busca');
    return tramos;
  };

  // Parte [0, n) en segmentos con el conjunto de clases de cada uno (para pintar spans sin solaparse)
  ET.segmentos = (t, opts) => {
    const n = t.length;
    const cortes = new Set([0, n]);
    const tr = ET.estilos(t, opts);
    for (const x of tr) { cortes.add(x.desde); cortes.add(x.hasta); }
    const orden = [...cortes].filter((c) => c >= 0 && c <= n).sort((a, b) => a - b);
    const out = [];
    for (let k = 0; k < orden.length - 1; k++) {
      const a = orden[k]; const b = orden[k + 1];
      const cls = new Set();
      for (const x of tr) if (x.desde <= a && x.hasta >= b) cls.add(x.cls);
      out.push({ desde: a, hasta: b, cls: [...cls] });
    }
    return out;
  };

  // ---------------------------------------------------------------- Cambios con la selección
  // v = { text, a, b } (a y b: principio y fin de la selección)
  const rango = (v) => ({ a: Math.min(v.a, v.b), b: Math.max(v.a, v.b) });
  const sufijoAst = (t, pos) => { let n = 0; while (pos - n - 1 >= 0 && t[pos - n - 1] === '*') n++; return n; };
  const prefijoAst = (t, pos) => { let n = 0; while (pos + n < t.length && t[pos + n] === '*') n++; return n; };

  ET.envuelve = (v, marca) => {
    const { a, b } = rango(v); const t = v.text;
    const sel = t.slice(a, b); const m = marca.length;
    const nb = sufijoAst(t, a); const na = prefijoAst(t, b);
    const fuera = (marca === '**' || marca === '*') ? ((x) => (marca === '*' ? x % 2 === 1 : x >= 2))(Math.min(nb, na))
      : a >= m && b + m <= t.length && t.slice(a - m, a) === marca && t.slice(b, b + m) === marca;
    if (fuera) return { text: t.slice(0, a - m) + sel + t.slice(b + m), a: a - m, b: b - m };
    if (sel.length >= 2 * m && sel.startsWith(marca) && sel.endsWith(marca) && !(marca === '*' && sel.startsWith('**') && sel.length < 5)) {
      return { text: t.slice(0, a) + sel.slice(m, sel.length - m) + t.slice(b), a, b: b - 2 * m };
    }
    return { text: t.slice(0, a) + marca + sel + marca + t.slice(b), a: a + m, b: sel.length ? b + m : a + m };
  };

  const PREF = /^(#{1,3} |> )/;
  ET.prefijoLinea = (v, prefijo) => {
    const { a, b } = rango(v); const t = v.text;
    const ini = t.lastIndexOf('\n', a - 1) + 1;
    let fin = t.indexOf('\n', Math.max(b, ini)); if (fin < 0) fin = t.length;
    const lineas = t.slice(ini, fin).split('\n');
    const todas = lineas.every((l) => l.startsWith(prefijo));
    const nuevo = lineas.map((l) => (todas ? l.slice(prefijo.length) : prefijo + l.replace(PREF, ''))).join('\n');
    const dif = nuevo.length - (fin - ini);
    const p = lineas[0];
    const cambioPrimera = todas ? -prefijo.length : prefijo.length - (p.length - p.replace(PREF, '').length);
    return { text: t.slice(0, ini) + nuevo + t.slice(fin), a: Math.max(ini, a + cambioPrimera), b: Math.max(ini, b + dif) };
  };

  ET.inserta = (v, x) => { const { a, b } = rango(v); return { text: v.text.slice(0, a) + x + v.text.slice(b), a: a + x.length, b: a + x.length }; };

  ET.salto = (v) => {
    const { a, b } = rango(v); const t = v.text;
    const antes = t.slice(0, a).replace(/\n+$/, ''); const despues = t.slice(b).replace(/^\n+/, '');
    const ini = `${antes ? `${antes}\n\n` : ''}***\n\n`;
    return { text: ini + despues, a: ini.length, b: ini.length };
  };

  ET.comillas = (v) => {
    const { a, b } = rango(v); const sel = v.text.slice(a, b);
    return { text: `${v.text.slice(0, a)}«${sel}»${v.text.slice(b)}`, a: a + 1, b: sel ? b + 1 : a + 1 };
  };

  // ---------------------------------------------------------------- Deshacer y rehacer
  ET.Historial = class {
    constructor(inicial, tope = 200) { this.atras = []; this.adelante = []; this.actual = inicial; this.ultima = 0; this.tope = tope; }
    // Apunta el cambio v. Los que llegan en menos de [junta] ms de otro cambio pequeño se funden con él
    apunta(v, ahora = Date.now(), junta = 900) {
      if (v.text === this.actual.text) { this.actual = v; return; }
      const grande = Math.abs(v.text.length - this.actual.text.length) > 24;
      if (!this.atras.length || grande || ahora - this.ultima > junta) { this.atras.push(this.actual); if (this.atras.length > this.tope) this.atras.shift(); }
      this.ultima = ahora; this.actual = v; this.adelante = [];
    }
    get puedeDeshacer() { return this.atras.length > 0; }
    get puedeRehacer() { return this.adelante.length > 0; }
    deshace() { const v = this.atras.pop(); if (!v) return null; this.adelante.push(this.actual); this.actual = v; this.ultima = 0; return v; }
    rehace() { const v = this.adelante.pop(); if (!v) return null; this.atras.push(this.actual); this.actual = v; this.ultima = 0; return v; }
  };

  // ---------------------------------------------------------------- Comparar dos textos
  ET.parrafos = (t) => String(t || '').replace(/\r\n/g, '\n').split(/\n\s*\n|\n/).map((x) => x.trim()).filter(Boolean);
  // [{ igual: [..] } | { aqui: [..], otro: [..] }]
  ET.compara = (aqui, otro) => {
    const a = ET.parrafos(aqui); const b = ET.parrafos(otro);
    const n = a.length; const m = b.length;
    const l = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
    for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) l[i][j] = a[i] === b[j] ? l[i + 1][j + 1] + 1 : Math.max(l[i + 1][j], l[i][j + 1]);
    const out = []; let igual = []; let xa = []; let xb = [];
    const cierraIgual = () => { if (igual.length) { out.push({ igual }); igual = []; } };
    const cierraCambio = () => { if (xa.length || xb.length) { out.push({ aqui: xa, otro: xb }); xa = []; xb = []; } };
    let i = 0; let j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && a[i] === b[j]) { cierraCambio(); igual.push(a[i]); i++; j++; }
      else if (j < m && (i === n || l[i][j + 1] >= l[i + 1][j])) { cierraIgual(); xb.push(b[j]); j++; }
      else { cierraIgual(); xa.push(a[i]); i++; }
    }
    cierraIgual(); cierraCambio();
    return out;
  };
  ET.mezcla = (partes, usaOtro) => {
    let k = 0; const ps = [];
    for (const p of partes) {
      if (p.igual) ps.push(...p.igual);
      else { ps.push(...(usaOtro[k] ? p.otro : p.aqui)); k++; }
    }
    return ps.join('\n\n');
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = ET;
  else raiz.RV = Object.assign(raiz.RV || {}, { ET });
})(typeof window !== 'undefined' ? window : globalThis);
