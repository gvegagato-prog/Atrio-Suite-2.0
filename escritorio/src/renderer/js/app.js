'use strict';
/* global A, window, document */
(function () {
  const api = window.atrio;
  const h = A.h;
  const app = {};
  A.app = app;
  A.api = api;

  let state = { config: null, games: [], scanInfo: {} };
  let layoutCtl = null;
  let focusedKey = null;
  let filter = 'all';
  const stage = A.$('#stage');

  const SOURCE_FILTERS = [
    ['steam', 'Steam'], ['heroic', 'Heroic'], ['lutris', 'Lutris'], ['emulators', 'Emuladores'], ['custom', 'Añadidos'],
  ];

  app.state = () => state;
  app.game = (key) => state.games.find((g) => g.key === key);
  app.focusedKey = () => focusedKey;

  // ---------- Tema ----------
  function applyTheme() {
    const t = state.config.theme;
    const b = document.body;
    b.dataset.layout = A.layouts.get(t.layout).id;
    b.dataset.scheme = t.scheme;
    // Esquemas claros: sombras y colores de aviso propios
    if (A.isLightScheme(t.scheme)) b.dataset.light = '1'; else delete b.dataset.light;
    b.dataset.font = t.font;
    b.dataset.density = t.density;
    b.dataset.backdrop = t.backdrop;
    b.dataset.herofit = t.heroFit || 'nitido';
    b.dataset.form = t.form || 'clasico';
    // Notas: las elegidas aparte o, si no, las de la forma
    b.dataset.gauge = t.gauge || A.GAUGE_OF_FORM[t.form || 'clasico'] || 'ring';
    b.classList.toggle('no-motion', !t.motion);
    b.classList.toggle('no-clock', !t.clock);
    b.classList.toggle('no-hints', !t.labels);
    if (t.accent && t.accent !== 'auto') {
      b.style.setProperty('--accent', t.accent);
    } else {
      b.style.removeProperty('--accent');
    }
    A.nav.setSwapAB(!!state.config.behavior.swapAB);
    A.nav.setPadMaps(A.settings.cleanMaps(state.config.behavior.padMaps));
  }

  // ---------- Juegos visibles ----------
  function sortGames(list) {
    const mode = state.config.behavior.sort;
    const alpha = (a, b) => a.sortName.localeCompare(b.sortName, 'es');
    if (mode === 'alpha') return list.sort(alpha);
    if (mode === 'source') {
      const order = { steam: 0, heroic: 1, lutris: 2, emulators: 3, custom: 4 };
      return list.sort((a, b) => (order[a.source] - order[b.source]) || alpha(a, b));
    }
    return list.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0) || alpha(a, b));
  }

  function visibleGames() {
    let list = state.games;
    // Los juegos ocultos no aparecen en la biblioteca: solo en Ajustes, Juegos ocultos
    list = list.filter((g) => !g.hidden);
    if (filter === 'fav') list = list.filter((g) => g.favorite);
    else if (filter !== 'all') list = list.filter((g) => g.source === filter);
    return sortGames([...list]);
  }

  function filterDefs() {
    const shown = state.games.filter((g) => !g.hidden);
    const defs = [['all', 'Todo', shown.length]];
    const favs = shown.filter((g) => g.favorite).length;
    if (favs) defs.push(['fav', 'Favoritos', favs]);
    for (const [id, label] of SOURCE_FILTERS) {
      const n = shown.filter((g) => g.source === id).length;
      if (n) defs.push([id, label, n]);
    }
    // Tus juegos valorados de Ronrón (siempre visible: sin sesión, la pestaña ofrece entrar)
    defs.push(['valorados', 'Valorados', A.ronron && A.ronron.signed() ? A.ronron.count() : null]);

    return defs;
  }

  function renderFilters() {
    const defs = filterDefs();
    if (!defs.some(([id]) => id === filter)) filter = 'all';
    A.$('#filters').replaceChildren(...defs.map(([id, label, n]) =>
      h('button.chip', {
        'data-nav': true, 'data-zone': 'chrome', 'data-filter': id, 'aria-pressed': String(id === filter),
        onclick: () => setFilter(id, true),
      }, label, n === null ? null : h('span.n', String(n)))));
  }

  function setFilter(id, keepChipFocus) {
    if (id === filter) return;
    filter = id;
    render(null, keepChipFocus);
    api.patchConfig({ behavior: { filter: id } }).catch(() => {});
  }

  function cycleFilter(step) {
    const ids = filterDefs().map(([id]) => id);
    const i = ids.indexOf(filter);
    setFilter(ids[(i + step + ids.length) % ids.length], false);
  }

  // ---------- Render ----------
  function render(keepKey, keepChipFocus) {
    applyTheme();
    renderFilters();
    stage.replaceChildren();
    if (filter === 'valorados') {
      layoutCtl = null;
      focusedKey = null;
      document.body.dataset.view = 'valorados';
      A.ronron.render(stage, keepKey);
      if (keepChipFocus) A.nav.focus(A.$('#filters [data-filter="valorados"]'), { noScroll: true });
      return;
    }
    delete document.body.dataset.view;
    const games = visibleGames();
    if (!games.length) {
      layoutCtl = null;
      focusedKey = null;
      stage.append(A.layouts.emptyState(filter));
      setBackdrop(null);
      if (keepChipFocus) A.nav.focus(A.$(`#filters [data-filter="${filter}"]`), { noScroll: true });
      else if (!A.nav.inOverlay()) A.nav.focusFirst('.empty [data-autofocus]');
      return;
    }
    const layout = A.layouts.get(state.config.theme.layout);
    layoutCtl = layout.fn(stage, games, { filter });
    const target = (keepKey && stage.querySelector(`[data-key="${CSS.escape(keepKey)}"]`)) || stage.querySelector('[data-key]');
    const key = target.getAttribute('data-key');
    focusedKey = null;
    showGame(key, true);
    if (keepChipFocus) {
      A.nav.focus(A.$(`#filters [data-filter="${filter}"]`), { noScroll: true });
      target.classList.add('focused');
      setTimeout(() => target.classList.remove('focused'), 0);
      target.scrollIntoView({ block: 'nearest', inline: target.closest('[data-scroll="center"]') ? 'center' : 'nearest' });
    } else if (!A.nav.inOverlay()) {
      A.nav.focus(target);
    }
  }
  app.render = render;
  app.filter = () => filter;
  app.renderFiltersOnly = () => renderFilters();

  // Muestra la información de un juego (sin mover el foco)
  const setBackdropSoon = A.debounce((g) => setBackdrop(g), 140);
  function showGame(key, immediate) {
    if (key === focusedKey) return;
    focusedKey = key;
    const g = app.game(key);
    if (!g || !layoutCtl) return;
    layoutCtl.update(g);
    if (immediate) setBackdrop(g); else setBackdropSoon(g);
    if (A.hd.enabled() && state.config.theme.backdrop === 'arte') A.hd.request(key);
    preloadAround(key);
  }

  // Precarga los fondos de los juegos vecinos para que el cambio sea instantáneo
  const preloaded = new Set();
  function preloadAround(key) {
    const tiles = A.$$('[data-key]', stage);
    const i = tiles.findIndex((t) => t.getAttribute('data-key') === key);
    if (i < 0) return;
    for (const t of tiles.slice(Math.max(0, i - 2), i + 3)) {
      const g = app.game(t.getAttribute('data-key'));
      const src = g && (g.art.herohd || g.art.hero || g.art.wide);
      if (!src || preloaded.has(src)) continue;
      preloaded.add(src);
      const img = new Image();
      img.decoding = 'async';
      img.src = src;
    }
  }

  // ---------- Fondo y acento ----------
  const layers = A.$$('#backdrop .bd-layer');
  let activeLayer = 0;
  let backdropSrc = null;
  let backdropKey = null;
  function setBackdrop(g) {
    backdropKey = g ? g.key : null;
    const src = g ? (g.art.herohd || g.art.hero || g.art.wide || g.art.cover) : null;
    if (!src && g) document.body.style.setProperty('--tint', A.color.hue(A.layouts.hueOf(g.name)).join(', '));
    if (src === backdropSrc) return;
    backdropSrc = src;
    const next = layers[1 - activeLayer];
    const prev = layers[activeLayer];
    if (!src) { prev.classList.remove('on'); next.classList.remove('on'); return; }
    const img = new Image();
    img.decoding = 'async';
    img.onload = () => {
      if (backdropSrc !== src) return;
      // Altura a la proporción real de la imagen; si es más alta que la pantalla, se recorta
      const h = window.innerWidth * img.naturalHeight / img.naturalWidth;
      const fits = h <= window.innerHeight * 1.02;
      next.classList.toggle('fit', fits);
      next.style.setProperty('--bd-h', `${Math.round(h)}px`);
      next.style.backgroundImage = `url("${src}")`;
      next.classList.add('on');
      prev.classList.remove('on');
      activeLayer = 1 - activeLayer;
    };
    img.src = src;
    // Acento automático tomado del arte
    const tintSrc = g.art.cover || g.art.hero || g.art.wide;
    if (tintSrc) {
      A.color.analyse(tintSrc).then((rgb) => {
        if (!rgb || backdropKey !== g.key) return;
        const light = A.isLightScheme(state.config.theme.scheme);
        const tint = A.color.toTint(rgb);
        document.body.style.setProperty('--tint', tint.join(', '));
        if (state.config.theme.accent === 'auto') {
          document.body.style.setProperty('--accent', `rgb(${A.color.toAccent(rgb, light).join(', ')})`);
        }
      });
    }
  }

  app.setBackdropFor = (g) => setBackdrop(g);

  // ---------- Reloj y pistas ----------
  function tick() {
    const d = new Date();
    A.$('#clock').textContent = d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
  }
  setInterval(tick, 10000);
  tick();

  const openLabel = () => (state.config && state.config.behavior.onSelect === 'play' ? 'Jugar' : 'Abrir');
  app.renderHints = () => renderHints();
  function renderHints() {
    const pad = A.nav.hasPad();
    const k = (key, text) => h('span', h('kbd', key), text);
    A.$('#hints').replaceChildren(...(pad
      ? [k('A', openLabel()), k('Y', 'Opciones'), k('X', 'Buscar'), k('Vista', 'Calendario'), k('LB RB', 'Filtro'), k('B', 'Volver')]
      : [k('Intro', openLabel()), k('Espacio', 'Opciones'), k('/', 'Buscar'), k('C', 'Calendario'), k('Tab', 'Filtro'), k('Esc', 'Volver')]));
  }

  // ---------- Avisos ----------
  let toastTimer = null;
  app.toast = (msg, kind) => {
    const t = A.$('#toast');
    t.textContent = msg;
    t.classList.toggle('error', kind === 'error');
    t.classList.add('on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('on'), kind === 'error' ? 5200 : 2600);
  };

  // ---------- Elegir un juego: su página o jugar directamente ----------
  app.select = (key) => {
    if (state.config.behavior.onSelect === 'play') app.launch(key);
    else A.gamepage.open(key);
  };

  // ---------- Lanzar ----------
  let launching = false;
  app.launch = async (key) => {
    const g = app.game(key);
    if (!g || launching) return;
    launching = true;
    const el = A.$('#launching');
    const art = g.art.cover || g.art.wide || g.art.hero;
    el.querySelector('.launch-art').style.backgroundImage = art ? `url("${art}")` : '';
    el.querySelector('.launch-name').textContent = `Abriendo ${g.name}`;
    el.classList.add('on');
    try {
      const updated = await api.launch(key);
      replaceGame(updated);
      await A.wait(1800);
    } catch (e) {
      app.toast(cleanError(e), 'error');
    } finally {
      el.classList.remove('on');
      launching = false;
    }
  };

  function cleanError(e) {
    return String((e && e.message) || e).replace(/^Error invoking remote method '[^']+': (Error: )?/, '');
  }
  app.cleanError = cleanError;

  function replaceGame(g) {
    if (!g) return;
    const i = state.games.findIndex((x) => x.key === g.key);
    if (i >= 0) state.games[i] = g; else state.games.push(g);
  }

  // Tras cambiar nombre, favorito u ocultar: re-render conservando el foco
  app.updateGame = (g, structural) => {
    replaceGame(g);
    if (A.gamepage.key() === g.key) A.gamepage.refresh(!!structural);
    if (structural) { render(g.hidden ? null : g.key); return; }
    A.layouts.refreshTiles(g);
    if (g.key === focusedKey && layoutCtl) { layoutCtl.update(g); backdropSrc = null; setBackdrop(g); }
  };

  app.patchConfig = async (partial, opts = {}) => {
    const next = await api.patchConfig(partial);
    state = next;
    if (opts.render !== false) render(focusedKey);
    return state;
  };
  app.setConfigLocal = (cfg) => { state.config = cfg; };

  app.rescan = async (announce) => {
    if (announce) app.toast('Buscando juegos…');
    state = await api.rescan();
    render(focusedKey);
    if (announce) app.toast(A.plural(state.games.length, 'juego encontrado', 'juegos encontrados'));
  };

  // ---------- Navegación ----------
  A.nav.on({
    focus(el) {
      const key = el.getAttribute('data-key');
      if (key && stage.contains(el)) showGame(key);
      if (el.hasAttribute('data-rr') && stage.contains(el)) A.ronron.onFocus(el);
      if (A.calendar.isOpen()) A.calendar.onFocus(el);
    },
    back() {
      const cur = A.nav.current();
      if (cur && !stage.contains(cur)) {
        const mem = A.nav.remembered(filter === 'valorados' ? 'rv' : 'games') || stage.querySelector('[data-key], [data-rr]') || stage.querySelector('[data-nav]');
        if (mem) A.nav.focus(mem);
        return;
      }
      if (filter !== 'all') { setFilter('all'); return; }
      const first = stage.querySelector('[data-key], [data-rr]');
      if (first && cur !== first) A.nav.focus(first);
    },
    y(el) {
      const key = el && el.getAttribute('data-key');
      if (key && !A.nav.inOverlay()) A.dialogs.options(key);
      const rr = el && el.getAttribute('data-rr');
      if (rr && !A.nav.inOverlay()) {
        const [k, id] = rr.split(':');
        const d = A.ronron.state().data || {};
        const item = ((k === 'g' ? d.games : d.plays) || []).find((o) => String(o.id) === id);
        if (item) A.ronron.itemMenu(k, item);
      }
    },
    x() { if (!A.nav.inOverlay()) A.dialogs.search(); },
    select() { if (!A.nav.inOverlay()) A.calendar.open(); },
    start() { if (!A.nav.inOverlay()) A.settings.open(); },
    prev() { if (!A.nav.inOverlay()) cycleFilter(-1); else A.dialogs.tab(-1); },
    next() { if (!A.nav.inOverlay()) cycleFilter(1); else A.dialogs.tab(1); },
    fullscreen() { api.toggleFullscreen(); },
    submit(input) { input.dispatchEvent(new CustomEvent('submit-input', { bubbles: true })); },
    pad() { renderHints(); },
    nonstandard() {
      app.toast('He detectado un mando sin mapeo estándar. Si algún botón no responde, configúralo en Ajustes, Mando.');
    },
  });

  A.$('#btn-search').addEventListener('click', () => A.dialogs.search());
  A.$('#btn-calendar').addEventListener('click', () => A.calendar.open());
  A.$('#btn-settings').addEventListener('click', () => A.settings.open());
  A.$('#btn-power').addEventListener('click', () => A.dialogs.power());

  // ---------- Eventos del proceso principal ----------
  api.on('art', (g) => app.updateGame(g, false));
  let pendingRender = false;
  app.afterOverlays = () => {
    if (pendingRender && !A.nav.inOverlay()) { pendingRender = false; render(focusedKey); }
  };
  api.on('library', (s) => {
    const firstLoad = document.body.classList.contains('booting');
    state = s;
    A.play.set(s.play);
    if (firstLoad || !A.nav.inOverlay()) render(focusedKey); else pendingRender = true;
    document.body.classList.remove('booting');
  });
  api.on('winfocus', (v) => A.nav.setWindowFocused(v));
  api.on('notice', (msg) => app.toast(msg));

  // Horas de juego en vivo
  api.on('play', (d) => {
    A.play.set(d);
    const g = focusedKey && app.game(focusedKey);
    // Solo se cambia la línea de datos: rehacer la cabecera haría parpadear el logo
    if (g && layoutCtl && !A.layouts.refreshMeta(stage, g)) { focusedKey = null; showGame(g.key, true); }
    A.calendar.refresh();
    if (A.gamepage.isOpen()) A.gamepage.refresh('play');
  });
  api.on('game-ended', async (key) => {
    state = await api.state();
    A.play.set(state.play);
    if (!A.nav.inOverlay()) render(key); else pendingRender = true;
  });

  // ---------- Arranque ----------
  (async function boot() {
    state = await api.state();
    A.play.set(state.play);
    filter = state.config.behavior.filter || 'all';
    try { api.log(`pantalla ${window.innerWidth}x${window.innerHeight} dpr=${window.devicePixelRatio} física=${screen.width}x${screen.height}`); } catch { /* */ }
    applyTheme();
    renderHints();
    if (state.games.length) { render(null); document.body.classList.remove('booting'); }
    setTimeout(() => {
      if (document.body.classList.contains('booting')) { render(null); document.body.classList.remove('booting'); }
    }, 2500);
    // Sin clave de SteamGridDB solo hay fondos de 1920 px: avisarlo una vez
    if (!state.config.sgdbKey && !state.config.behavior.sgdbHint) {
      setTimeout(() => {
        app.toast('Para fondos y portadas en 4K, añade tu clave gratuita de SteamGridDB en Ajustes, SteamGridDB.');
        api.patchConfig({ behavior: { sgdbHint: true } }).catch(() => {});
      }, 6000);
    }
  })();

  // ---------- Gancho de pruebas (capturas automáticas) ----------
  window.__test = async (step) => {
    if (step.pad !== undefined) {
      // Mando simulado: {id, mapping, buttons:[índices pulsados], axes:[...]}
      window.__fakePads = step.pad ? [{
        id: step.pad.id, mapping: step.pad.mapping || '', connected: true,
        buttons: Array.from({ length: 17 }, (_, i) => ({ pressed: (step.pad.buttons || []).includes(i), value: (step.pad.buttons || []).includes(i) ? 1 : 0 })),
        axes: step.pad.axes || [0, 0, 0, 0],
      }] : null;
    }
    if (step.config) await app.patchConfig(step.config);
    if (step.filter) setFilter(step.filter);
    if (step.moves) for (const m of step.moves) A.nav.dispatch(m);
    if (step.open === 'settings') A.settings.open(step.section);
    if (step.type) { const el = A.$(step.type[0]); if (el) { el.value = step.type[1]; el.dispatchEvent(new Event('input')); } }
    if (step.click) { const el = A.$(step.click); if (el) el.click(); }
    if (step.focus) { const el = A.$(step.focus); if (el) A.nav.focus(el); }
    if (step.open === 'options') A.dialogs.options(focusedKey);
    if (step.open === 'search') A.dialogs.search(step.text);
    if (step.open === 'power') A.dialogs.power();
    if (step.open === 'picker') A.dialogs.artPicker(focusedKey, step.type || 'cover');
    if (step.open === 'custom') A.dialogs.editCustom(null);
    if (step.open === 'emu') A.dialogs.editEmulator(null);
    if (step.close) A.dialogs.closeAll();
    if (step.launch) app.launch(step.launch);
    if (step.page) A.gamepage.open(step.page);
    // Pruebas: marca elementos y comprueba después si siguen siendo los mismos (no se han rehecho)
    if (step.mark) for (const sel of step.mark) { const el = A.$(sel); if (el) el.dataset.marca = '1'; }
    if (step.marked) {
      return 'marcas: ' + JSON.stringify(step.marked.map((sel) => { const el = A.$(sel); return el ? el.dataset.marca === '1' : 'no está'; }))
        + ' | ' + JSON.stringify((step.text || []).map((sel) => { const el = A.$(sel); return el ? el.textContent : null; }));
    }
    const c = A.nav.current();
    const all = A.$$('.focused').map((e) => e.className.split(' ')[0] + ':' + e.textContent.slice(0, 6)).join(',');
    return `ok ${focusedKey || ''} | foco: ${c ? (c.className + ' ' + c.textContent.slice(0, 24)) : '-'} | marcados: ${all}`;
  };
})();
