'use strict';
/* global window */
// Los datos de la revista: tu cuenta (las mismas fichas que el móvil, con la forma de syncData()) y la biblioteca
// del PC. Los guarda y sincroniza el proceso principal (src/main/ronron.js); aquí solo se leen y se editan.
// Modelo: Data.kt del móvil (Kind, Game, Play, Entry, Seg, Memo).
(function (RV) {
  const num = RV.num;
  const D = {};
  RV.D = D;

  let cuenta = { status: { signed: false }, data: null };
  let pc = { config: {}, games: [], play: { sessions: [], active: [] } };
  D.cuenta = () => cuenta;
  D.pc = () => pc;
  D.config = () => pc.config || {};
  // Tu nube (kDrive) conectada en este PC
  D.nubeOn = () => !!((D.config().nube || {}).token && (D.config().nube || {}).drive);
  // Fotos de la galería de tus otros dispositivos ya descargadas en este PC: nombre (sin extensión) → dirección
  let fotos = {};
  D.setFotos = (m) => { fotos = m || {}; };
  // Una foto de un dispositivo («file:…/covers/foto_….jpg») se ve si ya está aquí; las de internet, tal cual
  D.fotoDe = (u) => { const m = /^file:.*\/([A-Za-z0-9_.-]+?)(\.[A-Za-z0-9]+)?$/i.exec(String(u || '')); return m ? m[1] : null; };
  // El nombre completo del archivo (con su extensión), el que se le pide al móvil
  D.fotoArchivo = (u) => { const m = /^file:.*\/([A-Za-z0-9_.-]+)$/i.exec(String(u || '')); return m ? m[1] : null; };
  D.imagen = (u) => { const n = D.fotoDe(u); return n ? (fotos[n] || '') : (u || ''); };
  D.conCuenta = () => !!(cuenta.status && cuenta.status.signed);
  D.datos = () => cuenta.data || {};
  D.setCuenta = (s) => { if (s && typeof s === 'object') cuenta = { status: s.status || { signed: false }, data: s.data || null }; };
  D.setPc = (s) => { if (s && typeof s === 'object') { pc = { ...pc, ...s }; cachePc = null; } };
  D.setPlay = (p) => { if (p) { pc = { ...pc, play: p }; cachePc = null; } };
  // Un juego del PC que ha cambiado (arte, nombre, datos de la tienda…)
  D.setJuegoPc = (g) => {
    if (!g || !g.key) return;
    const games = [...(pc.games || [])];
    const i = games.findIndex((x) => x.key === g.key);
    if (i >= 0) games[i] = g; else games.push(g);
    pc = { ...pc, games };
    cachePc = null;
  };

  // ---------------------------------------------------------------- Categorías (Kind)
  D.CAMPOS = ['general', 'gfx', 'story', 'music', 'gameplay'];
  function tipo(o) {
    const labels = o.labels;
    const t = {
      id: o.id, title: o.title, labels, statuses: o.statuses, isBook: !!o.isBook, custom: !!o.custom, source: o.source || '',
    };
    t.isGame = t.id === 0;
    t.isMovie = t.source === 'movie';
    t.isTv = t.source === 'tv';
    t.crit = Math.max(1, Math.min(4, labels.length - 1));
    t.creatorWord = t.isMovie ? 'Director' : t.custom ? 'Creador' : t.isBook ? 'Autor' : 'Estudio';
    t.platformWord = t.isMovie || t.isTv ? 'Dónde' : t.isBook || t.custom ? 'Formato' : 'Plataforma';
    t.unit = t.isMovie ? 'película' : t.isTv ? 'serie' : t.custom ? 'ficha' : t.isBook ? 'libro' : 'juego';
    t.doneWord = t.isMovie || t.isTv ? 'Vistas' : t.custom ? 'Terminados' : t.isBook ? 'Leídos' : 'Pasados';
    // El estado de algo terminado, en singular («Pasado», «Leído», «Vista», «Terminado»)
    t.doneOne = t.isGame ? 'Pasado' : t.doneWord.replace(/s$/, '');
    t.short = t.isMovie ? 'Pelis' : t.isTv ? 'Series' : t.isGame ? 'Juegos' : t.isBook ? 'Libros' : t.title;
    t.platforms = t.isMovie ? ['Cine', 'Netflix', 'Prime Video', 'Disney+', 'HBO Max', 'Apple TV+', 'Filmin']
      : t.isTv ? ['Netflix', 'Prime Video', 'Disney+', 'HBO Max', 'Apple TV+', 'Filmin', 'Tele']
        : t.custom ? [] : t.isBook ? ['Papel', 'Ebook', 'Kindle', 'Audiolibro']
          : ['PC', 'PS5', 'PS4', 'Switch', 'Switch 2', 'Xbox', 'Steam Deck', 'Móvil'];
    return t;
  }
  const GAMES = tipo({ id: 0, title: 'Videojuegos', labels: ['Valoración', 'Gráficos', 'Historia', 'Música', 'Jugabilidad'], statuses: ['En lista', 'Jugando', 'Dropeado'] });
  const BOOKS = tipo({ id: 1, title: 'Libros', labels: ['Valoración', 'Personajes', 'Historia', 'Escritura', 'Ritmo'], statuses: ['En lista', 'Leyendo', 'Abandonado'], isBook: true });
  // Una categoría propia tal como la guarda el móvil (kindToJson): id, title, criteria, progress, color, source
  function propia(o) {
    if (!o || !Number.isFinite(Number(o.id)) || !o.title) return null;
    const crit = (Array.isArray(o.criteria) ? o.criteria : []).map((c) => String(c || '').trim()).filter(Boolean).slice(0, 4);
    const src = o.source === 'movie' || o.source === 'tv' ? o.source : '';
    return tipo({
      id: Number(o.id), title: String(o.title).trim(), labels: ['Valoración', ...(crit.length ? crit : ['Disfrute'])],
      statuses: ['En lista', String(o.progress || '').trim() || 'En curso', 'Abandonado'], custom: true, source: src,
    });
  }
  let kindsRef = null;
  let kindsCache = [GAMES, BOOKS];
  D.tipos = () => {
    const ks = D.datos().kinds;
    if (ks !== kindsRef) {
      kindsRef = ks;
      kindsCache = [GAMES, BOOKS, ...(Array.isArray(ks) ? ks : []).map(propia).filter(Boolean)];
    }
    return kindsCache;
  };
  D.tipo = (id) => D.tipos().find((k) => k.id === Number(id)) || null;

  // ---------------------------------------------------------------- Fichas
  // 'g' = valorado (Game, en games/books/items_<id>); 'p' = en curso, en lista o dejado (Play)
  const gk = (id) => (id === 0 ? 'games' : id === 1 ? 'books' : `items_${id}`);
  const pk = (id) => (id === 0 ? 'plays' : id === 1 ? 'bplays' : `iplays_${id}`);
  D.lista = (kind, t) => { const l = D.datos()[t === 'g' ? gk(kind.id) : pk(kind.id)]; return Array.isArray(l) ? l.filter(Boolean) : []; };
  D.valorados = (kind) => D.lista(kind, 'g');
  D.fichas = (kind) => D.lista(kind, 'p');
  D.buscar = (kind, t, id) => D.lista(kind, t).find((o) => Number(o.id) === Number(id)) || null;

  // Media de tus apuntes (el «baremo» del móvil)
  D.baremo = (p) => {
    const es = (p && p.entries) || [];
    if (!es.length) return null;
    const o = {};
    for (const f of D.CAMPOS) o[f] = Math.round(es.reduce((s, e) => s + num(e[f], 50), 0) / es.length);
    return o;
  };
  D.notas = (o) => D.CAMPOS.map((f) => num(o && o[f], 50));
  // La nota de una ficha: la tuya si está valorada; la media de tus apuntes si está en curso
  D.nota = (item, t) => (t === 'g' ? num(item.general, 50) : (D.baremo(item) || {}).general);
  // Momento de la última actividad (para ordenar), como Play.activity y Game.activity
  D.actividad = (o, t) => (t === 'g'
    ? Math.max(num(o.updated), num(o.end) * RV.DIA, num(o.start) * RV.DIA)
    : Math.max(num(o.updated), num(o.start) * RV.DIA, ...((o.entries || []).map((e) => num(e.time)))));
  // Porcentaje completado: por capítulos si los hay; si no, por horas frente a la duración media
  D.porcentaje = (p) => {
    const parts = p.parts || [];
    if (parts.length) return Math.floor((parts.filter((x) => x && (x.d || x.done)).length * 100) / parts.length);
    if (num(p.ttbMain) > 0 && num(p.minutes) > 0) return Math.min(100, Math.floor((num(p.minutes) * 100) / num(p.ttbMain)));
    return null;
  };
  D.memos = (p) => (p.memos || []).filter((m) => m && !m.del);
  D.estado = (kind, item, t) => (t === 'g' ? kind.doneOne : kind.statuses[Math.max(0, Math.min(2, num(item.status)))]);

  // Todo junto: [{ kind, t, item }]
  D.todo = () => {
    const out = [];
    for (const k of D.tipos()) {
      for (const g of D.valorados(k)) out.push({ kind: k, t: 'g', item: g });
      for (const p of D.fichas(k)) out.push({ kind: k, t: 'p', item: p });
    }
    return out;
  };
  // Lo que llevas entre manos (en curso, de todas las categorías), lo más reciente primero
  D.enCurso = () => {
    const out = [];
    for (const k of D.tipos()) for (const p of D.fichas(k)) if (num(p.status) === 1) out.push({ kind: k, t: 'p', item: p });
    return out.sort((a, b) => D.actividad(b.item, 'p') - D.actividad(a.item, 'p'));
  };

  D.ordenar = (list, t, orden) => {
    const l = [...list];
    const nombre = (a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'es', { sensitivity: 'base', numeric: true });
    if (orden === 'nombre') return l.sort(nombre);
    if (orden === 'nota') return l.sort((a, b) => num(D.nota(b, t), -1) - num(D.nota(a, t), -1) || nombre(a, b));
    return l.sort((a, b) => D.actividad(b, t) - D.actividad(a, t));
  };

  // Rellena los campos que el móvil exige al leer (gameFromJson / playFromJson usan getString y getInt)
  D.completarJuego = (o) => ({
    general: 50, gfx: 50, story: 50, music: 50, gameplay: 50, review: '', cover: '', hero: '', start: 0, end: 0, creator: '',
    fav: false, platform: '', pages: 0, minutes: 0, ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '', logo: '', ...o,
  });
  D.completarFicha = (o) => {
    const p = {
      cover: '', hero: '', status: 0, entries: [], chapter: 0, every: 3, start: 0, end: 0, creator: '', platform: '', pages: 0,
      minutes: 0, ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '', parts: [], logo: '', ...o,
    };
    p.entries = (p.entries || []).map((e) => ({ general: 50, gfx: 50, story: 50, music: 50, gameplay: 50, note: '', chapter: 0, ...e }));
    return p;
  };

  // ---------------------------------------------------------------- El PC
  // (la edición del Mac no abre juegos: nada de «Jugar en el PC» aunque llegue una lista)
  D.juegosPc = () => (RV.soloRevista ? [] : (pc.games || []).filter((g) => !g.hidden));
  // El juego instalado que corresponde a una ficha de videojuegos: por el id de SteamGridDB (la cuenta usa ese
  // mismo id) o por el nombre normalizado
  D.pcDe = (kind, item) => {
    if (!kind || !kind.isGame || !item) return null;
    const all = D.juegosPc();
    const n = RV.normName(item.name);
    return all.find((g) => g.sgdbKnown && Number(g.sgdbKnown) === Number(item.id))
      || all.find((g) => n && (RV.normName(g.name) === n || RV.normName(g.originalName) === n)) || null;
  };
  // Tiempo jugado en el PC (ms): lo registrado por Atrio (también en tus otros equipos) o lo que dice Steam, lo que sea mayor
  let cachePc = null;
  function totales() {
    if (cachePc) return cachePc;
    const alias = new Map();
    for (const g of pc.games || []) {
      alias.set(g.key, g.key);
      for (const k of g.aliasKeys || []) if (!alias.has(k)) alias.set(k, g.key);
    }
    const t = new Map();
    const play = pc.play || { sessions: [], active: [] };
    const now = Date.now();
    for (const [k0, s, e] of play.sessions || []) {
      const k = alias.get(k0) || k0;
      t.set(k, (t.get(k) || 0) + Math.max(0, e - s));
    }
    for (const a of play.active || []) {
      const k = alias.get(a.k) || a.k;
      t.set(k, (t.get(k) || 0) + Math.max(0, now - a.s));
    }
    cachePc = t;
    return t;
  }
  D.msPc = (g) => Math.max(totales().get(g.key) || 0, num(g.steamPlaytime));
  D.activoPc = (key) => ((pc.play && pc.play.active) || []).some((a) => a.k === key);
  // Lo que se está jugando ahora en este PC: { game, desde }
  D.jugandoAhora = () => {
    const a = [...((pc.play && pc.play.active) || [])].sort((x, y) => y.s - x.s)[0];
    if (!a) return null;
    const g = (pc.games || []).find((x) => x.key === a.k || (x.aliasKeys || []).includes(a.k));
    return g ? { game: g, desde: a.s } : null;
  };

  // ---------------------------------------------------------------- Editar
  // Todas las ediciones pasan por atrio.ronronEdit; la respuesta trae el estado nuevo de la cuenta
  RV.editar = async (op, ok) => {
    try {
      const s = await RV.api.ronronEdit(op);
      D.setCuenta(s);
      if (RV.refrescar) RV.refrescar();
      if (s && s.status && s.status.err) RV.aviso(s.status.err, 'error');
      else if (ok) RV.aviso(ok);
      return true;
    } catch (e) {
      RV.aviso(RV.limpiaError(e), 'error');
      return false;
    }
  };
})(window.RV);
