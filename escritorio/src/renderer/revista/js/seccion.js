'use strict';
/* global window */
// La sección de cada categoría (RevistaLibrary del móvil): pestañas En curso / En lista / Terminados / Dejados,
// buscador, orden y una lista de texto con el título grande, versalitas debajo y la nota gigante a la derecha.
// «+ Añadir»: videojuegos con el buscador de SteamGridDB; el resto, por nombre a mano.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // Lo que se recuerda de cada sección mientras la revista está abierta
  const estado = new Map(); // id de categoría -> { tab, q, orden, fav }
  const est = (k) => {
    if (!estado.has(k.id)) estado.set(k.id, { tab: 0, q: '', orden: 'recientes', fav: false });
    return estado.get(k.id);
  };
  // Pestañas: 0 en curso (status 1), 1 en lista (status 0), 2 terminados (valorados), 3 dejados (status 2)
  const STATUS_DE = [1, 0, null, 2];

  function filas(k, s) {
    const q = s.q.trim().toLowerCase();
    const casa = (o) => !q || [o.name, o.creator, o.platform].some((x) => String(x || '').toLowerCase().includes(q));
    if (s.tab === 2) {
      const l = D.valorados(k).filter((g) => (!s.fav || g.fav) && casa(g));
      return D.ordenar(l, 'g', s.orden).map((item) => ({ item, t: 'g' }));
    }
    const st = STATUS_DE[s.tab];
    const l = D.fichas(k).filter((p) => num(p.status) === st && casa(p));
    return D.ordenar(l, 'p', s.orden).map((item) => ({ item, t: 'p' }));
  }

  // Una línea del índice: título grande, versalitas y la nota a la derecha
  function linea(k, { item, t }) {
    const sub = [item.creator, item.platform];
    if (t === 'p') {
      if (k.isBook && num(item.chapter) > 0) sub.push(`capítulo ${item.chapter}`);
      else if (!k.isBook && num(item.minutes) > 0) sub.push(RV.horas(item.minutes));
      const n = (item.entries || []).length;
      if (n) sub.push(RV.plural(n, 'apunte', 'apuntes'));
      const pend = D.sinValorar(item).length;
      if (pend) sub.push(`${pend} por valorar`);
    } else if (num(item.end) > 0) sub.push(RV.fmtDia(item.end));
    const pct = t === 'p' ? D.porcentaje(item) : null;
    const nota = D.nota(item, t);
    const pcg = D.pcDe(k, item);
    const fila = h('button.item', { type: 'button', 'data-opciones': '', onclick: () => RV.ir({ v: 'ficha', k: k.id, t, id: item.id }) },
      h('span',
        h('span.item-tit.ajusta', { 'data-min': '16' }, t === 'g' && item.fav ? h('span.estrella', { 'aria-label': 'Favorito' }, '★') : null, item.name),
        h('span.versal', { style: { display: 'block' } }, sub.filter(Boolean).join(' · ') || (t === 'g' ? k.doneOne : D.estado(k, item, t))),
        pct !== null && pct > 0 ? h('span.hecho', { style: { display: 'block' } }, `${pct} % HECHO`) : null,
        pcg && D.activoPc(pcg.key) ? h('span.en-marcha', { style: { display: 'block' } }, '● Abierto en este PC') : null),
      nota != null ? h(`span.cifra${t === 'p' ? '.media' : ''}`, String(nota), t === 'p' ? h('small', 'Media') : null) : h('span'));
    fila.__opciones = () => RV.opcionesFicha(k, t, item);
    // Instalado en este PC: «Jugar →» al lado, sin entrar en la ficha
    if (!pcg || D.activoPc(pcg.key)) return fila;
    return h('div.item-fila', fila, h('button.enlace.acento.grande', { type: 'button', onclick: () => RV.jugar(pcg), title: `Jugar a ${pcg.name}` }, 'Jugar →'));
  }

  RV.vistas.seccion = (ruta) => {
    const k = D.tipo(ruta.k);
    if (!k) return h('div.hoja', RV.vacio('Esta categoría ya no existe.', true));
    const s = est(k);
    if (Number.isInteger(ruta.tab)) { s.tab = ruta.tab; delete ruta.tab; }
    const root = h('div.hoja.seccion-pag');
    const total = D.valorados(k).length + D.fichas(k).length;

    root.append(h('header.cabeza',
      h('div.ante', `Sección · ${RV.plural(total, 'ficha', 'fichas')}`),
      h('div.cabeza-fila',
        h('h1.titular.ajusta', { 'data-min': '36' }, k.title),
        D.conCuenta() ? h('div.herramientas',
          h('button.enlace', { type: 'button', onclick: () => RV.ir({ v: 'cifras', k: k.id }) }, 'Cifras'),
          h('button.enlace.acento.grande', { type: 'button', onclick: () => anadir(k, s.tab) }, '+ Añadir')) : null),
      h('div.filete')));

    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (en Ajustes) para ver aquí tus fichas.', true),
        h('button.boton-tinta', { type: 'button', onclick: () => RV.ir({ v: 'ajustes' }) }, 'Ir a Ajustes →'));
      return root;
    }

    const nombres = [k.statuses[1], k.statuses[0], k.doneWord, k.statuses[2]];
    const cuentas = [1, 0, null, 2].map((st) => (st === null ? D.valorados(k).length : D.fichas(k).filter((p) => num(p.status) === st).length));
    const lista = h('div');
    const pintarLista = () => {
      const fs = filas(k, s);
      const out = [];
      if (!fs.length) {
        out.push(RV.vacio(s.q.trim() ? 'Nada coincide con la búsqueda.' : s.fav ? 'Ningún favorito por aquí.' : 'Esta sección está en blanco, de momento.', true));
        if (!s.q.trim() && s.tab !== 3) out.push(h('button.enlace.acento.grande', { type: 'button', onclick: () => anadir(k, s.tab) }, '+ Añadir el primero'));
      } else {
        out.push(h(`div.indice${fs.length > 6 ? '.dos' : ''}`, fs.map((f) => linea(k, f))));
        if (s.q.trim()) out.push(h('p.versal.cuenta-lista', RV.plural(fs.length, 'resultado', 'resultados')));
      }
      lista.replaceChildren(...out);
      RV.ajustar(lista);
    };

    const pestanas = h('div.pestanas', { role: 'tablist' }, nombres.map((n, i) => h('button.pestana', {
      type: 'button', role: 'tab', 'aria-selected': String(s.tab === i),
      onclick: () => { s.tab = i; RV.refrescar(); },
    }, n, h('span.n', String(cuentas[i])))));
    const input = h('input', { type: 'search', placeholder: `Buscar en ${k.title.toLowerCase()}`, 'data-foco': `buscar-${k.id}`, 'aria-label': 'Buscar', value: s.q, spellcheck: 'false' });
    input.addEventListener('input', () => { s.q = input.value; pintarLista(); });
    input.addEventListener('keydown', (e) => { if (e.key === 'Escape' && input.value) { e.stopPropagation(); input.value = ''; s.q = ''; pintarLista(); } });
    const buscador = h('label.buscador', h('span.lupa', 'Buscar'), input);
    const ordenes = h('div.orden', [['recientes', 'Recientes'], ['nota', 'Nota'], ['nombre', 'Nombre']].map(([v, n]) => h('button.enlace.apagado', {
      type: 'button', 'aria-pressed': String(s.orden === v), onclick: () => { s.orden = v; RV.refrescar(); },
    }, n)));
    if (s.tab === 2) {
      ordenes.append(h('button.enlace.apagado', { type: 'button', 'aria-pressed': String(s.fav), onclick: () => { s.fav = !s.fav; RV.refrescar(); } }, '★ Favoritos'));
    }
    root.append(h('div.barra', pestanas, h('span.hueco'), ordenes, buscador));
    root.append(lista);
    pintarLista();
    return root;
  };
  // Para el atajo «/»: el buscador de la sección
  RV.buscadorSeccion = () => RV.$('.seccion-pag .buscador input');

  // ---------------------------------------------------------------- Añadir
  // tab: la pestaña desde la que se añade (en curso → empieza ya; si no, a la lista)
  function anadir(k, tab) {
    if (!D.conCuenta()) { RV.ir({ v: 'ajustes' }); return; }
    if (k.isGame) anadirJuego(k, tab === 0 ? 1 : 0);
    else if (RV.red.buscadorDe(k)) anadirBuscando(k, tab === 0 ? 1 : 0);
    else anadirAMano(k, tab === 0 ? 1 : 0);
  }
  RV.anadir = anadir;

  // Si ya lo tienes, se abre su ficha
  function yaLoTienes(k, id) {
    const p = D.buscar(k, 'p', id);
    if (p) { RV.aviso(`Ya lo tenías: ${D.estado(k, p, 'p').toLowerCase()}`); RV.ir({ v: 'ficha', k: k.id, t: 'p', id }); return true; }
    const g = D.buscar(k, 'g', id);
    if (g) { RV.aviso(`Ya lo tenías: ${k.doneOne.toLowerCase()}`); RV.ir({ v: 'ficha', k: k.id, t: 'g', id }); return true; }
    return false;
  }

  async function crear(k, base, status) {
    const item = D.completarFicha({
      ...base, status, entries: [], memos: [], segs: [], start: status === 1 ? RV.hoy() : 0,
    });
    const ok = await RV.editar({ op: 'savePlay', kind: k.id, item }, status === 1 ? `${item.name}: ${k.statuses[1].toLowerCase()} desde hoy` : `${item.name}, a tu lista`);
    if (ok) RV.ir({ v: 'ficha', k: k.id, t: 'p', id: item.id });
  }

  // Videojuegos: el mismo buscador que el móvil (SteamGridDB), así la ficha coincide en todos lados
  function anadirJuego(k, statusPorDefecto) {
    const cfg = D.config();
    if (!cfg.sgdbKey) {
      RV.dialogo({
        ante: 'Añadir un videojuego', titulo: 'Falta la clave de SteamGridDB',
        cuerpo: h('p.texto', 'Para buscar juegos, Atrio usa SteamGridDB (como el móvil). Añade tu clave gratuita en Ajustes y vuelve a probar.'),
        acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, { texto: 'Ir a Ajustes →', principal: true, fn: (d) => { d.cerrar(); RV.ir({ v: 'ajustes' }); } }],
      });
      return;
    }
    const input = RV.entrada('', { placeholder: 'Nombre del juego', 'data-autofocus': true });
    const res = h('ul.resultados');
    const msg = h('p.vacio');
    let token = 0;
    const buscar = async () => {
      const q = input.value.trim();
      if (!q) return;
      const my = ++token;
      res.replaceChildren();
      msg.textContent = 'Buscando en SteamGridDB…';
      try {
        const list = await RV.api.sgdbSearch(q);
        if (my !== token) return;
        msg.textContent = list.length ? '' : 'Sin resultados. Prueba con menos palabras o con el nombre en inglés.';
        for (const r of list) {
          const elegir = async (status) => {
            if (yaLoTienes(k, r.id)) { d.cerrar(); return; }
            msg.textContent = `Preparando ${r.name}…`;
            // Portada, fondo y logo de SteamGridDB (como hace el lanzador): se guardan como dirección y se ven también en el móvil
            const art = await RV.api.ronronArt(r.id).catch(() => ({ cover: '', hero: '', logo: '' }));
            d.cerrar();
            await crear(k, { id: Number(r.id), name: r.name, cover: art.cover || '', hero: art.hero || '', logo: art.logo || '', platform: '' }, status);
          };
          res.append(h('li', h('div.resultado',
            h('span', h('span.tit', r.name), r.year ? h('span.versal', { style: { display: 'block' } }, String(r.year)) : null),
            h('span.acc',
              h('button.enlace', { type: 'button', onclick: () => elegir(1) }, `${k.statuses[1]} →`),
              h('button.enlace.acento', { type: 'button', onclick: () => elegir(0) }, 'A mi lista +')))));
        }
      } catch (e) {
        if (my === token) msg.textContent = RV.limpiaError(e);
      }
    };
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } });
    const d = RV.dialogo({
      ante: 'Añadir un videojuego', titulo: statusPorDefecto === 1 ? '¿A qué estás jugando?' : '¿Qué quieres jugar?', ancho: true,
      cuerpo: [h('div', { style: { display: 'flex', gap: '18px', alignItems: 'flex-end' } }, h('div', { style: { flex: '1' } }, RV.campo('Buscar en SteamGridDB', input)),
        h('button.boton-tinta', { type: 'button', onclick: buscar }, 'Buscar')), msg, res],
    });
  }

  // Libros, pelis y series: con el mismo buscador que el móvil (Open Library, TMDB o TVmaze), para que la ficha
  // tenga el mismo id en todos lados y no salga repetida. Si no aparece, a mano.
  function anadirBuscando(k, statusPorDefecto) {
    const buscador = RV.red.buscadorDe(k);
    const fuente = RV.red.fuenteDe(k);
    const input = RV.entrada('', { placeholder: k.isBook ? 'Título o autor' : 'Título', 'data-autofocus': true });
    const res = h('ul.resultados');
    const msg = h('p.vacio');
    let token = 0;
    const buscar = async () => {
      const q = input.value.trim();
      if (!q) return;
      const my = ++token;
      res.replaceChildren();
      msg.textContent = `Buscando en ${fuente}…`;
      try {
        const list = await buscador(q);
        if (my !== token) return;
        msg.textContent = list.length ? '' : 'Sin resultados. Prueba con otras palabras (o créala a mano).';
        for (const r of list.slice(0, 20)) {
          const elegir = async (status) => {
            if (yaLoTienes(k, r.id)) { d.cerrar(); return; }
            d.cerrar();
            // El director (pelis) o los creadores (series), como hace el móvil al añadir
            let creator = r.creator || '';
            if (!creator && k.isMovie) creator = await RV.red.director(r.id).catch(() => '');
            if (!creator && k.isTv) creator = await RV.red.creadores(r.id).catch(() => '');
            await crear(k, { id: Number(r.id), name: r.name, cover: r.cover || '', hero: '', creator, pages: r.pages || 0 }, status);
          };
          res.append(h('li', h('div.resultado',
            h('span', h('span.tit', r.name), r.sub ? h('span.versal', { style: { display: 'block' } }, r.sub) : null),
            h('span.acc',
              h('button.enlace', { type: 'button', onclick: () => elegir(1) }, `${k.statuses[1]} →`),
              h('button.enlace.acento', { type: 'button', onclick: () => elegir(0) }, 'A mi lista +')))));
        }
      } catch (e) {
        if (my === token) msg.textContent = RV.limpiaError(e);
      }
    };
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } });
    const d = RV.dialogo({
      ante: `Añadir · ${k.title}`, titulo: statusPorDefecto === 1 ? `¿Qué ${k.isBook ? 'estás leyendo' : 'estás viendo'}?` : `¿Qué ${k.isBook ? 'quieres leer' : 'quieres ver'}?`, ancho: true,
      cuerpo: [h('div', { style: { display: 'flex', gap: '18px', alignItems: 'flex-end' } }, h('div', { style: { flex: '1' } }, RV.campo(`Buscar en ${fuente}`, input)),
        h('button.boton-tinta', { type: 'button', onclick: buscar }, 'Buscar')), msg, res],
      acciones: ['hueco', { texto: 'Crear a mano', fn: (x) => { x.cerrar(); anadirAMano(k, statusPorDefecto); } }, { texto: 'Cerrar', fn: (x) => x.cerrar() }],
    });
  }

  // El resto (categorías propias, o si no aparece en el buscador): por nombre, a mano
  function anadirAMano(k, statusPorDefecto) {
    const nombre = RV.entrada('', { placeholder: 'Título', 'data-autofocus': true });
    const creador = RV.entrada('', { placeholder: k.creatorWord });
    const msg = h('p.mensaje');
    let status = statusPorDefecto;
    const opts = h('div.opciones');
    const pintarOpts = () => opts.replaceChildren(...[[0, 'A mi lista'], [1, k.statuses[1]]].map(([v, n]) => h('button.opcion', {
      type: 'button', 'aria-pressed': String(status === v), onclick: () => { status = v; pintarOpts(); },
    }, n)));
    pintarOpts();
    const guardar = async () => {
      const name = nombre.value.trim().replace(/\s+/g, ' ');
      if (!name) { msg.textContent = 'Falta el título.'; nombre.focus(); return; }
      if (!RV.normName(name)) { msg.textContent = 'El título necesita alguna letra o número.'; return; }
      // Mismo id que daría el móvil al crearla a mano (customItemId)
      const id = RV.idPropio(name);
      d.cerrar();
      if (yaLoTienes(k, id)) return;
      await crear(k, { id, name, cover: '', hero: '', creator: creador.value.trim() }, status);
    };
    for (const i of [nombre, creador]) i.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); guardar(); } });
    const d = RV.dialogo({
      ante: `Añadir · ${k.title}`, titulo: `Nueva ficha de ${k.unit}`,
      cuerpo: [RV.campo('Título', nombre), RV.campo(k.creatorWord, creador), h('div.campo', h('span', 'Dónde va'), opts), msg,
        h('p.versal', 'Sin buscador: se crea a mano, con el mismo id que le daría el móvil.')],
      acciones: ['hueco', { texto: 'Cancelar', fn: (x) => x.cerrar() }, { texto: 'Crear la ficha', principal: true, fn: guardar }],
    });
  }
})(window.RV);
