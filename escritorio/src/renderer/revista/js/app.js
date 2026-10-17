'use strict';
/* global window, document, CSS, setTimeout, clearTimeout, requestAnimationFrame */
// La revista: la cabecera de periódico, la navegación entre páginas (con «volver»), los atajos de teclado,
// lo que llega de Atrio en directo (cuenta, biblioteca, partidas, avisos) y el arranque.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const api = RV.api;

  // ---------------------------------------------------------------- Navegación
  // Una ruta es { v: vista, …datos }. «Volver» desanda las rutas (y la posición en la página).
  // Dos modos: «juegos» (el lanzador: con lo que arranca Atrio) y «revista» (toda tu colección)
  RV.modo = 'juegos';
  const raiz = () => (RV.modo === 'juegos' ? { v: 'juegos' } : { v: 'portada' });
  let ruta = raiz();
  const pila = [];
  const igual = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  RV.ruta = () => ruta;
  RV.ir = (r, opts = {}) => {
    if (!opts.reemplazar && !igual(r, ruta)) pila.push({ ruta, y: window.scrollY, foco: claveFoco() });
    if (pila.length > 60) pila.shift();
    ruta = { ...r };
    pintar({ y: 0 });
  };
  RV.volver = () => {
    const prev = pila.pop();
    if (prev) { ruta = prev.ruta; pintar({ y: prev.y, foco: prev.foco }); return true; }
    if (!igual(ruta, raiz())) { ruta = raiz(); pintar({ y: 0 }); return true; }
    return false;
  };
  // Cambiar de modo: cada uno empieza en su portada (y «volver» no cruza de uno a otro)
  RV.cambiarModo = (m) => {
    const nuevo = m || (RV.modo === 'juegos' ? 'revista' : 'juegos');
    if (nuevo === RV.modo && igual(ruta, raiz())) return;
    RV.modo = nuevo;
    document.body.dataset.modo = nuevo;
    RV.escala();
    pila.length = 0;
    ruta = raiz();
    document.body.classList.add('cambia-modo');
    pintar({ y: 0 });
    setTimeout(() => document.body.classList.remove('cambia-modo'), 260);
  };
  // El elemento enfocado, para volver a él al redibujar o al volver atrás
  function claveFoco() {
    const a = document.activeElement;
    if (!a || a === document.body) return null;
    const pag = document.getElementById('pagina');
    if (!pag || !pag.contains(a)) return null;
    if (a.dataset && a.dataset.clave) return { clave: a.dataset.clave };
    const todos = [...pag.querySelectorAll('button, a[href], input, textarea, select, summary')];
    const i = todos.indexOf(a);
    return i >= 0 ? { i, txt: a.textContent.slice(0, 40) } : null;
  }
  function restaurarFoco(f) {
    const pag = document.getElementById('pagina');
    let el = null;
    if (f && f.clave) el = pag.querySelector(`[data-clave="${CSS.escape(f.clave)}"]`);
    if (!el && f && f.i != null) {
      const todos = [...pag.querySelectorAll('button, a[href], input, textarea, select, summary')];
      el = todos[f.i] && todos[f.i].textContent.slice(0, 40) === f.txt ? todos[f.i] : null;
    }
    if (el) el.focus({ preventScroll: true });
    return !!el;
  }
  RV.lanzador = () => {
    if (api.switchUi) api.switchUi('lanzador').catch((e) => RV.aviso(RV.limpiaError(e), 'error'));
    else RV.aviso('Esta versión de Atrio no puede cambiar de interfaz.', 'error');
  };

  const TITULOS = { juegos: 'Juegos', 'j-valorados': 'Valorados', 'j-juego': 'Juego', portada: 'Portada', diario: 'Diario', cifras: 'Cifras', pc: 'Tu PC', ajustes: 'Ajustes', descubrir: 'Descubrir', especial: 'Número especial' };
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
    // El foco (mando o teclado) se conserva al redibujar con datos nuevos
    const focoAntes = o.foco !== undefined ? o.foco : (act && pag.contains(act) && !foco ? claveFoco() : null);
    let nodo;
    try {
      nodo = (RV.vistas[ruta.v] || RV.vistas.portada)(ruta);
    } catch (e) {
      nodo = h('div.hoja', h('div.cabeza', h('div.ante', 'Fe de erratas')), RV.vacio(`No se ha podido mostrar esta página: ${RV.limpiaError(e)}`, true));
      try { api.log(`revista: ${e && e.stack ? e.stack : e}`); } catch { /* */ }
    }
    // Al redibujar con datos nuevos, la página no vuelve a entrar con su animación (solo al cambiar de página)
    if (o.refresco && nodo && nodo.classList) nodo.classList.add('sin-entrada');
    pag.replaceChildren(nodo);
    for (const el of RV.$$('[data-foco]', pag)) {
      if (campos.has(el.dataset.foco) && el.value !== campos.get(el.dataset.foco)) el.value = campos.get(el.dataset.foco);
    }
    cabecera();
    RV.ajustar(document);
    window.scrollTo(0, y);
    // Con mando o teclado siempre hay algo enfocado: lo de antes o lo principal de la página
    if (!foco && !RV.hayDialogo()) {
      const ok = focoAntes ? restaurarFoco(focoAntes) : false;
      if (!ok && RV.mando && (RV.mando.entrada() === 'mando' || RV.modo === 'juegos')) {
        const auto = pag.querySelector('[data-autofocus]');
        if (auto) auto.focus({ preventScroll: true });
        else if (RV.mando.entrada() === 'mando') RV.mando.primero(pag);
      }
    }
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
  RV.refrescar = RV.debounce(() => pintar({ refresco: true }), 50);
  const refrescarDespacio = RV.debounce(() => pintar({ refresco: true }), 1200);

  // ---------------------------------------------------------------- Cabecera de periódico
  function cabecera() {
    const cab = document.getElementById('cabecera');
    if (RV.modo === 'juegos' && RV.cabeceraJuegos) { RV.cabeceraJuegos(cab, ruta); return; }
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
      item('Descubrir', { v: 'descubrir', dom: 0 }, v === 'descubrir'),
      item('Cifras', { v: 'cifras' }, v === 'cifras'),
      item('Tu PC', { v: 'pc' }, v === 'pc'),
      item('Ajustes', { v: 'ajustes' }, v === 'ajustes'));
    const folio = h('div.cab-folio',
      h('button.enlace.acento', { type: 'button', onclick: () => RV.cambiarModo('juegos'), title: 'Botón Vista del mando (o C)' }, '⧉ Modo Juegos'),
      h('span.versal', `Nº ${RV.diaDelAno(hoy)} · ${RV.fmtLargo(hoy)}`),
      pila.length || v !== 'portada' ? h('button.enlace', { type: 'button', onclick: () => RV.volver(), title: 'Esc' }, '← Volver') : null,
      h('span.hueco'),
      h('button.enlace', { type: 'button', onclick: () => atajoBuscar(), title: 'Tecla /' }, 'Buscar /'),
      h('button.enlace.apagado', { type: 'button', onclick: () => RV.menu(), title: 'Botón Menú del mando (o S)' }, '☰ Menú'));
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
    if (RV.modo === 'juegos' && RV.buscarJuegos) { RV.buscarJuegos(); return; }
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
  api.on('library', (s) => { D.setPc(s); if (s && s.config) RV.mando.config(s.config.behavior); RV.refrescar(); });
  // El arte llega juego a juego (al arrancar, de toda la biblioteca): se redibuja despacio
  api.on('art', (g) => { D.setJuegoPc(g); if (['portada', 'pc', 'ficha', 'juegos', 'j-juego', 'j-valorados'].includes(ruta.v)) refrescarDespacio(); });
  api.on('play', (p) => { D.setPlay(p); refrescarDespacio(); });
  api.on('game-ended', () => RV.refrescar());
  api.on('notice', (m) => RV.aviso(m));
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
  // Órdenes del móvil: abrir un juego en Atrio o una guía (una web) para ese juego
  api.on('remote', (r) => {
    if (!r || !r.key) return;
    if (r.op === 'page') { while (RV.cerrarDialogo()) { /* cerrar todo */ } RV.abrirJuego(r.key); }
    if (r.op === 'guide' && RV.guia) { while (RV.cerrarDialogo()) { /* */ } RV.guia(r.key, r.url || null); }
  });
  // El foco de la ventana (jugando, Atrio queda detrás): el mando se mira menos y no navega
  api.on('winfocus', (v) => { if (RV.mando) RV.mando.foco(v); });

  // ---------------------------------------------------------------- Mando: lo que hace cada botón
  RV.mando.al({
    // LB / RB: la sección anterior o siguiente de la cabecera
    prev: () => (RV.secciones ? RV.secciones(-1) : null),
    next: () => (RV.secciones ? RV.secciones(1) : null),
    start: () => { if (!RV.hayDialogo()) RV.menu(); else RV.cerrarDialogo(); },
    select: () => { while (RV.cerrarDialogo()) { /* */ } RV.cambiarModo(); },
    x: () => { if (!RV.hayDialogo()) atajoBuscar(); },
    // Y: las opciones de lo que está enfocado (un juego, por ejemplo)
    y: (el) => {
      if (RV.guiaY && RV.guiaY()) return;
      if (RV.hayDialogo()) return;
      const o = el && el.closest('[data-opciones]');
      if (o && typeof o.__opciones === 'function') { o.__opciones(); return; }
      // Cualquier enlace a una ficha (portada, diario, cifras…) y, dentro de una ficha, la propia ficha
      const f = el && el.closest('[data-ficha]');
      const [k, t, id] = f ? f.dataset.ficha.split(':') : ruta.v === 'ficha' ? [ruta.k, ruta.t, ruta.id] : [];
      const kind = k != null ? D.tipo(Number(k)) : null;
      const item = kind ? D.buscar(kind, t, Number(id)) || D.buscar(kind, t, id) : null;
      if (item && RV.opcionesFicha) RV.opcionesFicha(kind, t, item);
    },
  });
  // LB / RB en la revista: recorrer las secciones de la cabecera
  let dirT = null;
  RV.secciones = (paso) => {
    if (RV.guiaNav && RV.guiaNav(paso)) return;
    if (RV.arteNav && RV.arteNav(paso)) return;
    if (RV.hayDialogo()) return;
    // La página nueva entra desde el lado hacia el que vas
    document.body.dataset.dir = paso < 0 ? 'izq' : 'der';
    clearTimeout(dirT);
    dirT = setTimeout(() => { delete document.body.dataset.dir; }, 320);
    if (ruta.v === 'ajustes' && RV.seccionAjustes) { RV.seccionAjustes(paso); return; }
    if (RV.modo === 'juegos' && RV.seccionesJuegos) { RV.seccionesJuegos(paso); return; }
    const items = RV.$$('#cabecera .cab-item');
    if (!items.length) return;
    let i = items.findIndex((b) => b.getAttribute('aria-current') === 'page');
    i = (i + paso + items.length) % items.length;
    items[i].click();
    requestAnimationFrame(() => { const b = RV.$$('#cabecera .cab-item')[i]; if (b && RV.mando.entrada() === 'mando') b.focus({ preventScroll: true }); });
  };
  // En «Automático», si el sistema cambia de claro a oscuro, las muestras de color se redibujan
  try { window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => RV.refrescar()); } catch { /* */ }

  // ---------------------------------------------------------------- Arranque
  async function arrancar() {
    document.body.dataset.modo = RV.modo;
    document.body.dataset.entrada = 'raton';
    RV.escala();
    try {
      const s = await api.state();
      D.setPc(s);
      RV.aplicarTema((s.config || {}).revista);
      RV.mando.config((s.config || {}).behavior);
    } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    try { D.setCuenta(await api.ronron()); } catch { /* sin cuenta */ }
    try { if (api.fotoList) D.setFotos(await api.fotoList()); } catch { /* */ }
    pintar({ y: 0 });
    // Con las letras ya cargadas, los titulares se miden bien
    if (document.fonts && document.fonts.load) {
      Promise.all(['700 100px Titular', 'italic 400 20px Titular', '600 20px Titular', '400 15px Texto', '900 12px Texto']
        .map((f) => document.fonts.load(f))).then(() => RV.ajustar(document)).catch(() => {});
    }
  }
  arrancar();
  // Para las capturas automáticas (ATRIO_SHOTS): cada paso del plan puede ir a una página o cambiar de interfaz
  window.__test = async (paso) => {
    if (paso && paso.modo) RV.cambiarModo(paso.modo);
    if (paso && paso.ir) RV.ir(paso.ir);
    if (paso && paso.ui && api.switchUi) await api.switchUi(paso.ui);
    return RV.ruta().v;
  };
})(window.RV);
