'use strict';
/* global window, document */
// Escribir: el árbol de relaciones. Las fichas (personajes, lugares, objetos…) en óvalos sobre un lienzo libre, unidas por
// líneas con la palabra que tú quieras en medio (enemigos, amantes, hogar, pertenencia…). Pulsar una ficha la abre; pulsar
// la palabra de una unión enseña lo que escribiste de ella. Las fichas se colocan arrastrando (la posición viaja con la
// cuenta) y «Ordenar» las reparte solas. Unión «v» { a, b, titulo, texto, flecha (1: de a hacia b), color } (docs/escribir.md).
(function (RV) {
  const h = RV.h;
  const num = RV.num;
  const E = RV.E;
  const op = (...a) => E.op(...a);
  const NS = 'http://www.w3.org/2000/svg';
  const sv = (tag, attrs = {}, ...kids) => {
    const el = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) if (v != null && v !== false) el.setAttribute(k, String(v));
    for (const k of kids.flat()) if (k != null && k !== false) el.append(typeof k === 'string' ? document.createTextNode(k) : k);
    return el;
  };
  // Tamaño de cada ficha: el que elijas o, si no, según el tipo (lugares y mundo grandes, personajes medianos, lo demás pequeño)
  const TAM = { 1: [52, 26, 13], 2: [70, 34, 15], 3: [96, 50, 19] };
  const tamDe = (f) => num(f.tam) || (f.tipo === 'lugar' || f.tipo === 'mundo' ? 3 : f.tipo === 'personaje' ? 2 : 1);
  const vistas = new Map(); // obra → { x, y, k } (el desplazamiento y el zoom del lienzo, mientras Atrio está abierto)

  // ---------------------------------------------------------------- Uniones
  E.dlgRelacion = (pid, rel, nueva = {}) => {
    const fs = E.fichas(pid);
    let a = rel ? rel.a : nueva.a || ''; let b = rel ? rel.b : nueva.b || '';
    let flecha = rel ? num(rel.flecha) : 0;
    let color = rel ? rel.color || '' : '';
    const ti = RV.entrada(rel ? rel.titulo : '', { 'data-autofocus': true, placeholder: 'Enemigos, amantes, hogar, pertenencia…' });
    const tx = RV.areaTexto(rel ? rel.texto : '', { rows: '8', placeholder: 'Todo lo que quieras contar de esta relación (opcional)' });
    const opciones = fs.map((f) => [f.id, f.nombre || 'Sin nombre']);
    const nombre = (id) => ((E.get(id) || {}).nombre || '…');
    const flechas = () => [[0, 'Sin flecha'], [1, `De ${nombre(a)} hacia ${nombre(b)}`], [2, `De ${nombre(b)} hacia ${nombre(a)}`]];
    const caja = h('div');
    const pintaFlechas = () => caja.replaceChildren(E.chips(flechas(), flecha, (k) => { flecha = k; }));
    pintaFlechas();
    RV.dialogo({
      ante: rel ? 'Editar la unión' : 'Nueva unión', titulo: `${nombre(a)} y ${nombre(b)}`, ancho: true,
      cuerpo: [h('div.e-dos', RV.campo('Una', E.selector(a, opciones, (v) => { a = v; pintaFlechas(); })), RV.campo('Con', E.selector(b, opciones, (v) => { b = v; pintaFlechas(); }))),
        RV.campo('La palabra de la línea', ti, 'Lo que se lee en el árbol, en medio de la línea.'), RV.campo('Qué son el uno para el otro', tx),
        h('div.campo', h('span', 'Flecha'), caja), RV.campo('Color de la línea', E.colores(color, (c) => { color = c; }))],
      acciones: [rel ? { texto: 'Borrar la unión', peligro: true, fn: (d) => { d.cerrar(); E.aPapelera(rel); } } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: rel ? 'Guardar' : 'Unir', principal: true, fn: async (d) => {
          if (!a || !b || a === b) { RV.aviso('Elige dos fichas distintas.', 'error'); return; }
          if (!ti.value.trim()) { RV.aviso('Pon la palabra de la línea.', 'error'); return; }
          // (la flecha se guarda siempre de «a» hacia «b»)
          const c = flecha === 2 ? { a: b, b: a, flecha: 1 } : { a, b, flecha: flecha ? 1 : 0 };
          try { const x = { ...c, titulo: ti.value.trim(), texto: tx.value, color: color || null }; if (rel) await op('edita', rel.id, x); else await op('nuevo', 'v', pid, x); d.cerrar(); } catch (e) { E.err(e); }
        } }],
    });
  };
  E.verRelacion = (pid, r) => {
    const a = E.get(r.a); const b = E.get(r.b);
    if (!a || !b) return;
    RV.dialogo({
      ante: 'Unión', titulo: (r.titulo || 'Unión').toUpperCase(), ancho: true, clase: 'e-lee',
      cuerpo: [h('span.e-lee-color', { style: { background: E.colorDe(r.color, 'var(--tinta)') } }),
        h('div.e-union-par', h('button.enlace-serif', { type: 'button', onclick: () => { RV.cerrarDialogo(); E.verFicha(pid, a); } }, a.nombre || 'Sin nombre'),
          h('span.versal', r.flecha ? '→' : '—'), h('button.enlace-serif', { type: 'button', onclick: () => { RV.cerrarDialogo(); E.verFicha(pid, b); } }, b.nombre || 'Sin nombre')),
        r.texto ? h('div.e-lee-sec', ...E.parrafos(r.texto)) : h('p.apagado', 'Solo la palabra; no has escrito nada más de esta unión.')],
      acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, { texto: 'Editar', principal: true, fn: (d) => { d.cerrar(); E.dlgRelacion(pid, r); } }],
    });
  };

  // ---------------------------------------------------------------- Colocar
  /** Dónde va cada ficha: la suya o, si aún no tiene, en círculo (sin guardar hasta que la muevas u ordenes). */
  const posiciones = (fs) => {
    const sin = fs.filter((f) => !Number.isFinite(Number(f.ax)) || f.ax === '' || f.ax == null);
    const p = new Map();
    for (const f of fs) if (!sin.includes(f)) p.set(f.id, { x: num(f.ax), y: num(f.ay) });
    const r = 140 + sin.length * 28;
    sin.forEach((f, i) => { const t = (i / Math.max(1, sin.length)) * Math.PI * 2 - Math.PI / 2; p.set(f.id, { x: Math.round(r * Math.cos(t)), y: Math.round(r * Math.sin(t) * 0.7) }); });
    return p;
  };
  /** «Ordenar»: muelles entre lo unido y repulsión entre todo (unas pocas centenas de pasos), centrado en el origen. */
  E.ordenaArbol = (fs, rels, ini) => {
    const p = new Map(fs.map((f) => [f.id, { ...(ini.get(f.id) || { x: 0, y: 0 }) }]));
    const ids = fs.map((f) => f.id);
    const k = 270;
    for (let it = 0; it < 400; it++) {
      const t = 30 * (1 - it / 400) + 1;
      const d = new Map(ids.map((id) => [id, { x: 0, y: 0 }]));
      for (let i = 0; i < ids.length; i++) for (let j = i + 1; j < ids.length; j++) {
        const A = p.get(ids[i]); const B = p.get(ids[j]);
        let dx = A.x - B.x; let dy = A.y - B.y; let dist = Math.hypot(dx, dy);
        if (dist < 0.01) { dx = (i - j) * 0.1 + 0.05; dy = 0.07; dist = 0.1; }
        const f = (k * k) / dist;
        d.get(ids[i]).x += (dx / dist) * f; d.get(ids[i]).y += (dy / dist) * f;
        d.get(ids[j]).x -= (dx / dist) * f; d.get(ids[j]).y -= (dy / dist) * f;
      }
      for (const r of rels) {
        const A = p.get(r.a); const B = p.get(r.b); if (!A || !B) continue;
        const dx = A.x - B.x; const dy = A.y - B.y; const dist = Math.max(0.01, Math.hypot(dx, dy));
        const f = (dist * dist) / k;
        d.get(r.a).x -= (dx / dist) * f; d.get(r.a).y -= (dy / dist) * f;
        d.get(r.b).x += (dx / dist) * f; d.get(r.b).y += (dy / dist) * f;
      }
      for (const id of ids) {
        const P = p.get(id); const D = d.get(id);
        D.x -= P.x * 0.02 * k / 10; D.y -= P.y * 0.03 * k / 10; // hacia el centro (un poco más en vertical: el lienzo es apaisado)
        const m = Math.max(0.01, Math.hypot(D.x, D.y));
        P.x += (D.x / m) * Math.min(m, t * 3); P.y += (D.y / m) * Math.min(m, t * 3);
      }
    }
    const cx = [...p.values()].reduce((s, q) => s + q.x, 0) / Math.max(1, p.size);
    const cy = [...p.values()].reduce((s, q) => s + q.y, 0) / Math.max(1, p.size);
    for (const q of p.values()) { q.x = Math.round(q.x - cx); q.y = Math.round(q.y - cy); }
    return p;
  };

  // ---------------------------------------------------------------- El lienzo
  E.arbol = (obra, ruta) => {
    const pid = obra.id;
    const fs = E.fichas(pid);
    const rels = E.relaciones(pid).filter((r) => E.get(r.a) && E.get(r.b));
    const raiz = h('div.e-arbol');
    let uniendo = null; // null, o { a: id|null }
    const ayuda = h('span.apagado.e-arbol-ayuda');
    const btnUnir = h('button.boton-borde', { type: 'button', onclick: () => { uniendo = uniendo ? null : { a: null }; pintaModo(); } }, 'Unir dos fichas');
    const pintaModo = () => {
      btnUnir.textContent = uniendo ? 'Dejar de unir' : 'Unir dos fichas';
      btnUnir.setAttribute('aria-pressed', uniendo ? 'true' : 'false');
      ayuda.textContent = uniendo ? (uniendo.a ? `Ahora pulsa la ficha con la que unir a ${(E.get(uniendo.a) || {}).nombre || ''}.` : 'Pulsa la primera ficha.') : 'Arrastra para mover; rueda o +/− para acercar; pulsa una ficha para verla y la palabra de una línea para leer la unión.';
      for (const n of svg.querySelectorAll('.e-nodo')) n.classList.toggle('elegido', !!(uniendo && uniendo.a === n.dataset.id));
    };
    const pos = posiciones(fs);
    const v = vistas.get(pid) || { x: 0, y: 0, k: 1 };
    vistas.set(pid, v);
    const svg = sv('svg', { class: 'e-arbol-lienzo', role: 'img', 'aria-label': 'Árbol de relaciones' });
    const defs = sv('defs', {}, sv('marker', { id: 'e-flecha', viewBox: '0 0 10 10', refX: '9', refY: '5', markerWidth: '7', markerHeight: '7', orient: 'auto-start-reverse' }, sv('path', { d: 'M0,0 L10,5 L0,10 z', fill: 'context-stroke' })));
    const mundo = sv('g');
    svg.append(defs, mundo);
    const aplicaVista = () => mundo.setAttribute('transform', `translate(${v.x} ${v.y}) scale(${v.k})`);

    // Borde de un óvalo en la dirección (dx, dy): para que las líneas acaben en el borde y no en el centro
    const borde = (f, dx, dy) => { const [rx, ry] = TAM[tamDe(f)]; const l = Math.hypot(dx / rx, dy / ry) || 1; return { x: dx / l, y: dy / l }; };
    const capaLineas = sv('g', { class: 'e-lineas' });
    const capaNodos = sv('g', { class: 'e-nodos' });
    const capaEtq = sv('g', { class: 'e-etiquetas' }); // (las palabras, encima de las fichas: que no las tape ninguna)
    mundo.append(capaLineas, capaNodos, capaEtq);
    const pintaLineas = () => {
      capaLineas.replaceChildren(); capaEtq.replaceChildren();
      // (si hay varias uniones entre las mismas dos fichas, se curvan para no taparse)
      const pares = new Map();
      for (const r of rels) { const k = [r.a, r.b].sort().join('|'); pares.set(k, [...(pares.get(k) || []), r]); }
      for (const r of rels) {
        const fa = E.get(r.a); const fb = E.get(r.b); const A = pos.get(r.a); const B = pos.get(r.b);
        if (!A || !B) continue;
        const grupo = pares.get([r.a, r.b].sort().join('|')); const idx = grupo.indexOf(r); const n = grupo.length;
        const curva = n > 1 ? (idx - (n - 1) / 2) * 46 * (r.a < r.b ? 1 : -1) : 0;
        const dx = B.x - A.x; const dy = B.y - A.y; const len = Math.hypot(dx, dy) || 1;
        const nx = -dy / len; const ny = dx / len;
        const mx = (A.x + B.x) / 2 + nx * curva; const my = (A.y + B.y) / 2 + ny * curva;
        const sa = borde(fa, mx - A.x, my - A.y); const sb = borde(fb, mx - B.x, my - B.y);
        const color = E.colorDe(r.color, 'var(--tinta)');
        capaLineas.append(sv('path', { d: `M${A.x + sa.x},${A.y + sa.y} Q${mx},${my} ${B.x + sb.x},${B.y + sb.y}`, class: 'e-linea-rel', stroke: color, fill: 'none', 'marker-end': r.flecha ? 'url(#e-flecha)' : null }));
        // La palabra, en medio de la curva (el punto medio de una cuadrática está a medio camino del control)
        const lx = (A.x + B.x) / 4 + mx / 2; const ly = (A.y + B.y) / 4 + my / 2;
        const txt = (r.titulo || 'unión').toUpperCase();
        const ancho = Math.max(30, txt.length * 7.2 + 14);
        const et = sv('g', { class: 'e-rel-etq', transform: `translate(${lx} ${ly})`, tabindex: '0', role: 'button', 'aria-label': `${txt}: ${fa.nombre} y ${fb.nombre}` },
          sv('rect', { x: -ancho / 2, y: -10, width: ancho, height: 20, rx: 4 }), sv('text', { 'text-anchor': 'middle', y: 4, fill: color }, txt));
        et.addEventListener('click', (ev) => { ev.stopPropagation(); E.verRelacion(pid, r); });
        et.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') E.verRelacion(pid, r); });
        capaEtq.append(et);
      }
    };
    const nodos = new Map();
    for (const f of fs) {
      const [rx, ry, fz] = TAM[tamDe(f)];
      const P = pos.get(f.id);
      const nombre = f.nombre || 'Sin nombre';
      const g = sv('g', { class: 'e-nodo', 'data-id': f.id, transform: `translate(${P.x} ${P.y})`, tabindex: '0', role: 'button', 'aria-label': nombre },
        sv('ellipse', { rx, ry, stroke: E.colorDe(f.color), class: 'e-nodo-fondo' }),
        sv('text', { 'text-anchor': 'middle', y: fz / 3, 'font-size': nombre.length > 12 ? fz * 0.8 : fz }, nombre.length > 18 ? `${nombre.slice(0, 17)}…` : nombre));
      nodos.set(f.id, g);
      capaNodos.append(g);
      // Arrastrar una ficha la mueve (y se guarda); pulsarla sin moverla la abre (o la elige, si estás uniendo)
      g.addEventListener('pointerdown', (ev) => {
        if (ev.button !== 0) return;
        ev.stopPropagation();
        g.setPointerCapture(ev.pointerId);
        const ini = { x: ev.clientX, y: ev.clientY, px: P.x, py: P.y };
        let movido = false;
        const mueve = (e) => {
          const dx = (e.clientX - ini.x) / v.k; const dy = (e.clientY - ini.y) / v.k;
          if (!movido && Math.hypot(e.clientX - ini.x, e.clientY - ini.y) < 5) return;
          movido = true;
          P.x = ini.px + dx; P.y = ini.py + dy;
          g.setAttribute('transform', `translate(${P.x} ${P.y})`);
          pintaLineas();
        };
        const suelta = () => {
          g.removeEventListener('pointermove', mueve); g.removeEventListener('pointerup', suelta); g.removeEventListener('pointercancel', suelta);
          if (movido) { op('edita', f.id, { ax: Math.round(P.x), ay: Math.round(P.y) }).catch(E.err); return; }
          if (uniendo) {
            if (!uniendo.a) { uniendo.a = f.id; pintaModo(); return; }
            if (uniendo.a === f.id) { uniendo.a = null; pintaModo(); return; }
            const a = uniendo.a; uniendo = null; pintaModo();
            E.dlgRelacion(pid, null, { a, b: f.id });
            return;
          }
          E.verFicha(pid, f);
        };
        g.addEventListener('pointermove', mueve); g.addEventListener('pointerup', suelta); g.addEventListener('pointercancel', suelta);
      });
      g.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') E.verFicha(pid, f); });
    }
    pintaLineas();

    // Mover el lienzo arrastrando el fondo; acercar con la rueda (hacia donde está el ratón)
    svg.addEventListener('pointerdown', (ev) => {
      // (sobre la palabra de una unión no se arrastra el lienzo: si no, el lienzo se quedaría el clic)
      if (ev.button !== 0 || (ev.target.closest && ev.target.closest('.e-rel-etq'))) return;
      svg.setPointerCapture(ev.pointerId);
      const ini = { x: ev.clientX, y: ev.clientY, vx: v.x, vy: v.y };
      const mueve = (e) => { v.x = ini.vx + e.clientX - ini.x; v.y = ini.vy + e.clientY - ini.y; aplicaVista(); };
      const suelta = () => { svg.removeEventListener('pointermove', mueve); svg.removeEventListener('pointerup', suelta); };
      svg.addEventListener('pointermove', mueve); svg.addEventListener('pointerup', suelta);
    });
    const zoom = (factor, cx, cy) => {
      const k = Math.min(3, Math.max(0.25, v.k * factor));
      const r = svg.getBoundingClientRect();
      const px = cx == null ? r.width / 2 : cx - r.left; const py = cy == null ? r.height / 2 : cy - r.top;
      v.x = px - ((px - v.x) * k) / v.k; v.y = py - ((py - v.y) * k) / v.k; v.k = k;
      aplicaVista();
    };
    svg.addEventListener('wheel', (ev) => { ev.preventDefault(); zoom(ev.deltaY < 0 ? 1.12 : 1 / 1.12, ev.clientX, ev.clientY); }, { passive: false });
    // Centrar: que se vea todo (o la ficha pedida, si se viene de «Ver en el árbol»)
    const centra = (soloId = null) => {
      const r = svg.getBoundingClientRect();
      if (!r.width || !fs.length) { v.x = r.width / 2 || 400; v.y = r.height / 2 || 300; aplicaVista(); return; }
      const ps = soloId && pos.get(soloId) ? [pos.get(soloId)] : [...pos.values()];
      const minX = Math.min(...ps.map((p) => p.x)) - 110; const maxX = Math.max(...ps.map((p) => p.x)) + 110;
      const minY = Math.min(...ps.map((p) => p.y)) - 70; const maxY = Math.max(...ps.map((p) => p.y)) + 70;
      v.k = soloId ? 1.2 : Math.min(1.4, Math.max(0.3, Math.min(r.width / (maxX - minX), r.height / (maxY - minY))));
      v.x = r.width / 2 - ((minX + maxX) / 2) * v.k; v.y = r.height / 2 - ((minY + maxY) / 2) * v.k;
      aplicaVista();
      if (soloId && nodos.get(soloId)) nodos.get(soloId).classList.add('elegido');
    };
    const primera = !v.puesta;
    requestAnimationFrame(() => { if (primera || ruta.foco) { v.puesta = true; centra(ruta.foco || null); } else aplicaVista(); });
    aplicaVista();

    const ordenar = async () => {
      if (fs.length < 2) return;
      const p = E.ordenaArbol(fs, rels, pos);
      try { for (const f of fs) await op('edita', f.id, { ax: p.get(f.id).x, ay: p.get(f.id).y }); v.puesta = false; } catch (e) { E.err(e); }
    };
    raiz.append(
      h('div.fila-botones.e-arbol-barra',
        h('button.boton-tinta', { type: 'button', onclick: () => { E.alCrearFicha = (f) => { const r = svg.getBoundingClientRect(); op('edita', f.id, { ax: Math.round((r.width / 2 - v.x) / v.k), ay: Math.round((r.height / 2 - v.y) / v.k) }).catch(E.err); }; E.dlgFicha(pid, null); } }, 'Nueva ficha'),
        btnUnir,
        h('button.enlace', { type: 'button', onclick: () => RV.confirmar('¿Ordenar el árbol?', 'Atrio colocará todas las fichas solas (lo unido, cerca). Después puedes moverlas a mano.', 'Ordenar', ordenar) }, 'Ordenar'),
        h('span.hueco'),
        h('button.enlace', { type: 'button', title: 'Acercar', onclick: () => zoom(1.25) }, '+'),
        h('button.enlace', { type: 'button', title: 'Alejar', onclick: () => zoom(0.8) }, '−'),
        h('button.enlace', { type: 'button', onclick: () => centra() }, 'Verlo todo')),
      ayuda);
    if (!fs.length) raiz.append(h('div.e-vacio', h('h2.titular', 'El árbol de tu historia'), h('p', 'Crea fichas (personajes, lugares, objetos…) y únelas: «enemigos», «amantes», «hogar», «pertenencia»… Con todo lo que quieras contar de cada unión.')));
    else raiz.append(svg);
    pintaModo();
    return raiz;
  };
})(window.RV);
