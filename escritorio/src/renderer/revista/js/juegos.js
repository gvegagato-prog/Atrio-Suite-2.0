'use strict';
/* global window, document, setInterval, clearInterval, setTimeout, clearTimeout, requestAnimationFrame */
// Modo Juegos: el lanzador de Atrio con la estética de la revista. Es con lo que arranca Atrio, a pantalla
// completa. Arriba, la cabecera con tres secciones (Valorados · Juegos · Revista: LB y RB cambian de una a
// otra); en Juegos, el último que has jugado en portada, «Seguir jugando» y toda la biblioteca en portadas.
// Cada juego tiene su página: Jugar, guías, tu nota y tus apuntes si lo tienes en la cuenta.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};
  const est = { orden: 'recientes', origen: 'todos', heroe: null, tiras: {} };

  const SECCIONES = [['j-valorados', 'Valorados'], ['juegos', 'Juegos'], ['revista', 'Revista']];
  const remota = (u) => { const x = D.imagen(u); return x && /^(https?|atrio|data):/i.test(x) ? x : ''; };
  const portadaDe = (g) => remota((g.art || {}).cover) || remota((g.art || {}).wide) || '';
  const fondoDe = (g) => { const a = g.art || {}; return remota(a.herohd) || remota(a.hero) || remota(a.wide) || remota(a.cover) || ''; };
  const juego = (key) => D.juegosPc().find((g) => g.key === key || (g.aliasKeys || []).includes(key)) || null;
  RV.juegoPc = juego;

  // La ficha de tu cuenta de un juego del PC (por el id de SteamGridDB o por el nombre)
  function fichaDe(g) {
    const k = D.tipo(0);
    if (!k || !D.conCuenta()) return null;
    const id = Number(g.sgdbKnown) || null;
    const n = RV.normName(g.name);
    const hallar = (l) => (id && l.find((o) => Number(o.id) === id)) || l.find((o) => RV.normName(o.name) === n || RV.normName(o.name) === RV.normName(g.originalName));
    const p = hallar(D.fichas(k));
    if (p && num(p.status) === 1) return { t: 'p', item: p, kind: k };
    const v = hallar(D.valorados(k));
    if (v) return { t: 'g', item: v, kind: k };
    return p ? { t: 'p', item: p, kind: k } : null;
  }
  RV.fichaDeJuego = fichaDe;

  // ---------------------------------------------------------------- Cabecera
  const reloj = () => new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' }).format(new Date());
  RV.cabeceraJuegos = (cab, ruta) => {
    const activa = ruta.v === 'j-juego' ? 'juegos' : ruta.v;
    const ahora = D.jugandoAhora();
    cab.replaceChildren(h('div.hoja.j-cab',
      h('div.j-cab-fila',
        h('button.j-marca', { type: 'button', onclick: () => RV.ir({ v: 'juegos' }), 'aria-label': 'Atrio: juegos' }, 'ATRIO'),
        h('nav.j-secciones', { 'aria-label': 'Secciones' }, SECCIONES.map(([v, t]) => h('button.j-sec', {
          type: 'button', 'aria-current': activa === v ? 'page' : null,
          onclick: () => (v === 'revista' ? RV.cambiarModo('revista') : RV.ir({ v })),
        }, t))),
        h('div.j-cab-der',
          ahora ? h('span.j-directo', `● ${ahora.game.name}`) : null,
          h('span.j-reloj', reloj()),
          h('button.enlace.apagado', { type: 'button', onclick: () => RV.menu(), title: 'Botón Menú (o S)' }, '☰'))),
      h('div.filete')));
  };
  setInterval(() => { const r = document.querySelector('.j-reloj'); if (r) r.textContent = reloj(); }, 20000);
  RV.seccionesJuegos = (paso) => {
    const r = RV.ruta();
    const cur = r.v === 'j-juego' ? 'juegos' : r.v;
    let i = SECCIONES.findIndex(([v]) => v === cur);
    if (i < 0) i = 1;
    i = Math.max(0, Math.min(SECCIONES.length - 1, i + paso));
    const v = SECCIONES[i][0];
    if (v === 'revista') RV.cambiarModo('revista');
    else if (v !== cur) RV.ir({ v }, { reemplazar: r.v !== 'j-juego' });
  };

  // ---------------------------------------------------------------- Piezas
  const horas = (g) => { const ms = D.msPc(g); return ms >= 60000 ? RV.horas(ms / 60000) : ''; };
  const visto = (g) => (g.lastPlayed ? `jugado ${RV.hace(g.lastPlayed)}` : 'sin estrenar');

  // Y: las opciones de un juego
  function opciones(g) {
    const f = fichaDe(g);
    const ul = h('ul.j-opciones');
    const op = (t, fn, extra) => ul.append(h('li', h('button.resultado', { type: 'button', style: { width: '100%', textAlign: 'left' }, onclick: () => { d.cerrar(); fn(); } },
      h('span.tit', t), extra ? h('span.versal', extra) : h('span'))));
    if (!D.activoPc(g.key)) op('Jugar', () => RV.jugar(g), 'A');
    op('Abrir su página', () => RV.ir({ v: 'j-juego', key: g.key }));
    op(g.favorite ? 'Quitar de favoritos' : 'Añadir a favoritos', () => marcar(g, { favorite: !g.favorite }, g.favorite ? 'Quitado de favoritos' : 'En favoritos'));
    if (f) op('Su ficha en la revista', () => { RV.cambiarModo('revista'); RV.ir({ v: 'ficha', k: 0, t: f.t, id: f.item.id }); });
    op('Guías y webs', () => RV.guia(g.key, null));
    op('Imágenes: portada, fondo, logo…', () => RV.arteJuego(g.key));
    op('Cambiar el nombre', () => RV.renombrarJuego(g.key));
    op('Ocultar de la biblioteca', () => marcar(g, { hidden: true }, `${g.name} oculto (lo puedes recuperar en Ajustes → Juegos ocultos)`));
    const d = RV.dialogo({ ante: 'Opciones', titulo: g.name, cuerpo: ul });
  }
  RV.opcionesJuego = opciones;
  async function marcar(g, patch, msg) {
    try {
      const ng = await RV.api.override(g.key, patch);
      if (ng) D.setJuegoPc(ng);
      RV.aviso(msg);
      RV.refrescar();
    } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
  }

  // Sin imagen: una portada de tipografía (el nombre en grande, como una tapa de la revista)
  function tapa(g, cls) {
    return h(`div.${cls}.sin-imagen.j-tapa`, h('span.j-tapa-ini', String(g.name || '?').trim().charAt(0).toUpperCase()),
      h('span.j-tapa-ante', g.sourceLabel || g.source || ''), h('span.j-tapa-tit', g.name));
  }
  // Las portadas se pintan en el mismo fotograma que el resto (decodificación síncrona: ya están en caché);
  // solo las de la biblioteca, más abajo, se cargan al acercarse
  function arte(g, src, cls, perezosa) {
    if (!src) return tapa(g, cls);
    const img = h('img', { alt: '', decoding: 'sync', loading: perezosa ? 'lazy' : null, draggable: 'false' });
    const box = h(`figure.${cls}`, img);
    img.addEventListener('error', () => box.replaceWith(tapa(g, cls)));
    img.src = src;
    return box;
  }

  // Una portada de la biblioteca (vertical, con el título debajo)
  function carta(g, o = {}) {
    const activo = D.activoPc(g.key);
    const b = h(`button.j-carta${o.ancha ? '.ancha' : ''}`, {
      type: 'button', 'data-clave': `${o.zona || 'b'}:${g.key}`, 'data-key': g.key, 'data-opciones': '', onclick: () => RV.ir({ v: 'j-juego', key: g.key }),
      'aria-label': g.name, 'data-inicio': o.inicio ? '' : null,
    },
    arte(g, o.ancha ? fondoDe(g) : portadaDe(g), o.ancha ? 'j-img-ancha' : 'j-img', o.perezosa),
    h('span.j-carta-txt',
      h('span.j-carta-tit', g.favorite ? h('span.estrella', '★') : null, g.name),
      h('span.versal', activo ? '● Abierto ahora' : [g.sourceLabel || g.source, horas(g)].filter(Boolean).join(' · '))));
    b.__opciones = () => opciones(g);
    return b;
  }

  // ---------------------------------------------------------------- El escaparate (la portada del lanzador)
  // Arriba, un juego en grande (imagen, titular y «Jugar →»). Cambia con lo que enfoques en las filas de
  // debajo: recorres «Seguir jugando» con la cruceta, subes y juegas.
  function escaparate(inicial) {
    const fig = h('div.j-heroe.j-capas');
    const txt = h('div.j-portada-txt');
    const sec = h('section.j-portada.j-escaparate',
      h('button.j-portada-img', { type: 'button', tabindex: '-1', 'aria-hidden': 'true', onclick: () => { if (est.heroe) RV.ir({ v: 'j-juego', key: est.heroe }); } }, fig),
      txt);
    let cur = null;
    function capa(g) {
      const src = fondoDe(g);
      if (!src) return tapa(g, 'j-capa');
      const img = h('img', { alt: '', decoding: 'sync', draggable: 'false' });
      const box = h('figure.j-capa', img);
      img.addEventListener('error', () => box.replaceWith(tapa(g, 'j-capa')));
      img.src = src;
      return box;
    }
    function poner(g, animar, antetitulo) {
      if (!g || (cur && cur.key === g.key && !animar)) return;
      cur = g;
      est.heroe = g.key;
      // La imagen: la nueva entra fundiéndose sobre la anterior
      const nueva = capa(g);
      if (animar) nueva.classList.add('entra');
      fig.append(nueva);
      const viejas = [...fig.children].slice(0, -1);
      if (!animar) viejas.forEach((v) => v.remove());
      else setTimeout(() => viejas.forEach((v) => v.remove()), 320);
      // El texto
      const f = fichaDe(g);
      const activo = D.activoPc(g.key);
      const nota = f ? D.nota(f.item, f.t) : null;
      const m = g.meta || {};
      const jugar = activo
        ? h('span.j-en-marcha', '● Abierto ahora mismo')
        : h('button.boton-tinta.j-jugar', { type: 'button', 'data-autofocus': true, 'data-clave': 'portada:jugar', onclick: () => RV.jugar(g) }, 'Jugar →');
      const ante = antetitulo || (activo ? 'En directo' : g.lastPlayed ? `Seguir jugando · ${visto(g)}` : g.favorite ? 'De tus favoritos · sin estrenar' : 'Sin estrenar');
      txt.replaceChildren(...[
        h('div.ante', ante),
        h(`h1.titular.ajusta.gran-titular${RV.tallaTitular(g.name)}`, { 'data-min': '34' }, g.name),
        m.description ? h('p.j-entradilla', m.description) : null,
        h('p.meta', [g.sourceLabel || g.source, horas(g) ? `${horas(g)} jugadas` : '', nota != null ? `tu nota ${nota}` : ''].filter(Boolean).join(' · ')),
        h('div.j-acciones', { 'data-recuerda': '' },
          jugar,
          h('button.enlace', { type: 'button', 'data-clave': 'portada:pagina', onclick: () => RV.ir({ v: 'j-juego', key: g.key }) }, 'Su página →'),
          h('button.enlace', { type: 'button', 'data-clave': 'portada:sorpresa', onclick: sorprender, title: 'Un juego tuyo que tienes olvidado' }, 'Sorpréndeme'),
          h('button.enlace.apagado', { type: 'button', 'data-clave': 'portada:opciones', onclick: () => opciones(g) }, 'Opciones')),
      ].filter(Boolean));
      if (animar) { txt.classList.remove('cambia'); void txt.offsetWidth; txt.classList.add('cambia'); }
      if (txt.isConnected) RV.ajustar(txt);
    }
    // «Sorpréndeme»: uno de tus juegos que llevas tiempo sin abrir (o sin estrenar); pulsando otra vez, otro
    const MES = 30 * 86400e3;
    function sorprender() {
      const todos = D.juegosPc().filter((g) => !D.activoPc(g.key) && (!cur || g.key !== cur.key));
      if (!todos.length) return;
      const olvidados = todos.filter((g) => !g.lastPlayed || Date.now() - g.lastPlayed > MES);
      const pool = olvidados.length ? olvidados : todos;
      const g = pool[Math.floor(Math.random() * pool.length)];
      poner(g, true, g.lastPlayed ? `Sorpresa · última vez ${RV.hace(g.lastPlayed)}` : 'Sorpresa · aún sin estrenar');
      const b = txt.querySelector('[data-clave="portada:sorpresa"]');
      if (b) b.focus({ preventScroll: true });
    }
    sec.sorprender = sorprender;
    poner(inicial, false);
    // Escaparate solo: si dejas el mando un rato, la portada va pasando tus juegos sin prisa (como una revista
    // expuesta); con cualquier botón se queda quieta en el que esté
    const desde = Date.now();
    sec.girar = (lista) => {
      const ls = lista.filter(Boolean);
      if (ls.length < 2) return;
      let ultimoGiro = 0;
      const reloj = setInterval(() => {
        if (!sec.isConnected) { clearInterval(reloj); return; }
        const ult = (RV.mando && RV.mando.ultima && RV.mando.ultima()) || null;
        const quieto = Date.now() - Math.max(desde, ult ? ult.t : 0);
        const capa = document.getElementById('capa');
        if (document.visibilityState !== 'visible' || quieto < (window.__quietoMs || 60000) || (capa && capa.childElementCount) || window.scrollY > 120) return;
        if (Date.now() - ultimoGiro < (window.__giroMs || 9000)) return;
        ultimoGiro = Date.now();
        const i = cur ? ls.findIndex((g) => g.key === cur.key) : -1;
        poner(ls[(i + 1) % ls.length], true);
      }, 1000);
    };
    // Al enfocar una portada de las filas, el escaparate pasa a ese juego (sin prisa: al recorrerlas rápido
    // con la cruceta no se cambia la imagen en cada paso)
    let t = null;
    sec.seguir = (fila) => {
      fila.addEventListener('focusin', (e) => {
        const c = e.target.closest('[data-key]');
        if (!c) return;
        clearTimeout(t);
        t = setTimeout(() => { const g = juego(c.dataset.key); if (g && (!cur || g.key !== cur.key)) poner(g, true); }, 110);
      });
    };
    return sec;
  }
  function tira(titulo, lista, o = {}) {
    const fila = h('div.j-tira', { 'data-recuerda': '' }, lista.map((g) => carta(g, { zona: o.zona, inicio: g.key === o.inicio })));
    // Los bordes se difuminan donde quedan más portadas por ver
    const bordes = () => {
      fila.classList.toggle('mas-izq', fila.scrollLeft > 4);
      fila.classList.toggle('mas-der', fila.scrollLeft + fila.clientWidth < fila.scrollWidth - 4);
    };
    // Al redibujar (llegan datos nuevos), cada fila sigue por donde la dejaste
    const zona = o.zona || 'b';
    fila.addEventListener('scroll', () => { est.tiras[zona] = fila.scrollLeft; bordes(); }, { passive: true });
    requestAnimationFrame(() => { if (est.tiras[zona]) fila.scrollLeft = est.tiras[zona]; bordes(); });
    return { sec: h('section.j-bloque', RV.seccion(titulo), fila), fila };
  }

  // ---------------------------------------------------------------- Juegos (la portada del lanzador)
  RV.vistas.juegos = () => {
    const root = h('div.hoja.j-pag');
    const todos = D.juegosPc();
    if (!todos.length) {
      root.append(h('h1.masthead.ajusta', { 'data-min': '60' }, 'ATRIO'),
        RV.vacio('No he encontrado juegos en este PC. En Ajustes → Bibliotecas eliges dónde buscar (Steam, Heroic, Lutris, emuladores) y añades los tuyos.', true),
        h('button.boton-tinta', { type: 'button', 'data-autofocus': true, onclick: () => RV.ir({ v: 'ajustes', sec: 'bibliotecas' }) }, 'Ir a Bibliotecas →'));
      return root;
    }
    const recientes = [...todos].filter((g) => g.lastPlayed > 0).sort((a, b) => b.lastPlayed - a.lastPlayed);
    const ahora = D.jugandoAhora();
    const front = (ahora && ahora.game && (juego(ahora.game.key) || ahora.game)) || recientes[0] || todos.find((g) => g.favorite) || todos[0];
    const enEscaparate = (est.heroe && juego(est.heroe)) || front;

    // En portada: el que estás jugando o el último (o el que enfocaste la última vez)
    const esc = escaparate(enEscaparate);
    root.append(esc);

    // Seguir jugando y Favoritos: filas de portadas que se recorren de lado
    const nombre = (a, b) => String(a.sortName || a.name).localeCompare(String(b.sortName || b.name), 'es', { sensitivity: 'base', numeric: true });
    const favs = todos.filter((g) => g.favorite).sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0) || nombre(a, b));
    const seguir = recientes.slice(0, 14);
    const primera = seguir.length ? tira('Seguir jugando', seguir, { zona: 's', inicio: enEscaparate.key })
      : tira('Tus juegos', (favs.length ? favs : [...todos].sort(nombre)).slice(0, 14), { zona: 's', inicio: enEscaparate.key });
    root.append(primera.sec);
    esc.seguir(primera.fila);
    if (favs.length && seguir.length) {
      const fv = tira(`Favoritos · ${favs.length}`, favs, { zona: 'f', inicio: enEscaparate.key });
      root.append(fv.sec);
      esc.seguir(fv.fila);
    }
    esc.girar([...new Map([...seguir, ...favs].map((g) => [g.key, g])).values()].slice(0, 12));

    // La biblioteca
    const origenes = [...new Map(todos.map((g) => [g.source, g.sourceLabel || g.source])).entries()];
    if (est.origen !== 'todos' && !origenes.some(([s]) => s === est.origen)) est.origen = 'todos';
    let lista = todos.filter((g) => est.origen === 'todos' || g.source === est.origen);
    if (est.orden === 'nombre') lista.sort(nombre);
    else if (est.orden === 'horas') lista.sort((a, b) => D.msPc(b) - D.msPc(a) || nombre(a, b));
    else lista.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0) || nombre(a, b));
    const opt = (actual, valor, texto, fn) => h('button.j-opt', { type: 'button', 'aria-pressed': String(actual === valor), 'data-clave': `opt:${valor}`, onclick: fn }, texto);
    root.append(h('section.j-bloque.j-biblioteca',
      RV.seccion(`La biblioteca · ${RV.plural(lista.length, 'juego', 'juegos')}`),
      h('div.j-controles',
        h('div.j-grupo', [['recientes', 'Recientes'], ['nombre', 'A–Z'], ['horas', 'Horas']].map(([v, t]) => opt(est.orden, v, t, () => { est.orden = v; RV.refrescar(); }))),
        h('span.j-sep'),
        h('div.j-grupo', [['todos', 'Todo'], ...origenes].map(([v, t]) => opt(est.origen, v, t, () => { est.origen = v; RV.refrescar(); }))),
        h('span.hueco'),
        h('button.enlace', { type: 'button', onclick: () => RV.buscarJuegos(), title: 'X (o /)' }, 'Buscar un juego')),
      h('div.j-rejilla', lista.map((g) => carta(g, { perezosa: true })))));
    root.append(h('p.pie', 'Atrio · ⧉ Vista para la revista · ☰ Menú'));
    return root;
  };

  // ---------------------------------------------------------------- La página de un juego
  RV.vistas['j-juego'] = (ruta) => {
    const g = juego(ruta.key);
    const root = h('div.hoja.j-pag.j-juego');
    if (!g) { root.append(RV.vacio('Este juego ya no está en el PC.', true)); return root; }
    const f = fichaDe(g);
    const m = g.meta || {};
    const activo = D.activoPc(g.key);
    const datos = h('ul.datos');
    const dato = (k, v, serif) => { if (v) datos.append(RV.dato(k, v, serif)); };
    dato('En este PC', horas(g) || 'sin horas todavía', true);
    dato('Última vez', g.lastPlayed ? RV.hace(g.lastPlayed) : 'sin estrenar');
    dato('Tienda', g.sourceLabel || g.source);
    if (m.developers && m.developers.length) dato('Estudio', m.developers.join(', '));
    if (m.release) dato('Salida', m.release);
    if (m.genres && m.genres.length) dato('Género', m.genres.join(', '));
    if (m.metacritic) dato('Metacritic', String(m.metacritic), true);
    if (m.controller) dato('Mando', m.controller === 'full' ? 'compatible' : 'en parte');

    const acciones = h('div.j-acciones',
      activo ? h('span.j-en-marcha', '● Abierto ahora mismo')
        : h('button.boton-tinta.j-jugar', { type: 'button', 'data-autofocus': true, 'data-clave': 'juego:jugar', onclick: () => RV.jugar(g) }, 'Jugar →'),
      h('button.enlace', { type: 'button', onclick: () => RV.guia(g.key, null) }, (g.links || []).length ? `Guías · ${(g.links || []).length} →` : 'Guías →'),
      f ? h('button.enlace', { type: 'button', onclick: () => { RV.cambiarModo('revista'); RV.ir({ v: 'ficha', k: 0, t: f.t, id: f.item.id }); } }, 'Su ficha →') : null,
      h('button.enlace.apagado', { type: 'button', onclick: () => marcar(g, { favorite: !g.favorite }, g.favorite ? 'Quitado de favoritos' : 'En favoritos') }, g.favorite ? '★ Favorito' : '☆ Favorito'),
      h('button.enlace.apagado', { type: 'button', onclick: () => opciones(g) }, 'Más'));

    // Lo de tu cuenta: tu nota y tu crítica, o lo que llevas
    let cuenta = null;
    if (f) {
      const n = D.nota(f.item, f.t);
      const cita = f.t === 'g' ? String(f.item.review || '').trim()
        : ([...(f.item.entries || []).filter((x) => x.note && x.note.trim())].sort((a, b) => num(b.time) - num(a.time))[0] || {}).note || '';
      cuenta = h('section.j-cuenta',
        RV.seccion(f.t === 'g' ? 'Tu nota' : `${D.estado(f.kind, f.item, f.t)} · en tu cuenta`),
        n != null ? h('div.j-nota', h('span.cifra', String(n)), h('small', f.t === 'g' ? '/100' : `media de ${(f.item.entries || []).length} apuntes`)) : null,
        cita ? h('blockquote.cita', `«${cita.length > 300 ? `${cita.slice(0, 297).trim()}…` : cita}»`, h('span.de.versal', f.t === 'g' ? 'Tu crítica' : 'Tu último apunte')) : null);
    }
    root.append(h('section.j-pag-juego',
      arte(g, fondoDe(g), 'j-heroe'),
      h('div.j-juego-txt',
        h('div.ante', [activo ? 'En directo' : 'Juego', g.platform || ''].filter(Boolean).join(' · ')),
        h(`h1.titular.ajusta.gran-titular${RV.tallaTitular(g.name)}`, { 'data-min': '34' }, g.name),
        m.description ? h('p.entradilla', m.description) : null,
        acciones)),
    h('div.j-dos', h('section', RV.seccion('Datos'), datos), cuenta || h('section', RV.seccion('Tu cuenta'),
      RV.vacio(D.conCuenta() ? 'No lo tienes en tu cuenta. Al jugarlo unos minutos pasa solo a «Jugando».' : 'Entra con tu cuenta (Ajustes) para ver aquí tu nota y tus apuntes.'))));
    return root;
  };

  // ---------------------------------------------------------------- Valorados
  let ordenV = 'nota';
  RV.vistas['j-valorados'] = () => {
    const root = h('div.hoja.j-pag');
    root.append(h('header.cabeza', h('div.ante', 'Lo que has terminado, con tu nota'), h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Valorados')), h('div.filete')));
    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (Ajustes → Cuenta) para ver aquí tus juegos valorados.', true),
        h('button.boton-tinta', { type: 'button', 'data-autofocus': true, onclick: () => RV.ir({ v: 'ajustes' }) }, 'Ir a Ajustes →'));
      return root;
    }
    const k = D.tipo(0);
    const l = [...D.valorados(k)].sort(ordenV === 'nota'
      ? (a, b) => num(b.general) - num(a.general) || num(b.end) - num(a.end)
      : (a, b) => num(b.end) - num(a.end) || num(b.general) - num(a.general));
    root.append(h('div.j-controles', h('div.j-grupo',
      [['nota', 'Por nota'], ['fecha', 'Por fecha']].map(([v, t]) => h('button.j-opt', { type: 'button', 'aria-pressed': String(ordenV === v), onclick: () => { ordenV = v; RV.refrescar(); } }, t)))));
    if (!l.length) { root.append(RV.vacio('Aún no has valorado ningún juego.', true)); return root; }
    const ul = h('ul.j-valorados');
    l.forEach((g, i) => {
      const pcg = D.pcDe(k, g);
      const abrir = () => {
        if (pcg) RV.ir({ v: 'j-juego', key: pcg.key });
        else { RV.cambiarModo('revista'); RV.ir({ v: 'ficha', k: 0, t: 'g', id: g.id }); }
      };
      const tapaV = (pcg && portadaDe(pcg)) || remota(g.cover);
      const b = h('button.j-valorado', { type: 'button', 'data-clave': `v:${g.id}`, 'data-autofocus': i === 0 ? true : null, onclick: abrir },
        h('span.cifra', String(num(g.general))),
        tapaV ? arte({ name: g.name }, tapaV, 'j-mini') : h('span.j-mini.sin-imagen'),
        h('span', h('span.tit.ajusta', { 'data-min': '16' }, g.fav ? h('span.estrella', '★') : null, g.name),
          h('span.versal', [num(g.end) > 0 ? RV.fmtDia(g.end) : '', g.creator, pcg ? 'en este PC' : ''].filter(Boolean).join(' · '))));
      // Y: las opciones de la ficha (estén o no instalados en este PC)
      b.dataset.opciones = '';
      b.__opciones = () => RV.opcionesFicha(k, 'g', g);
      ul.append(h('li', b));
    });
    root.append(ul);
    return root;
  };

  // ---------------------------------------------------------------- Abrir un juego (desde el móvil, por ejemplo)
  RV.abrirJuego = (key) => {
    const g = juego(key);
    if (!g) return false;
    if (RV.modo !== 'juegos') RV.cambiarModo('juegos');
    RV.ir({ v: 'j-juego', key: g.key });
    return true;
  };

  // ---------------------------------------------------------------- Buscar un juego del PC
  RV.buscarJuegos = () => {
    const input = h('input', { type: 'search', placeholder: 'Nombre del juego', 'data-autofocus': true, spellcheck: 'false', 'aria-label': 'Buscar un juego' });
    const ul = h('ul.resultados');
    let primero = null;
    const pintar = () => {
      const q = RV.normName(input.value);
      ul.replaceChildren();
      primero = null;
      if (!q) return;
      const l = D.juegosPc().filter((g) => RV.normName(g.name).includes(q))
        .sort((a, b) => Number(!RV.normName(a.name).startsWith(q)) - Number(!RV.normName(b.name).startsWith(q)) || (b.lastPlayed || 0) - (a.lastPlayed || 0)).slice(0, 30);
      for (const g of l) {
        const ir = () => { d.cerrar(); RV.ir({ v: 'j-juego', key: g.key }); };
        if (!primero) primero = ir;
        ul.append(h('li', h('div.resultado',
          h('button.enlace-texto', { type: 'button', onclick: ir }, h('span.tit', g.name), h('span.versal', { style: { display: 'block' } }, [g.sourceLabel, horas(g)].filter(Boolean).join(' · '))),
          D.activoPc(g.key) ? h('span.versal', 'En marcha') : h('button.enlace.acento', { type: 'button', onclick: () => { d.cerrar(); RV.jugar(g); } }, 'Jugar →'))));
      }
      if (!ul.children.length) ul.append(h('li', RV.vacio('Ningún juego se llama así.')));
    };
    input.addEventListener('input', pintar);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && primero) { e.preventDefault(); primero(); } });
    const d = RV.dialogo({ ante: 'Buscar', titulo: 'Un juego de este PC', ancho: true, cuerpo: [h('label.buscador', { style: { maxWidth: 'none' } }, h('span.lupa', 'Buscar'), input), ul] });
  };

  // ---------------------------------------------------------------- El menú (☰)
  const ENERGIA = {
    suspender: { t: 'Suspender', ask: '¿Suspender el ordenador?' },
    reiniciar: { t: 'Reiniciar', ask: '¿Reiniciar el ordenador?' },
    apagar: { t: 'Apagar', ask: '¿Apagar el ordenador?' },
  };
  RV.energia = (accion) => {
    const p = ENERGIA[accion];
    const hayJuego = !!D.jugandoAhora();
    RV.dialogo({
      ante: 'Energía', titulo: p.ask,
      cuerpo: h('p.texto', hayJuego ? 'Hay un juego abierto: guarda la partida antes. Atrio guarda tus horas jugadas.' : 'Atrio guarda tus horas jugadas antes.'),
      acciones: ['hueco',
        { texto: 'Cancelar', fn: (x) => x.cerrar() },
        { texto: p.t, principal: true, fn: async (x) => { x.cerrar(); try { await RV.api.power(accion); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); } } }],
    });
    // La opción segura, enfocada
    requestAnimationFrame(() => { const b = RV.$$('#capa .pie-dialogo button'); if (b[0]) b[0].focus({ preventScroll: true }); });
  };
  RV.menu = () => {
    const ul = h('ul.j-opciones');
    const op = (t, fn, extra) => ul.append(h('li', h('button.resultado', { type: 'button', style: { width: '100%', textAlign: 'left' }, onclick: () => { d.cerrar(); fn(); } },
      h('span.tit', t), extra ? h('span.versal', extra) : h('span'))));
    op(RV.modo === 'juegos' ? 'La revista' : 'Modo Juegos', () => RV.cambiarModo(), 'Vista');
    op('Ajustes', () => RV.ir({ v: 'ajustes' }));
    op('Buscar', () => (RV.modo === 'juegos' ? RV.buscarJuegos() : RV.buscarTodo()), 'X');
    ul.append(h('li.j-op-sep', h('span.versal', 'Energía')));
    op('Suspender', () => RV.energia('suspender'));
    op('Reiniciar', () => RV.energia('reiniciar'));
    op('Apagar', () => RV.energia('apagar'));
    ul.append(h('li.j-op-sep', h('span.versal', 'Atrio')));
    op('Minimizar Atrio', () => RV.api.minimize());
    op('Salir de Atrio', () => RV.api.quit());
    const d = RV.dialogo({ ante: 'Atrio', titulo: 'Menú', cuerpo: ul });
  };

  // ---------------------------------------------------------------- Ayuda de botones (solo con mando o teclado)
  const ayuda = h('div#ayuda-mando', { 'aria-hidden': 'true' });
  const pista = (b, t) => h('span.pista-boton', h('b', b), t);
  function pintarAyuda() {
    const juegos = RV.modo === 'juegos';
    ayuda.replaceChildren(pista('A', 'Elegir'), pista('B', 'Volver'), pista('Y', 'Opciones'), pista('X', 'Buscar'),
      pista('LB · RB', 'Secciones'), pista('⧉', juegos ? 'Revista' : 'Juegos'), pista('☰', 'Menú'));
  }
  pintarAyuda();
  document.body.append(ayuda);
  new window.MutationObserver(pintarAyuda).observe(document.body, { attributes: true, attributeFilter: ['data-modo'] });
})(window.RV);
