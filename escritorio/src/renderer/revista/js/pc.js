'use strict';
/* global window */
// Tu PC: la biblioteca del lanzador (Steam, Heroic, Lutris, emuladores y juegos propios) como el índice de una
// revista, con los favoritos primero y «Jugar →» en cada línea. Funciona igual sin cuenta.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  RV.vistas = RV.vistas || {};
  const est = { q: '', orden: 'recientes', origen: 'todos' };

  // Lanzar un juego del PC (también desde la portada y las fichas)
  RV.jugar = async (g) => {
    if (!g) return;
    RV.aviso(`Abriendo ${g.name}…`);
    try { await RV.api.launch(g.key); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
  };

  function linea(g) {
    const ms = D.msPc(g);
    const sub = [g.sourceLabel || g.source, ms >= 60000 ? RV.horas(ms / 60000) : '', g.lastPlayed ? `jugado ${RV.hace(g.lastPlayed)}` : 'sin jugar desde Atrio'];
    const ficha = D.conCuenta() ? fichaDe(g) : null;
    const activo = D.activoPc(g.key);
    return h('div.item',
      h('span',
        h('span.item-tit.ajusta', { 'data-min': '16' }, g.favorite ? h('span.estrella', { 'aria-label': 'Favorito' }, '★') : null, g.name),
        h('span.versal', { style: { display: 'block' } }, sub.filter(Boolean).join(' · ')),
        activo ? h('span.en-marcha', { style: { display: 'block' } }, '● Abierto ahora') : null),
      h('span.lado',
        activo ? h('span.versal', 'En marcha') : h('button.enlace.acento.grande', { type: 'button', onclick: () => RV.jugar(g) }, 'Jugar →'),
        ficha ? h('button.enlace.apagado', { type: 'button', onclick: () => RV.ir({ v: 'ficha', k: 0, t: ficha.t, id: ficha.item.id }) }, 'Ficha →') : null));
  }
  // La ficha de tu cuenta que corresponde a un juego del PC (en curso primero, como hace el lanzador)
  function fichaDe(g) {
    const k = D.tipo(0);
    const id = Number(g.sgdbKnown) || null;
    const n = RV.normName(g.name);
    const hallar = (l) => (id && l.find((o) => Number(o.id) === id)) || l.find((o) => RV.normName(o.name) === n || RV.normName(o.name) === RV.normName(g.originalName));
    const p = hallar(D.fichas(k));
    if (p && Number(p.status) === 1) return { t: 'p', item: p };
    const v = hallar(D.valorados(k));
    if (v) return { t: 'g', item: v };
    return p ? { t: 'p', item: p } : null;
  }

  RV.vistas.pc = (ruta) => {
    if (RV.soloRevista) return RV.vistas.portada(ruta);
    const root = h('div.hoja.pc');
    const todos = D.juegosPc();
    root.append(h('header.cabeza',
      h('div.ante', `La biblioteca del lanzador · ${RV.plural(todos.length, 'juego', 'juegos')}`),
      h('div.cabeza-fila',
        h('h1.titular.ajusta', { 'data-min': '36' }, 'Tu PC'),
        h('div.herramientas',
          h('button.enlace', {
            type: 'button',
            onclick: async (e) => {
              const b = e.currentTarget;
              b.disabled = true;
              b.textContent = 'Leyendo…';
              try { D.setPc(await RV.api.rescan()); RV.aviso('Bibliotecas leídas de nuevo'); } catch (err) { RV.aviso(RV.limpiaError(err), 'error'); }
              RV.refrescar();
            },
          }, 'Volver a leer'),
          h('button.enlace.acento', { type: 'button', onclick: () => RV.cambiarModo('juegos') }, '⧉ Modo Juegos →'))),
      h('div.filete')));

    if (!todos.length) {
      root.append(RV.vacio('No he encontrado juegos en este PC. En Ajustes → Bibliotecas eliges dónde buscar y añades los tuyos.', true));
      return root;
    }

    const origenes = [...new Map(todos.map((g) => [g.source, g.sourceLabel || g.source])).entries()];
    if (est.origen !== 'todos' && !origenes.some(([s]) => s === est.origen)) est.origen = 'todos';
    const lista = h('div');
    const pintar = () => {
      const q = RV.normName(est.q);
      let l = todos.filter((g) => (est.origen === 'todos' || g.source === est.origen) && (!q || RV.normName(g.name).includes(q)));
      const nombre = (a, b) => String(a.sortName || a.name).localeCompare(String(b.sortName || b.name), 'es', { sensitivity: 'base', numeric: true });
      if (est.orden === 'nombre') l.sort(nombre);
      else if (est.orden === 'horas') l.sort((a, b) => D.msPc(b) - D.msPc(a) || nombre(a, b));
      else l.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0) || nombre(a, b));
      const favs = l.filter((g) => g.favorite);
      const resto = l.filter((g) => !g.favorite);
      const out = [];
      if (!l.length) out.push(RV.vacio('Nada coincide con la búsqueda.', true));
      if (favs.length) out.push(h('section', { style: { marginTop: '22px' } }, RV.seccion(`Favoritos · ${favs.length}`), h(`div.indice${favs.length > 6 ? '.dos' : ''}`, favs.map(linea))));
      if (resto.length) out.push(h('section', { style: { marginTop: '22px' } }, RV.seccion(favs.length ? `El resto · ${resto.length}` : `Todos · ${resto.length}`), h(`div.indice${resto.length > 6 ? '.dos' : ''}`, resto.map(linea))));
      lista.replaceChildren(...out);
      RV.ajustar(lista);
    };

    const input = h('input', { type: 'search', placeholder: 'Buscar en tu PC', 'data-foco': 'buscar-pc', 'aria-label': 'Buscar', value: est.q, spellcheck: 'false' });
    input.addEventListener('input', () => { est.q = input.value; pintar(); });
    input.addEventListener('keydown', (e) => { if (e.key === 'Escape' && input.value) { e.stopPropagation(); input.value = ''; est.q = ''; pintar(); } });
    const origen = h('div.pestanas', [['todos', 'Todos'], ...origenes].map(([id, n]) => h('button.pestana', {
      type: 'button', 'aria-selected': String(est.origen === id), onclick: () => { est.origen = id; RV.refrescar(); },
    }, n)));
    const orden = h('div.orden', [['recientes', 'Recientes'], ['nombre', 'Nombre'], ['horas', 'Horas']].map(([v, n]) => h('button.enlace.apagado', {
      type: 'button', 'aria-pressed': String(est.orden === v), onclick: () => { est.orden = v; RV.refrescar(); },
    }, n)));
    root.append(h('div.barra', origen, h('span.hueco'), orden, h('label.buscador', h('span.lupa', 'Buscar'), input)));
    root.append(lista);
    pintar();
    return root;
  };
  RV.buscadorPc = () => RV.$('.pc .buscador input');
  // Desde el móvil («Abrir en Atrio»): la ficha del juego si la tienes; si no, el juego en Tu PC
  RV.abrirJuegoPc = (key) => {
    const g = (D.pc().games || []).find((x) => x.key === key || (x.aliasKeys || []).includes(key));
    if (!g) return false;
    const f = D.conCuenta() ? fichaDe(g) : null;
    if (f) RV.ir({ v: 'ficha', k: 0, t: f.t, id: f.item.id });
    else { est.q = g.name; est.origen = 'todos'; RV.ir({ v: 'pc' }); }
    return true;
  };
})(window.RV);
