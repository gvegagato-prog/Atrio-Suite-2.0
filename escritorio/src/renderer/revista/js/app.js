'use strict';
/* global window, document, CSS */
// La revista: la cabecera de periódico, la navegación entre páginas (con «volver»), los atajos de teclado,
// lo que llega de Atrio en directo (cuenta, biblioteca, partidas, avisos) y el arranque.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const api = RV.api;

  // ---------------------------------------------------------------- Navegación
  // Una ruta es { v: vista, …datos }. «Volver» desanda las rutas (y la posición en la página).
  let ruta = { v: 'portada' };
  const pila = [];
  const igual = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  RV.ruta = () => ruta;
  RV.ir = (r, opts = {}) => {
    if (!opts.reemplazar && !igual(r, ruta)) pila.push({ ruta, y: window.scrollY });
    if (pila.length > 60) pila.shift();
    ruta = { ...r };
    pintar({ y: 0 });
  };
  RV.volver = () => {
    const prev = pila.pop();
    if (prev) { ruta = prev.ruta; pintar({ y: prev.y }); return true; }
    if (ruta.v !== 'portada') { ruta = { v: 'portada' }; pintar({ y: 0 }); return true; }
    return false;
  };
  RV.lanzador = () => {
    if (api.switchUi) api.switchUi('lanzador').catch((e) => RV.aviso(RV.limpiaError(e), 'error'));
    else RV.aviso('Esta versión de Atrio no puede cambiar de interfaz.', 'error');
  };

  const TITULOS = { portada: 'Portada', diario: 'Diario', cifras: 'Cifras', pc: 'Tu PC', ajustes: 'Ajustes' };
  function pintar(o = {}) {
    const pag = document.getElementById('pagina');
    // Lo que estabas escribiendo (y dónde), para no perderlo al redibujar con datos nuevos
    const campos = new Map();
    for (const el of RV.$$('[data-foco]', pag)) campos.set(el.dataset.foco, el.value);
    const act = document.activeElement;
    let foco = null;
    if (act && act.dataset && act.dataset.foco && pag.contains(act)) {
      foco = { k: act.dataset.foco, a: null, b: null };
      try { foco.a = act.selectionStart; foco.b = act.selectionEnd; } catch { /* campos sin selección */ }
    }
    const y = o.y != null ? o.y : window.scrollY;
    let nodo;
    try {
      nodo = (RV.vistas[ruta.v] || RV.vistas.portada)(ruta);
    } catch (e) {
      nodo = h('div.hoja', h('div.cabeza', h('div.ante', 'Fe de erratas')), RV.vacio(`No se ha podido mostrar esta página: ${RV.limpiaError(e)}`, true));
      try { api.log(`revista: ${e && e.stack ? e.stack : e}`); } catch { /* */ }
    }
    pag.replaceChildren(nodo);
    for (const el of RV.$$('[data-foco]', pag)) {
      if (campos.has(el.dataset.foco) && el.value !== campos.get(el.dataset.foco)) el.value = campos.get(el.dataset.foco);
    }
    cabecera();
    RV.ajustar(document);
    window.scrollTo(0, y);
    if (foco) {
      const el = pag.querySelector(`[data-foco="${CSS.escape(foco.k)}"]`);
      if (el) {
        el.focus({ preventScroll: true });
        try { if (foco.a != null) el.setSelectionRange(foco.a, foco.b); } catch { /* */ }
      }
    }
    const k = (ruta.v === 'seccion' || ruta.v === 'ficha') ? D.tipo(ruta.k) : null;
    document.title = `Atrio · ${ruta.v === 'ficha' ? ((k && D.buscar(k, ruta.t, ruta.id)) || {}).name || 'Ficha' : k ? k.title : TITULOS[ruta.v] || 'Revista'}`;
  }
  // Redibujar con los datos nuevos, sin perder el sitio (agrupando avisos seguidos)
  RV.refrescar = RV.debounce(() => pintar(), 50);
  const refrescarDespacio = RV.debounce(() => pintar(), 1200);

  // ---------------------------------------------------------------- Cabecera de periódico
  function cabecera() {
    const cab = document.getElementById('cabecera');
    const hoy = new Date();
    const item = (texto, r, activo, extra) => h('button.cab-item', {
      type: 'button', 'aria-current': activo ? 'page' : null, onclick: () => RV.ir(r),
    }, texto, extra != null ? h('span.n', String(extra)) : null);
    const v = ruta.v;
    const kAct = v === 'seccion' || v === 'ficha' ? Number(ruta.k) : null;
    const menu = h('nav.cab-menu', { 'aria-label': 'Secciones' },
      h('button.cab-marca', { type: 'button', onclick: () => RV.ir({ v: 'portada' }), 'aria-label': 'Atrio: portada' }, 'ATRIO'),
      item('Portada', { v: 'portada' }, v === 'portada'),
      D.tipos().map((k) => item(k.title, { v: 'seccion', k: k.id }, kAct === k.id)),
      item('Diario', { v: 'diario' }, v === 'diario'),
      item('Cifras', { v: 'cifras' }, v === 'cifras'),
      item('Tu PC', { v: 'pc' }, v === 'pc'),
      item('Ajustes', { v: 'ajustes' }, v === 'ajustes'));
    const folio = h('div.cab-folio',
      h('span.versal', `Nº ${RV.diaDelAno(hoy)} · ${RV.fmtLargo(hoy)}`),
      pila.length || v !== 'portada' ? h('button.enlace', { type: 'button', onclick: () => RV.volver(), title: 'Esc' }, '← Volver') : null,
      h('span.hueco'),
      h('button.enlace', { type: 'button', onclick: () => atajoBuscar(), title: 'Tecla /' }, 'Buscar /'),
      h('button.enlace.apagado', { type: 'button', onclick: () => api.toggleFullscreen(), title: 'F11' }, 'Pantalla completa'),
      h('button.enlace.apagado', { type: 'button', onclick: () => api.minimize() }, 'Minimizar'),
      h('button.enlace.apagado', { type: 'button', onclick: () => api.quit() }, 'Salir'));
    cab.replaceChildren(h('div.hoja', folio, menu, h('div.filete')));
  }

  // ---------------------------------------------------------------- Buscar en toda la colección
  RV.buscarTodo = () => {
    const input = h('input', { type: 'search', placeholder: 'Título, autor, estudio…', 'data-autofocus': true, spellcheck: 'false', 'aria-label': 'Buscar' });
    const ul = h('ul.resultados');
    let primero = null;
    const pintarRes = () => {
      const q = RV.normName(input.value);
      ul.replaceChildren();
      primero = null;
      if (!q) return;
      const hits = D.todo().filter((e) => RV.normName(e.item.name).includes(q) || RV.normName(e.item.creator).includes(q))
        .sort((a, b) => Number(!RV.normName(a.item.name).startsWith(q)) - Number(!RV.normName(b.item.name).startsWith(q)) || D.actividad(b.item, b.t) - D.actividad(a.item, a.t))
        .slice(0, 40);
      for (const e of hits) {
        const ir = () => { d.cerrar(); RV.ir({ v: 'ficha', k: e.kind.id, t: e.t, id: e.item.id }); };
        if (!primero) primero = ir;
        const nota = D.nota(e.item, e.t);
        ul.append(h('li', h('button.resultado', { type: 'button', style: { width: '100%', textAlign: 'left' }, onclick: ir },
          h('span', h('span.tit', e.item.name), h('span.versal', { style: { display: 'block' } }, [e.kind.short, D.estado(e.kind, e.item, e.t), e.item.creator].filter(Boolean).join(' · '))),
          nota != null ? h('span.tit', String(nota)) : h('span'))));
      }
      // Y los juegos del PC que coincidan
      const pcs = D.juegosPc().filter((g) => RV.normName(g.name).includes(q)).slice(0, 10);
      for (const g of pcs) {
        const jugar = () => { d.cerrar(); RV.jugar(g); };
        if (!primero) primero = () => { d.cerrar(); RV.ir({ v: 'pc' }); };
        ul.append(h('li', h('div.resultado',
          h('span', h('span.tit', g.name), h('span.versal', { style: { display: 'block' } }, `Tu PC · ${g.sourceLabel || g.source}`)),
          h('button.enlace.acento', { type: 'button', onclick: jugar }, 'Jugar →'))));
      }
      if (!ul.children.length) ul.append(h('li', RV.vacio('Nada coincide con la búsqueda.')));
    };
    input.addEventListener('input', pintarRes);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && primero) { e.preventDefault(); primero(); } });
    const d = RV.dialogo({ ante: 'Buscar', titulo: 'En toda la colección', ancho: true, cuerpo: [h('label.buscador', { style: { maxWidth: 'none' } }, h('span.lupa', 'Buscar'), input), ul] });
  };
  function atajoBuscar() {
    const propio = ruta.v === 'seccion' ? RV.buscadorSeccion() : ruta.v === 'pc' ? RV.buscadorPc() : null;
    if (propio) { propio.focus(); propio.select(); window.scrollTo({ top: 0 }); } else RV.buscarTodo();
  }

  // ---------------------------------------------------------------- Teclado y ratón
  const escribiendo = (el) => !!el && (el.tagName === 'TEXTAREA' || el.isContentEditable || (el.tagName === 'INPUT' && !['range', 'button', 'checkbox', 'radio'].includes(el.type)));
  window.addEventListener('keydown', (e) => {
    const act = document.activeElement;
    if (e.key === 'Escape') {
      // En un texto largo, Esc solo sale del campo (para no perder lo escrito por un descuido)
      if (act && act.tagName === 'TEXTAREA') { act.blur(); e.preventDefault(); return; }
      if (RV.cerrarDialogo()) { e.preventDefault(); return; }
      if (escribiendo(act)) { act.blur(); }
      if (RV.volver()) e.preventDefault();
      return;
    }
    if (e.key === 'F11') { e.preventDefault(); api.toggleFullscreen(); return; }
    if (RV.hayDialogo()) return;
    if ((e.key === '/' && !escribiendo(act)) || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f')) {
      e.preventDefault();
      atajoBuscar();
      return;
    }
    if (e.altKey && e.key === 'ArrowLeft') { e.preventDefault(); RV.volver(); }
  });
  // El botón «atrás» del ratón
  window.addEventListener('mouseup', (e) => { if (e.button === 3 && !RV.hayDialogo()) { e.preventDefault(); RV.volver(); } });

  // ---------------------------------------------------------------- Lo que llega de Atrio en directo
  api.on('ronron', (s) => { D.setCuenta(s); RV.refrescar(); });
  api.on('library', (s) => { D.setPc(s); RV.refrescar(); });
  // El arte llega juego a juego (al arrancar, de toda la biblioteca): se redibuja despacio
  api.on('art', (g) => { D.setJuegoPc(g); if (ruta.v === 'portada' || ruta.v === 'pc' || ruta.v === 'ficha') refrescarDespacio(); });
  api.on('play', (p) => { D.setPlay(p); refrescarDespacio(); });
  api.on('game-ended', () => RV.refrescar());
  api.on('notice', (m) => RV.aviso(m));
  api.on('seg', (x) => { if (x && x.name) RV.aviso(`Partida de ${x.name}: ${RV.horas(x.min)}. Valórala en su ficha.`); });
  api.on('shot-taken', (x) => {
    RV.olvidarCapturas();
    if (x && x.by) RV.aviso(`Captura para ${x.by} · ${x.w}×${x.h}`);
    if (ruta.v === 'ficha') refrescarDespacio();
  });
  // Destello al capturar la pantalla (como el lanzador)
  api.on('shot-start', () => {
    const f = h('div.destello');
    document.body.append(f);
    window.requestAnimationFrame(() => f.classList.add('ya'));
    setTimeout(() => f.remove(), 700);
  });
  // Órdenes del móvil: abrir un juego en Atrio; las guías (webs) son cosa del lanzador clásico
  api.on('remote', (r) => {
    if (!r || !r.key) return;
    if (r.op === 'page') { while (RV.cerrarDialogo()) { /* cerrar todo */ } RV.abrirJuegoPc(r.key); }
    if (r.op === 'guide') RV.aviso('Las guías se abren en el lanzador clásico (Ajustes → Abrir el lanzador clásico).');
  });
  // En «Automático», si el sistema cambia de claro a oscuro, las muestras de color se redibujan
  try { window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => RV.refrescar()); } catch { /* */ }

  // ---------------------------------------------------------------- Arranque
  async function arrancar() {
    try {
      const s = await api.state();
      D.setPc(s);
      RV.aplicarTema((s.config || {}).revista);
    } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    try { D.setCuenta(await api.ronron()); } catch { /* sin cuenta */ }
    pintar({ y: 0 });
    // Con las letras ya cargadas, los titulares se miden bien
    if (document.fonts && document.fonts.load) {
      Promise.all(['700 100px Titular', 'italic 400 20px Titular', '600 20px Titular', '400 15px Texto', '900 12px Texto']
        .map((f) => document.fonts.load(f))).then(() => RV.ajustar(document)).catch(() => {});
    }
  }
  arrancar();
})(window.RV);
