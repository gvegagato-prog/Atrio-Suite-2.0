'use strict';
/* global window, document, navigator, performance, requestAnimationFrame, setTimeout */
// Mando, teclado y ratón en toda la revista (los dos modos: Juegos y Revista).
// Se mueve el foco de verdad (el del navegador) entre los elementos que se pueden pulsar, eligiendo el vecino
// más cercano en cada dirección. Con el mando o el teclado se ve un marco del color del acento; al mover el
// ratón, desaparece.
//
// Mando (mapeo estándar, el de un mando de Xbox y el del Steam Controller con Steam Input):
//   cruceta o stick izquierdo: moverse · A: pulsar · B: volver · X: buscar · Y: opciones
//   LB / RB: sección anterior / siguiente · Menú (☰): el menú de Atrio · Vista (⧉): cambiar entre Juegos y Revista
//   stick derecho: desplazar la página
// Teclado (y el Steam Controller en modo escritorio, con las teclas que vienen en Ajustes → Mando):
//   flechas · Intro · Esc o Retroceso · / · Espacio · RePág y AvPág · S · C
(function (RV) {
  const M = {};
  RV.mando = M;
  let acciones = {};            // acción -> función (las pone cada modo)
  M.al = (mapa) => { acciones = { ...acciones, ...mapa }; };

  // ---------------------------------------------------------------- Entrada: mando o ratón
  let entrada = 'raton';
  const ponEntrada = (e) => {
    if (entrada === e) return;
    entrada = e;
    document.body.dataset.entrada = e;
  };
  M.entrada = () => entrada;

  // ---------------------------------------------------------------- Lo que se puede enfocar
  const SEL = 'button:not([disabled]), a[href], input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';
  function ambito() {
    const capa = document.getElementById('capa');
    const arriba = capa && capa.lastElementChild;
    return arriba || document;
  }
  function visibles() {
    const raiz = ambito();
    return [...raiz.querySelectorAll(SEL)].filter((el) => {
      if (el.closest('[inert], [hidden], .oculto-mando') || el.getAttribute('tabindex') === '-1') return false;
      if (raiz === document && el.closest('#capa')) return false;
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      const cs = window.getComputedStyle(el);
      return cs.visibility !== 'hidden' && cs.display !== 'none';
    });
  }
  const actual = () => {
    const a = document.activeElement;
    return a && a !== document.body && ambito().contains(a) ? a : null;
  };
  const movimiento = () => !document.body.classList.contains('quieto');
  function enfocar(el, o = {}) {
    if (!el) return;
    el.focus({ preventScroll: true });
    if (o.sinDesplazar) return;
    const r = el.getBoundingClientRect();
    const margen = 90;
    // Solo se desplaza lo necesario (y un poco más, para ver lo que viene)
    if (r.top < margen || r.bottom > window.innerHeight - margen || r.left < 0 || r.right > window.innerWidth) {
      el.scrollIntoView({ block: o.centro ? 'center' : 'nearest', inline: 'nearest', behavior: movimiento() ? 'smooth' : 'auto' });
      if (r.bottom > window.innerHeight - margen && !o.centro) window.scrollBy({ top: margen, behavior: movimiento() ? 'smooth' : 'auto' });
    }
  }
  M.enfocar = enfocar;
  M.primero = (raiz) => {
    const l = raiz ? [...raiz.querySelectorAll(SEL)] : visibles();
    const el = l.find((e) => e.hasAttribute('data-autofocus')) || l[0];
    if (el) enfocar(el, { sinDesplazar: true });
  };

  // El vecino más cercano en una dirección (como el lanzador clásico): primero en la misma fila o columna
  function mover(dir) {
    const lista = visibles();
    const cur = actual();
    if (!cur || !lista.includes(cur)) {
      const auto = lista.find((e) => e.hasAttribute('data-autofocus'));
      const visible = lista.find((e) => { const r = e.getBoundingClientRect(); return r.top >= 0 && r.bottom <= window.innerHeight; });
      enfocar(auto || visible || lista[0]);
      return;
    }
    const a = cur.getBoundingClientRect();
    const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    let mejor = null, nota = Infinity;
    for (const el of lista) {
      if (el === cur || el.contains(cur) || cur.contains(el)) continue;
      const b = el.getBoundingClientRect();
      const bx = b.left + b.width / 2, by = b.top + b.height / 2;
      let main, cross;
      if (dir === 'right') { if (bx <= ax + 2 || b.right <= a.right) continue; main = Math.max(0, b.left - a.right); cross = Math.abs(by - ay); }
      else if (dir === 'left') { if (bx >= ax - 2 || b.left >= a.left) continue; main = Math.max(0, a.left - b.right); cross = Math.abs(by - ay); }
      else if (dir === 'down') { if (by <= ay + 2 || b.bottom <= a.bottom) continue; main = Math.max(0, b.top - a.bottom); cross = Math.abs(bx - ax); }
      else { if (by >= ay - 2 || b.top >= a.top) continue; main = Math.max(0, a.top - b.bottom); cross = Math.abs(bx - ax); }
      const solape = dir === 'left' || dir === 'right'
        ? Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
        : Math.min(a.right, b.right) - Math.max(a.left, b.left);
      const n = main + cross * (solape > 0 ? 0.5 : 2.2) + (solape > 0 ? 0 : 300);
      if (n < nota) { nota = n; mejor = el; }
    }
    if (mejor) enfocar(mejor);
    else if (dir === 'up' && window.scrollY > 0) window.scrollBy({ top: -window.innerHeight * 0.6, behavior: movimiento() ? 'smooth' : 'auto' });
    else if (dir === 'down') window.scrollBy({ top: window.innerHeight * 0.6, behavior: movimiento() ? 'smooth' : 'auto' });
  }
  M.mover = mover;

  const escribiendo = (el) => !!el && (el.tagName === 'TEXTAREA' || el.isContentEditable || (el.tagName === 'INPUT' && !['range', 'button', 'checkbox', 'radio'].includes(el.type)));

  function pulsar() {
    const cur = actual();
    if (!cur) { mover('down'); return; }
    if (escribiendo(cur)) { cur.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true })); return; }
    cur.click();
  }
  function volver() {
    const cur = actual();
    if (escribiendo(cur)) { cur.blur(); return; }
    if (acciones.b && acciones.b() === true) return;
    if (RV.cerrarDialogo()) return;
    RV.volver();
  }

  // Un deslizador enfocado: izquierda y derecha cambian su valor
  function ajustar(dir) {
    const cur = actual();
    if (!cur || cur.tagName !== 'INPUT' || cur.type !== 'range') return false;
    if (dir === 'left') cur.stepDown(); else cur.stepUp();
    cur.dispatchEvent(new window.Event('input', { bubbles: true }));
    cur.dispatchEvent(new window.Event('change', { bubbles: true }));
    return true;
  }

  let ultima = null;
  M.ultima = () => ultima;
  const nota = (tipo, detalle) => { ultima = { tipo, detalle, t: Date.now() }; };

  M.hacer = (acc) => {
    ponEntrada('mando');
    if ((acc === 'left' || acc === 'right') && ajustar(acc)) return;
    switch (acc) {
      case 'up': case 'down': case 'left': case 'right': mover(acc); return;
      case 'a': pulsar(); return;
      case 'b': volver(); return;
      default: if (acciones[acc]) acciones[acc](actual());
    }
  };

  // ---------------------------------------------------------------- Teclado
  window.addEventListener('keydown', (e) => {
    nota('tecla', e.key);
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const t = document.activeElement;
    const typing = escribiendo(t);
    const mapa = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', PageUp: 'prev', PageDown: 'next' };
    let acc = mapa[e.key];
    if (typing) {
      // Escribiendo: las flechas mueven el cursor; arriba/abajo salen del campo (no en los textos largos)
      if (acc === 'left' || acc === 'right' || acc === 'prev' || acc === 'next') return;
      if ((acc === 'up' || acc === 'down') && t.tagName === 'TEXTAREA') return;
    } else {
      // Donde la página usa sus propias teclas (las preguntas de Descubrir: A–E y espacio), se las deja
      const libres = RV.ruta && RV.ruta().v === 'descubrir';
      // Espacio = Y (opciones) solo si hay opciones para lo enfocado; si no, es el espacio de siempre
      if (e.key === ' ' && !libres && t && t.closest && t.closest('[data-opciones]')) acc = 'y';
      if (e.key === 'Backspace') acc = 'b';
      if (!libres && (e.key === 's' || e.key === 'S')) acc = 'start';
      if (!libres && (e.key === 'c' || e.key === 'C')) acc = 'select';
    }
    // Intro y Esc ya los entiende la página (pulsar un botón, cerrar, volver): aquí solo se marca que es teclado
    if (e.key === 'Enter' || e.key === 'Escape') { ponEntrada('mando'); return; }
    if (!acc) return;
    e.preventDefault();
    e.stopPropagation();
    M.hacer(acc);
  }, true);

  // ---------------------------------------------------------------- Ratón
  window.addEventListener('mousemove', (e) => {
    if (Math.abs(e.movementX) + Math.abs(e.movementY) < 3) return;
    nota('ratón', 'movimiento');
    ponEntrada('raton');
  }, { passive: true });
  window.addEventListener('mousedown', () => ponEntrada('raton'), { passive: true });

  // ---------------------------------------------------------------- Mando (API Gamepad)
  // Mapeo estándar: 0 A, 1 B, 2 X, 3 Y, 4 LB, 5 RB, 8 Vista, 9 Menú, 12-15 cruceta; ejes 0-1 stick izquierdo, 2-3 derecho.
  // Un mando sin mapeo estándar se configura en Ajustes → Mando (el mismo mapa que usaba el lanzador clásico).
  const ACCIONES = ['up', 'down', 'left', 'right', 'a', 'b', 'x', 'y', 'prev', 'next', 'start', 'select'];
  M.ACCIONES = ACCIONES;
  const ESTANDAR = {
    a: { t: 'b', i: 0 }, b: { t: 'b', i: 1 }, x: { t: 'b', i: 2 }, y: { t: 'b', i: 3 },
    prev: { t: 'b', i: 4 }, next: { t: 'b', i: 5 }, select: { t: 'b', i: 8 }, start: { t: 'b', i: 9 },
    up: { t: 'b', i: 12 }, down: { t: 'b', i: 13 }, left: { t: 'b', i: 14 }, right: { t: 'b', i: 15 },
  };
  let mapas = {};
  let cambiarAB = false;
  M.config = (behavior) => {
    const b = behavior || {};
    cambiarAB = !!b.swapAB;
    mapas = {};
    for (const [id, m] of Object.entries(b.padMaps || {})) {
      if (!m) continue;
      try { mapas[id] = typeof m === 'string' ? JSON.parse(m) : m; } catch { /* mapa dañado */ }
    }
  };
  M.mandos = () => window.__fakePads || [...(navigator.getGamepads ? navigator.getGamepads() : [])].filter((p) => p && p.connected);
  const pulsado = (p, b) => {
    if (!b) return false;
    if (b.t === 'b') { const x = p.buttons[b.i]; return !!x && (x.pressed || x.value > 0.5); }
    if (b.t === 'a') { const v = p.axes[b.i] || 0; return b.d < 0 ? v < -0.55 : v > 0.55; }
    return false;
  };
  M.leer = (p, out) => {
    const propio = mapas[p.id];
    const mapa = propio || ESTANDAR;
    for (const acc of ACCIONES) {
      if (!pulsado(p, mapa[acc])) continue;
      let a = acc;
      if (cambiarAB && a === 'a') a = 'b'; else if (cambiarAB && a === 'b') a = 'a';
      out.add(a);
    }
    const usaEjes = propio && Object.values(propio).some((b) => b && b.t === 'a' && (b.i === 0 || b.i === 1));
    if (!usaEjes) {
      const [x = 0, y = 0] = p.axes;
      if (x < -0.55) out.add('left');
      if (x > 0.55) out.add('right');
      if (y < -0.55) out.add('up');
      if (y > 0.55) out.add('down');
    }
  };
  let captura = null;
  M.capturar = (fn) => { captura = fn; antes.clear(); };
  M.soltar = () => { captura = null; antes.clear(); };

  const REPITE = new Set(['up', 'down', 'left', 'right']);
  const antes = new Set();
  const sostenido = new Map();
  let foco = true;
  M.foco = (v) => { foco = !!v; if (foco) antes.clear(); };
  function sondear() {
    const pads = M.mandos();
    if (captura) { try { captura(pads); } catch { /* */ } return seguir(); }
    const ahora = new Set();
    for (const p of pads) M.leer(p, ahora);
    const t = performance.now();
    if (ahora.size) nota('mando', [...ahora].join(' '));
    if (foco || window.__fakePads) {
      for (const acc of ahora) {
        if (!antes.has(acc)) { sostenido.set(acc, { desde: t, ult: t }); M.hacer(acc); }
        else if (REPITE.has(acc)) {
          const s = sostenido.get(acc);
          if (s && t - s.desde > 380 && t - s.ult > (t - s.desde > 1500 ? 50 : 95)) { s.ult = t; M.hacer(acc); }
        }
      }
      // Stick derecho: desplazar la página (o lo que tenga la barra de desplazamiento debajo del foco)
      for (const p of pads) {
        const y = p.axes[3] || 0;
        if (Math.abs(y) > 0.25 && !(mapas[p.id])) {
          const zona = (document.activeElement && document.activeElement.closest('.desplaza')) || null;
          const v = Math.sign(y) * Math.pow((Math.abs(y) - 0.25) / 0.75, 1.6) * 28;
          if (zona) zona.scrollTop += v; else window.scrollBy(0, v);
        }
      }
    }
    antes.clear();
    for (const a of ahora) antes.add(a);
    seguir();
  }
  // Con la ventana en segundo plano (jugando), se mira el mando mucho menos
  function seguir() {
    if ((foco && document.visibilityState === 'visible') || window.__fakePads) requestAnimationFrame(sondear);
    else setTimeout(sondear, 300);
  }
  window.addEventListener('gamepadconnected', (e) => {
    try { RV.api.log(`mando conectado: «${e.gamepad.id}» mapeo=${e.gamepad.mapping || 'ninguno'}`); } catch { /* */ }
    ponEntrada('mando');
    if (!actual()) M.primero();
  });
  requestAnimationFrame(sondear);
})(window.RV);
