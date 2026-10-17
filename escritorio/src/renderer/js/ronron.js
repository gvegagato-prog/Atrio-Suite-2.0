'use strict';
/* global A, document, window */
// Ronrón dentro de Atrio: la pestaña «Valorados», las fichas, la edición y lo que estás jugando en la página de cada juego.
// Los datos los guarda y sincroniza el proceso principal (src/main/ronron.js); aquí solo se ven y se editan.
(function () {
  const h = A.h;
  const R = {};
  let st = { status: { signed: false }, data: null };
  let tab = 'valorados'; // o 'curso'
  let sort = 'recientes';
  let focusedId = null;

  const LABELS = ['Valoración', 'Gráficos', 'Historia', 'Música', 'Jugabilidad'];
  const FIELDS = ['general', 'gfx', 'story', 'music', 'gameplay'];
  const STATUS = ['En tu lista', 'Jugando', 'Dropeado'];

  const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/[™®©]/g, '').replace(/[^a-z0-9]+/g, '');
  const num = (v, d = 0) => (Number.isFinite(Number(v)) ? Number(v) : d);
  const games = () => (st.data && st.data.games) || [];
  const plays = () => (st.data && st.data.plays) || [];
  const scores = (o) => FIELDS.map((f) => num(o[f], 50));
  const avg4 = (o) => Math.round(FIELDS.slice(1).reduce((s, f) => s + num(o[f], 50), 0) / 4);
  const tone = (v) => (v >= 85 ? 'top' : v >= 70 ? 'good' : v >= 50 ? 'mid' : 'low');
  const isLocal = (u) => !u || /^(content|file):/i.test(u);
  const remote = (u) => (isLocal(u) ? '' : u);

  function baremo(p) {
    const es = p.entries || [];
    if (!es.length) return null;
    const o = {};
    for (const f of FIELDS) o[f] = Math.round(es.reduce((s, e) => s + num(e[f], 50), 0) / es.length);
    return o;
  }

  // Días desde 1970 (como Ronrón: LocalDate.toEpochDay)
  const todayDay = () => { const d = new Date(); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); };
  const dayFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const fmtDay = (d) => (d > 0 ? dayFmt.format(new Date(d * 86400000)) : '');
  const isoDay = (d) => (d > 0 ? new Date(d * 86400000).toISOString().slice(0, 10) : '');
  const parseIso = (s) => { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || ''); return m ? Math.floor(Date.UTC(+m[1], +m[2] - 1, +m[3]) / 86400000) : 0; };
  const fmtMin = (m) => (m > 0 ? A.play.fmt(m * 60000) : '');
  const noteFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

  // ---------------------------------------------------------------- Juego de Atrio ↔ ficha de Ronrón
  // Por el id de SteamGridDB (Ronrón usa ese mismo id) o, si no, por el nombre
  function libGameFor(item) {
    if (!item) return null;
    const all = A.app.state().games || [];
    return all.find((g) => g.sgdbKnown && Number(g.sgdbKnown) === Number(item.id))
      || all.find((g) => norm(g.name) === norm(item.name) || norm(g.originalName) === norm(item.name)) || null;
  }
  function entryFor(game) {
    if (!st.data || !game) return null;
    const byId = (l) => game.sgdbKnown && l.find((o) => Number(o.id) === Number(game.sgdbKnown));
    const byName = (l) => l.find((o) => norm(o.name) === norm(game.name) || norm(o.name) === norm(game.originalName));
    const p = byId(plays()) || byName(plays());
    const g = byId(games()) || byName(games());
    // Lo que estás jugando tiene prioridad (si lo rejuegas, también lo tendrás valorado)
    if (p && p.status === 1) return { type: 'play', item: p, rated: g || null };
    if (g) return { type: 'game', item: g, play: p || null };
    if (p) return { type: 'play', item: p, rated: null };
    return null;
  }
  R.entryFor = entryFor;

  // Para el fondo y la portada: el arte local de Atrio (4K) si el juego está en la biblioteca; si no, el de la ficha
  function artOf(item) {
    const g = libGameFor(item);
    return {
      cover: (g && g.art.cover) || remote(item.cover),
      hero: (g && (g.art.herohd || g.art.hero || g.art.wide)) || remote(item.hero) || remote(item.cover),
      logo: (g && g.art.logo) || remote(item.logo),
      game: g,
    };
  }

  // ---------------------------------------------------------------- Estado
  R.state = () => st;
  R.signed = () => !!(st.status && st.status.signed);
  R.count = () => games().length;
  function set(s, why) {
    if (!s) return;
    const before = JSON.stringify(st.data);
    st = s;
    const changed = JSON.stringify(st.data) !== before;
    if (changed || why === 'status') {
      if (A.app.filter && A.app.filter() === 'valorados' && !A.nav.inOverlay()) A.app.render(focusedId);
      else if (A.app.renderFiltersOnly) A.app.renderFiltersOnly();
      if (A.gamepage.isOpen()) A.gamepage.refresh(true);
      if (detail) detail.redraw();
      if (A.calendar && A.calendar.isOpen()) A.calendar.refresh();
    }
  }
  R.load = async () => { try { set(await A.api.ronron(), 'status'); } catch { /* */ } };
  A.api.on('ronron', (s) => set(s));

  async function edit(op, okMsg) {
    try {
      const s = await A.api.ronronEdit(op);
      set(s, 'status');
      if (s.status.err) A.app.toast(s.status.err, 'error');
      else if (okMsg) A.app.toast(okMsg);
      return true;
    } catch (e) {
      A.app.toast(A.app.cleanError(e), 'error');
      return false;
    }
  }

  // ---------------------------------------------------------------- Entrar
  function loginForm(onDone) {
    const user = A.dialogs.input('', { placeholder: 'Usuario', autocomplete: 'username', 'data-autofocus': true });
    const pass = A.dialogs.input('', { type: 'password', placeholder: 'Contraseña', autocomplete: 'current-password' });
    const key = A.dialogs.input('', { placeholder: 'Clave web de Firebase (empieza por AIza)', class: 'input mono' });
    const keyField = A.dialogs.field('Clave web de Firebase', key,
      'Solo la primera vez, si Atrio en el móvil todavía no la ha dejado guardada. Está en la consola de Firebase → Configuración del proyecto → General → Clave de API web.');
    keyField.style.display = st.status && st.status.needKey && st.status.askKey ? '' : 'none';
    const msg = h('p.note.rr-msg');
    const go = async () => {
      msg.textContent = 'Entrando…';
      try {
        const s = await A.api.ronronLogin(user.value, pass.value, key.value.trim() || undefined);
        pass.value = '';
        set(s, 'status');
        A.app.toast(`Hola, ${s.status.user}. ${A.plural(games().length, 'juego valorado', 'juegos valorados')}.`);
        if (onDone) onDone();
      } catch (e) {
        const m = A.app.cleanError(e);
        if (m === 'FALTA_CLAVE') {
          keyField.style.display = '';
          st.status.askKey = true;
          msg.textContent = 'Falta la clave web de Firebase. Pégala abajo y vuelve a pulsar Entrar.';
          A.nav.focus(key);
        } else msg.textContent = m;
      }
    };
    for (const i of [user, pass, key]) i.addEventListener('submit-input', () => (i === user ? A.nav.focus(pass) : go()));
    return h('div.rr-login',
      A.dialogs.field('Usuario de Atrio', user),
      A.dialogs.field('Contraseña', pass),
      keyField,
      h('div.row', h('button.btn.primary', { 'data-nav': true, onclick: go }, 'Entrar')),
      msg);
  }

  // ---------------------------------------------------------------- Control de nota (mando: izquierda/derecha)
  function scoreControl(label, value, onChange) {
    let v = Math.max(0, Math.min(100, num(value, 50)));
    let run = 0;
    let lastAt = 0;
    const fill = h('span.rr-fill');
    const out = h('span.rr-val', String(v));
    const el = h('button.rr-slider', { 'data-nav': true, 'data-zone': 'scores', type: 'button', role: 'slider', 'aria-label': label },
      h('span.rr-lab', label), h('span.rr-track', fill), out);
    const paint = () => {
      fill.style.width = `${v}%`;
      out.textContent = String(v);
      el.dataset.tone = tone(v);
      el.setAttribute('aria-valuenow', String(v));
    };
    const setV = (nv) => { v = Math.max(0, Math.min(100, Math.round(nv))); paint(); onChange(v); };
    // Al mantener pulsado, va cada vez más deprisa
    el.__adjust = (d) => {
      const now = performance.now();
      run = now - lastAt < 160 ? run + 1 : 0;
      lastAt = now;
      setV(v + d * (run > 30 ? 5 : run > 10 ? 2 : 1));
    };
    const track = el.querySelector('.rr-track');
    const fromPointer = (e) => { const r = track.getBoundingClientRect(); setV(((e.clientX - r.left) / r.width) * 100); };
    track.addEventListener('pointerdown', (e) => { e.preventDefault(); track.setPointerCapture(e.pointerId); fromPointer(e); track.onpointermove = fromPointer; });
    track.addEventListener('pointerup', () => { track.onpointermove = null; });
    el.addEventListener('wheel', (e) => { e.preventDefault(); e.stopPropagation(); setV(v + (e.deltaY < 0 ? 1 : -1)); }, { passive: false });
    el.addEventListener('click', (e) => { if (!e.target.closest('.rr-track')) A.nav.focus(el); });
    paint();
    return el;
  }

  function ring(v, big) {
    return h(`div.rr-ring${big ? '.big' : ''}`, { 'data-tone': tone(v), style: { '--p': String(v) } }, h('span', String(v)));
  }

  function bars(o) {
    return h('div.rr-bars', FIELDS.slice(1).map((f, i) => h('div.rr-bar',
      h('span.k', LABELS[i + 1]), h('span.t', h('span', { style: { width: `${num(o[f], 50)}%` }, 'data-tone': tone(num(o[f], 50)) })), h('span.v', String(num(o[f], 50))))));
  }

  // ---------------------------------------------------------------- Pestaña «Valorados»
  function sorted(list) {
    const l = [...list];
    if (sort === 'nota') return l.sort((a, b) => num(b.general) - num(a.general) || avg4(b) - avg4(a));
    if (sort === 'nombre') return l.sort((a, b) => String(a.name).localeCompare(String(b.name), 'es'));
    const act = (o) => Math.max(num(o.updated), num(o.end) * 86400000, num(o.start) * 86400000,
      ...((o.entries || []).map((e) => num(e.time))));
    return l.sort((a, b) => act(b) - act(a));
  }

  function itemsForTab() {
    if (tab === 'curso') return sorted(plays().filter((p) => p.status === 1));
    return sorted(games());
  }

  function tileFor(item, kind) {
    const a = artOf(item);
    const id = `${kind}:${item.id}`;
    const el = h('button.tile.rv-tile', {
      'data-nav': true, 'data-zone': 'rv', 'data-rr': id, 'data-shape': 'cover',
      'aria-label': item.name, style: { '--ph-h': String(A.layouts.hueOf(item.name)) },
      onclick: () => (kind === 'g' ? R.openGame(item.id) : R.openPlay(item.id)),
    });
    el.append(h('div.ph', h('span', item.name)));
    if (a.cover) {
      const img = h('img', { alt: '', loading: 'lazy', decoding: 'async', draggable: 'false' });
      img.onload = () => { img.classList.add('ok'); el.classList.add('has-art'); };
      img.onerror = () => img.remove();
      img.src = a.cover;
      el.append(img);
    }
    if (kind === 'g') el.append(h('span.rv-badge', { 'data-tone': tone(num(item.general)) }, String(num(item.general))));
    else {
      const b = baremo(item);
      el.append(h('span.rv-badge.curso', b ? String(b.general) : '·'));
    }
    if (item.fav) el.append(h('span.fav', A.icon('starFill')));
    return el;
  }

  function caption(item, kind) {
    if (!item) return [];
    const parts = [];
    const o = kind === 'g' ? item : baremo(item);
    const a0 = artOf(item);
    const left = h('div.rv-cap-main', logoOr(a0.logo, item.name, 'div.name'));
    const meta = [];
    if (kind === 'g') {
      if (item.start > 0 && item.end > 0) meta.push(`${fmtDay(item.start)} – ${fmtDay(item.end)}`);
      else if (item.end > 0) meta.push(`Terminado el ${fmtDay(item.end)}`);
    } else {
      meta.push(item.entries && item.entries.length ? A.plural(item.entries.length, 'apunte', 'apuntes') : 'Sin apuntes todavía');
      if (item.start > 0) meta.push(`Empezado el ${fmtDay(item.start)}`);
    }
    if (item.minutes > 0) meta.push(fmtMin(item.minutes));
    if (item.platform) meta.push(item.platform);
    if (libGameFor(item)) meta.push('En tu biblioteca');
    left.append(h('div.meta', meta.map((m) => h('span', m))));
    const text = kind === 'g' ? item.review : ((item.entries || []).filter((e) => e.note).slice(-1)[0] || {}).note;
    if (text) left.append(h('p.rv-review', text));
    parts.push(left);
    if (o) parts.push(h('div.rv-cap-score', ring(num(o.general), true), bars(o)));
    return parts;
  }

  R.render = (stage, keep) => {
    if (!R.signed()) {
      stage.append(h('div.empty.rr-empty',
        h('h2', 'Tus juegos valorados'),
        h('p', 'Entra con tu cuenta de Atrio para ver aquí tus notas y opiniones, editarlas y apuntar lo que estés jugando. Todo lo que cambies aquí aparece en el móvil, y al revés.'),
        loginForm(() => A.app.render(null))));
      A.app.setBackdropFor(null);
      if (!A.nav.inOverlay()) A.nav.focusFirst('.rr-login input');
      return;
    }
    const list = itemsForTab();
    const kind = tab === 'curso' ? 'p' : 'g';
    const cap = h('div.rv-caption');
    const grid = h('div.rv-grid', list.map((it) => tileFor(it, kind)));
    const nCurso = plays().filter((p) => p.status === 1).length;
    const tabs = A.dialogs.seg([['valorados', `Valorados · ${games().length}`], ['curso', `En curso · ${nCurso}`]], tab, (v) => {
      tab = v; focusedId = null; A.app.render(null);
      setTimeout(() => A.nav.focus(A.$(`.rv-head .seg [data-v="${v}"]`)), 0);
    }, 'rv-head');
    const sorts = A.dialogs.seg([['recientes', 'Recientes'], ['nota', 'Nota'], ['nombre', 'Nombre']], sort, (v) => {
      sort = v; A.app.render(focusedId);
      setTimeout(() => A.nav.focus(A.$(`.rv-sort [data-v="${v}"]`)), 0);
    }, 'rv-head');
    sorts.classList.add('rv-sort');
    const s = st.status;
    const head = h('div.rv-head',
      tabs, sorts,
      h('span.rv-sync', s.err ? s.err : s.lastSync ? `Sincronizado ${A.timeAgo(s.lastSync)} · ${s.user}` : s.user),
      h('button.btn', { 'data-nav': true, 'data-zone': 'rv-head', onclick: () => R.newRating() }, A.icon('plus'), 'Valorar un juego'));
    const body = list.length ? h('div.rv-scroll.scroller', grid)
      : h('div.rv-none', h('p', tab === 'curso' ? 'No estás jugando nada ahora mismo.' : 'Todavía no has valorado ningún juego.'));
    stage.append(h('div.rv', head, body, cap));

    const show = (el) => {
      const [k, id] = el.getAttribute('data-rr').split(':');
      focusedId = el.getAttribute('data-rr');
      const item = (k === 'g' ? games() : plays()).find((o) => String(o.id) === id);
      if (!item) return;
      cap.replaceChildren(...caption(item, k));
      const a = artOf(item);
      A.app.setBackdropFor({ key: `rr:${focusedId}`, name: item.name, art: { hero: a.hero, cover: a.cover } });
    };
    R._show = show;
    const target = (keep && grid.querySelector(`[data-rr="${CSS.escape(keep)}"]`)) || grid.firstElementChild;
    if (target) { show(target); if (!A.nav.inOverlay()) A.nav.focus(target); }
    else { A.app.setBackdropFor(null); if (!A.nav.inOverlay()) A.nav.focusFirst('.rv-head .btn'); }
  };
  R.onFocus = (el) => { if (el.hasAttribute('data-rr') && R._show) R._show(el); };
  R.focusedId = () => focusedId;

  // ---------------------------------------------------------------- Ficha (valorado o en curso)
  let detail = null;
  function openDetail(kind, id) {
    const find = () => (kind === 'g' ? games() : plays()).find((o) => Number(o.id) === Number(id));
    if (!find()) return;
    if (detail) detail.layer.close();
    const layer = A.dialogs.open('full', (sheet) => { sheet.classList.add('gp', 'rr-detail'); }, { onClose: () => { detail = null; } });
    const root = layer.sheet;
    const draw = (focusSel) => {
      const item = find();
      if (!item) { layer.close(); return; }
      const a = artOf(item);
      const bg = h('div.gp-hero', { style: { '--ph-h': String(A.layouts.hueOf(item.name)) } });
      if (a.hero) { const img = new Image(); img.onload = () => { bg.style.backgroundImage = `url("${a.hero}")`; bg.classList.add('on'); }; img.src = a.hero; }
      const title = h('div.gp-title', a.game && a.game.art.logo ? A.layouts.titleBlock(a.game) : logoOr(a.logo, item.name, 'h2.title-text'));
      const lib = a.game;
      const actions = [];
      if (kind === 'g') {
        actions.push(h('button.btn.primary.gp-play', { 'data-nav': true, 'data-zone': 'gp-actions', 'data-autofocus': true, onclick: () => R.editGame(item) }, A.icon('edit'), 'Editar'));
      } else {
        actions.push(h('button.btn.primary.gp-play', { 'data-nav': true, 'data-zone': 'gp-actions', 'data-autofocus': true, onclick: () => R.addNote(item) }, A.icon('edit'), 'Apuntar'));
        actions.push(h('button.btn.gp-btn', { 'data-nav': true, 'data-zone': 'gp-actions', onclick: () => R.memo(item) }, A.icon('note'), 'Apunte rápido'));
        actions.push(h('button.btn.gp-btn', { 'data-nav': true, 'data-zone': 'gp-actions', onclick: () => R.finish(item) }, A.icon('check'), 'Terminado'));
      }
      if (lib) actions.push(h('button.btn.gp-btn', { 'data-nav': true, 'data-zone': 'gp-actions', onclick: () => { layer.close(); A.app.launch(lib.key); } }, A.icon('play'), 'Jugar'));
      actions.push(h('button.icon-btn.gp-icon', { 'data-nav': true, 'data-zone': 'gp-actions', 'aria-label': 'Cambiar portada, fondo y logo', onclick: () => R.pickArt(kind, item, 'hero') }, A.icon('image')));
      actions.push(h('button.icon-btn.gp-icon', { 'data-nav': true, 'data-zone': 'gp-actions', 'aria-label': 'Más opciones', onclick: () => R.itemMenu(kind, item) }, A.icon('more')));

      const o = kind === 'g' ? item : baremo(item);
      const scoreBox = h('div.rr-scorebox', o ? [ring(num(o.general), true), h('div', h('span.rr-sub', kind === 'g' ? 'Tu nota' : 'Media de tus apuntes'), bars(o))]
        : h('p.gp-empty', 'Aún no has puesto notas. Pulsa Apuntar.'));

      const rows = [];
      const row = (k, v) => { if (v) rows.push(h('div.gp-row', h('span.k', k), h('span.v', v))); };
      if (kind === 'g') { row('Empezado', fmtDay(item.start)); row('Terminado', fmtDay(item.end)); }
      else { row('Estado', STATUS[item.status] || ''); row('Empezado', fmtDay(item.start)); }
      row('Tiempo jugado', fmtMin(item.minutes));
      row('Plataforma', item.platform);
      row('Estudio', item.creator);
      if (item.ttbMain > 0) row('Duración media', fmtMin(item.ttbMain));
      if (kind === 'p' && item.parts && item.parts.length) row('Capítulos', `${item.parts.filter((p) => p.d).length} de ${item.parts.length}`);
      const dataBox = h('section.gp-block', h('h3', 'Datos'), rows.length ? h('div.gp-rows', rows) : h('p.gp-empty', 'Sin datos.'));

      let textBox;
      if (kind === 'g') {
        textBox = h('section.gp-block.rr-text', h('h3', 'Tu opinión'),
          item.review ? h('div.rr-review.scroller', { 'data-nav': true, 'data-zone': 'rr-text', tabindex: '0' }, item.review) : h('p.gp-empty', 'Sin opinión escrita.'));
      } else {
        const es = [...(item.entries || [])].sort((x, y) => num(y.time) - num(x.time));
        // Apuntes con nota y apuntes rápidos, del más reciente al más antiguo
        const memos = (item.memos || []).filter((m) => m && !m.del);
        const rows = [
          ...[...es.map((e) => ({ t: num(e.time), e })), ...memos.map((m) => ({ t: m.t, m }))].sort((a, b) => b.t - a.t).map((x) => (x.e
            ? h('li', { 'data-nav': true, 'data-zone': 'rr-text', onclick: () => R.addNote(item, x.e) },
              h('div.rr-note-head', h('span.d', x.e.time ? noteFmt.format(new Date(x.e.time)) : ''),
                h('span.s', { 'data-tone': tone(num(x.e.general)) }, String(num(x.e.general)))),
              x.e.note ? h('p', x.e.note) : null)
            : h('li.rr-memo', { 'data-nav': true, 'data-zone': 'rr-text', onclick: () => R.memo(item, x.m) },
              h('div.rr-note-head', h('span.d', `${noteFmt.format(new Date(x.m.t))} · apunte rápido`), x.m.sh ? h('span.s.cam', '📷') : null),
              x.m.x ? h('p', x.m.x) : null))),
        ];
        textBox = h('section.gp-block.rr-text', h('h3', `Apuntes${es.length + memos.length ? ` · ${es.length + memos.length}` : ''}`),
          rows.length ? h('ol.rr-notes.scroller', rows)
            : h('p.gp-empty', 'Todavía no hay apuntes. Pulsa Apuntar para guardar notas y comentarios provisionales, o Apunte rápido para una idea suelta.'));
      }

      root.replaceChildren(bg, h('div.gp-shade'),
        h('div.gp-content',
          h('div.gp-top', h('span.rr-kicker', kind === 'g' ? 'Valorado' : `${STATUS[item.status] || 'En curso'}`), title, h('div.gp-actions', actions, scoreBox)),
          h('div.gp-bottom.rr-bottom', textBox, dataBox)));
      A.nav.focusFirst(focusSel || '.gp-play');
    };
    detail = {
      layer, kind, id,
      redraw: () => {
        const all = A.$$('[data-nav]', root);
        const i = all.indexOf(A.nav.current());
        draw();
        const again = A.$$('[data-nav]', root);
        if (i >= 0 && again[i]) A.nav.focus(again[i], { noScroll: true });
      },
    };
    draw();
  }
  R.openGame = (id) => openDetail('g', id);
  R.openPlay = (id) => openDetail('p', id);

  R.itemMenu = (kind, item) => {
    A.dialogs.open('side', (sheet, layer) => {
      const a = artOf(item);
      const mi = (icon, label, fn, danger) => h(`button.menu-item${danger ? '.danger' : ''}`, { 'data-nav': true, onclick: () => { layer.close(); fn(); } }, A.icon(icon), h('span', label));
      sheet.append(
        h('div.side-head', h('div.cv', { style: a.cover ? { backgroundImage: `url("${a.cover}")` } : {} }), h('div', h('h2', item.name), h('p', kind === 'g' ? 'Valorado' : STATUS[item.status] || ''))),
        h('div.menu',
          kind === 'g' ? mi('edit', 'Editar valoración', () => R.editGame(item)) : mi('edit', 'Apuntar', () => R.addNote(item)),
          kind === 'p' ? mi('check', 'Terminado: pasar a valorados', () => R.finish(item)) : null,
          kind === 'p' && item.status !== 2 ? mi('hide', 'Dropear', () => edit({ op: 'savePlay', kind: 0, item: { ...item, status: 2 } }, `${item.name}: dropeado`)) : null,
          kind === 'p' && item.status !== 1 ? mi('play', 'Seguir jugando', () => edit({ op: 'savePlay', kind: 0, item: { ...item, status: 1, start: item.start || todayDay() } }, `Jugando a ${item.name}`)) : null,
          kind === 'g' ? mi(item.fav ? 'starFill' : 'star', item.fav ? 'Quitar de favoritos' : 'Favorito', () => edit({ op: 'saveGame', kind: 0, item: { ...item, fav: !item.fav } })) : null,
          mi('image', 'Cambiar portada, fondo y logo', () => R.pickArt(kind, item)),
          h('div.menu-sep'),
          mi('trash', 'Borrar la ficha', () => A.dialogs.confirm(`Borrar ${item.name}`,
            'Se borra en todos tus dispositivos (también en el móvil).', 'Borrar', async () => {
              if (await edit({ op: kind === 'g' ? 'deleteGame' : 'deletePlay', kind: 0, id: item.id }, 'Borrado')) if (detail) detail.layer.close();
            }), true)));
    });
  };

  // Logo (si lo hay) o el nombre en texto
  function logoOr(src, name, sel) {
    const text = () => h(sel, name);
    if (!src) return text();
    const img = h('img.title-art.rr-logo', { alt: name, decoding: 'async', draggable: 'false' });
    img.onerror = () => img.replaceWith(text());
    img.src = src;
    return img;
  }

  // ---------------------------------------------------------------- Portada, fondo y logo
  // Se eligen en SteamGridDB. Ronrón guarda la dirección (así también se ven en el móvil) y, si el juego
  // está en tu biblioteca, Atrio descarga además su copia a máxima calidad para ese mismo juego.
  const ART_TABS = [['cover', 'Portada', 'cover'], ['hero', 'Fondo', 'hero'], ['logo', 'Logo', 'logo']];
  R.pickArt = (kind, item0, startType) => {
    if (!A.app.state().config.sgdbKey) { A.app.toast('Para elegir arte necesitas tu clave de SteamGridDB (Ajustes, SteamGridDB).', 'error'); return; }
    let item = item0;
    let sgdbId = item.id;
    A.dialogs.open('center', (sheet, layer) => {
      let type = startType || 'cover';
      let page = 0;
      let token = 0;
      const tabs = h('div.tabs');
      const body = h('div.sheet-body');
      const current = () => item[type] || '';
      const renderTabs = () => tabs.replaceChildren(...ART_TABS.map(([t, label]) =>
        h('button.chip', { 'data-nav': true, 'data-zone': 'tabs', 'aria-pressed': String(t === type), onclick: () => switchTo(t) }, label)));
      const switchTo = (t, focusTab) => {
        type = t; page = 0;
        renderTabs();
        if (focusTab) A.nav.focus(tabs.querySelector('[aria-pressed="true"]'), { noScroll: true });
        load(false);
      };
      layer.tabs = (step) => {
        const i = ART_TABS.findIndex(([t]) => t === type);
        switchTo(ART_TABS[(i + step + ART_TABS.length) % ART_TABS.length][0], true);
      };

      async function choose(it, el) {
        el.style.opacity = '.5';
        const next = { ...item, [type]: it.url };
        const ok = await edit({ op: kind === 'g' ? 'saveGame' : 'savePlay', kind: 0, item: next });
        if (ok) {
          item = next;
          const lib = libGameFor(item);
          if (lib) {
            try { A.app.updateGame(await A.api.setArt(lib.key, type, it.url), false); } catch (e) { /* la ficha ya está guardada */ }
          }
          for (const p of body.querySelectorAll('.pick.current')) p.classList.remove('current');
          el.classList.add('current');
          A.app.toast(lib ? 'Guardado en la ficha y en tu biblioteca' : 'Guardado');
          if (A.app.filter() === 'valorados' && !detail) A.app.render(focusedId);
        }
        el.style.opacity = '';
      }

      async function load(more) {
        const my = ++token;
        if (!more) body.replaceChildren(h('div.state-msg', 'Buscando en SteamGridDB…'));
        try {
          const res = await A.api.ronronAssets(sgdbId, type, page);
          if (my !== token) return;
          let grid = body.querySelector('.picker-grid');
          if (!more || !grid) {
            body.replaceChildren();
            if (!res.items.length) {
              body.append(h('div.state-msg', 'SteamGridDB no tiene este tipo de arte para este juego. Prueba otra pestaña o «Buscar el juego».'));
              A.nav.focusFirst('.tabs [aria-pressed="true"]');
              return;
            }
            grid = h(`div.picker-grid.${type}`);
            body.append(grid);
          }
          const firstNew = grid.children.length;
          const items = type === 'hero' ? [...res.items].sort((a, b) => (b.width || 0) - (a.width || 0)) : res.items;
          for (const it of items) {
            const el = h('button.pick', { 'data-nav': true, 'data-zone': 'picks', title: it.author ? `Por ${it.author}` : '' });
            el.append(h('img', { alt: '', loading: 'lazy', decoding: 'async', src: it.thumb }),
              h('span.by', [it.width && it.height ? `${it.width}×${it.height}` : '', it.author ? `  ${it.author}` : ''].join('')));
            if (type === 'hero' && it.width) el.append(h('span.res', it.width >= 3000 ? '4K' : `${it.width} px`));
            if (it.url === current()) el.classList.add('current');
            el.addEventListener('click', () => choose(it, el));
            grid.append(el);
          }
          const moreBtn = body.querySelector('.more');
          if (moreBtn) moreBtn.remove();
          if (res.items.length >= 50) {
            body.append(h('div', { style: { display: 'flex', justifyContent: 'center', padding: '.5rem 0 1rem' } },
              h('button.btn.more', { 'data-nav': true, onclick: () => { page++; load(true); } }, 'Cargar más')));
          }
          A.nav.focus(grid.children[firstNew] || grid.children[0]);
        } catch (e) {
          if (my !== token) return;
          body.replaceChildren(h('div.state-msg', A.app.cleanError(e)));
          A.nav.focusFirst('.tabs [aria-pressed="true"]');
        }
      }

      // Si la ficha no corresponde a un juego de SteamGridDB (o es otro), se busca por nombre solo para el arte
      const searchOther = () => {
        A.dialogs.open('center', (s2, l2) => {
          const inp = A.dialogs.input(item.name, { 'data-autofocus': true });
          const results = h('div.results');
          const go = async () => {
            results.replaceChildren(h('div.state-msg', 'Buscando…'));
            try {
              const list = await A.api.sgdbSearch(inp.value.trim());
              results.replaceChildren(...list.map((r) => h('button.result', {
                'data-nav': true, onclick: () => { sgdbId = r.id; l2.close(); load(false); },
              }, h('span', r.name), r.year ? h('span.y', String(r.year)) : null)));
              if (list.length) A.nav.focus(results.firstChild);
              else results.append(h('div.state-msg', 'Sin resultados.'));
            } catch (e) { results.replaceChildren(h('div.state-msg', A.app.cleanError(e))); }
          };
          inp.addEventListener('submit-input', go);
          s2.append(h('div.sheet-head', h('h2', 'Buscar el juego en SteamGridDB')),
            h('div.sheet-body', h('div.input-row', inp, h('button.btn.primary', { 'data-nav': true, onclick: go }, 'Buscar')), results),
            h('div.sheet-foot', h('button.btn', { 'data-nav': true, onclick: () => l2.close() }, 'Cerrar')));
          go();
        }, { focus: 'input' });
      };

      sheet.append(
        h('div.sheet-head', h('h2', `Arte de ${item.name}`)), tabs, body,
        h('div.sheet-foot',
          h('button.btn.quiet', { 'data-nav': true, onclick: searchOther }, 'Buscar el juego'),
          h('button.btn.quiet', {
            'data-nav': true,
            onclick: async () => {
              const next = { ...item, [type]: '' };
              if (await edit({ op: kind === 'g' ? 'saveGame' : 'savePlay', kind: 0, item: next }, 'Quitado de la ficha')) {
                item = next;
                for (const p of body.querySelectorAll('.pick.current')) p.classList.remove('current');
              }
            },
          }, 'Quitar de la ficha'),
          h('span', { style: { flex: 1 } }),
          h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Hecho')));
      renderTabs();
      load(false);
    });
  };

  // ---------------------------------------------------------------- Editar una valoración
  R.editGame = (item0, opts = {}) => {
    const item = { general: 50, gfx: 50, story: 50, music: 50, gameplay: 50, review: '', hero: '', start: 0, end: 0, creator: '', fav: false, platform: '', pages: 0, minutes: 0, ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '', ...item0 };
    A.dialogs.open('center', (sheet, layer) => {
      const v = scores(item);
      const sliders = LABELS.map((l, i) => scoreControl(l, v[i], (nv) => { item[FIELDS[i]] = nv; }));
      sliders[0].classList.add('main');
      const review = h('textarea.input.rr-area', { 'data-nav': true, rows: '6', spellcheck: 'true', placeholder: '¿Qué te ha parecido?' });
      review.value = item.review || '';
      const start = dateInput(item.start);
      const end = dateInput(item.end);
      const hours = A.dialogs.input(item.minutes ? String(Math.round((item.minutes / 60) * 10) / 10) : '', { inputmode: 'decimal', placeholder: 'Horas' });
      const platform = A.dialogs.input(item.platform || '', { placeholder: 'PC, Switch…' });
      const lib = libGameFor(item);
      if (!item.minutes && lib) {
        const ms = A.play.best(lib).ms;
        if (ms >= 60000) hours.value = String(Math.round((ms / 3600000) * 10) / 10);
      }
      if (!item.platform && lib) platform.value = 'PC';
      const save = async () => {
        const hv = parseFloat(String(hours.value).replace(',', '.'));
        const out = {
          ...item, review: review.value.trim(), start: parseIso(start.value), end: parseIso(end.value),
          minutes: Number.isFinite(hv) && hv > 0 ? Math.round(hv * 60) : 0, platform: platform.value.trim(),
        };
        if (await edit({ op: 'saveGame', kind: 0, item: out }, opts.isNew ? `${item.name} valorado` : 'Guardado')) {
          layer.close();
          if (opts.isNew && A.app.filter() === 'valorados') A.app.render(`g:${item.id}`);
          if (opts.then) opts.then();
        }
      };
      const todayBtn = (inp) => h('button.btn.quiet', { 'data-nav': true, type: 'button', onclick: () => { inp.value = isoDay(todayDay()); } }, 'Hoy');
      sheet.append(
        h('div.sheet-head', h('h2', opts.isNew ? `Valorar ${item.name}` : `Editar ${item.name}`), h('span.rr-avg', `Media de criterios: `, h('b', String(avg4(item))))),
        h('div.sheet-body.rr-form',
          h('div.rr-sliders', sliders),
          A.dialogs.field('Tu opinión', review),
          h('div.rr-grid2',
            A.dialogs.field('Empezado', h('div.input-row', start, todayBtn(start))),
            A.dialogs.field('Terminado', h('div.input-row', end, todayBtn(end))),
            A.dialogs.field('Horas jugadas', hours),
            A.dialogs.field('Plataforma', platform))),
        h('div.sheet-foot',
          h('span.rr-hint', 'Notas: izquierda y derecha en el mando (mantén para ir más rápido)'),
          h('span', { style: { flex: 1 } }),
          h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Cancelar'),
          h('button.btn.primary', { 'data-nav': true, onclick: save }, 'Guardar')));
      const avgEl = sheet.querySelector('.rr-avg b');
      sheet.addEventListener('click', () => { avgEl.textContent = String(avg4(item)); });
      for (const s of sliders) { const old = s.__adjust; s.__adjust = (d) => { old(d); avgEl.textContent = String(avg4(item)); }; }
    }, { focus: '.rr-slider' });
  };

  // ---------------------------------------------------------------- Apuntes de lo que estás jugando
  // Apunte rápido (texto suelto, como en el móvil). memo: editar uno que ya existe
  R.memo = (play, memo) => {
    A.dialogs.open('center', (sheet, layer) => {
      const note = h('textarea.input.rr-area', { 'data-nav': true, rows: '5', spellcheck: 'true', placeholder: 'Una idea, algo que no quieres olvidar…' });
      note.value = memo ? memo.x || '' : '';
      const save = async () => {
        const t = memo ? memo.t : Date.now();
        if (await edit({ op: 'memo', kind: 0, id: play.id, memo: { t, x: note.value.trim(), sh: memo ? memo.sh || '' : '' } }, 'Apunte guardado')) layer.close();
      };
      const del = memo ? h('button.btn.quiet.danger', {
        'data-nav': true, onclick: async () => { if (await edit({ op: 'memo', kind: 0, id: play.id, memo: { t: memo.t }, remove: true }, 'Apunte borrado')) layer.close(); },
      }, 'Borrar') : null;
      sheet.append(
        h('div.sheet-head', h('h2', `Apunte rápido · ${play.name}`)),
        h('div.sheet-body.rr-form', A.dialogs.field('Texto', note), memo && memo.sh ? h('p.note', 'Este apunte tiene una captura: la ves en el móvil y en Imágenes/Atrio.') : null),
        h('div.sheet-foot', del, h('span', { style: { flex: 1 } }),
          h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Cancelar'),
          h('button.btn.primary', { 'data-nav': true, onclick: save }, 'Guardar')));
    }, { focus: 'textarea' });
  };

  R.addNote = (play, entry, opts = {}) => {
    const last = entry || (play.entries || []).slice(-1)[0] || baremo(play) || {};
    const e = { general: num(last.general, 50), gfx: num(last.gfx, 50), story: num(last.story, 50), music: num(last.music, 50), gameplay: num(last.gameplay, 50) };
    A.dialogs.open('center', (sheet, layer) => {
      const sliders = LABELS.map((l, i) => scoreControl(l, e[FIELDS[i]], (nv) => { e[FIELDS[i]] = nv; }));
      sliders[0].classList.add('main');
      const note = h('textarea.input.rr-area', { 'data-nav': true, rows: '5', spellcheck: 'true', placeholder: 'Lo que llevas, lo que te está gustando o no…' });
      note.value = entry ? entry.note || '' : '';
      const save = async () => {
        const list = [...(play.entries || [])];
        const ne = { time: entry ? entry.time : Date.now(), ...e, note: note.value.trim(), chapter: entry ? num(entry.chapter) : num(play.chapter) };
        const i = entry ? list.findIndex((x) => x.time === entry.time) : -1;
        if (i >= 0) list[i] = ne; else list.push(ne);
        const lib = libGameFor(play);
        const ms = lib ? A.play.best(lib).ms : 0;
        const minutes = Math.max(num(play.minutes), Math.round(ms / 60000));
        if (await edit({ op: 'savePlay', kind: 0, item: { ...play, entries: list, minutes } }, entry ? 'Apunte guardado' : 'Apuntado')) layer.close();
      };
      const del = entry ? h('button.btn.quiet.danger', {
        'data-nav': true,
        onclick: async () => {
          const list = (play.entries || []).filter((x) => x.time !== entry.time);
          if (await edit({ op: 'savePlay', kind: 0, item: { ...play, entries: list } }, 'Apunte borrado')) layer.close();
        },
      }, 'Borrar apunte') : null;
      sheet.append(
        h('div.sheet-head', h('h2', entry ? `Apunte de ${play.name}` : `Apuntar · ${play.name}`)),
        h('div.sheet-body.rr-form', h('div.rr-sliders', sliders), A.dialogs.field('Nota', note)),
        h('div.sheet-foot', del, h('span', { style: { flex: 1 } }),
          h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Cancelar'),
          h('button.btn.primary', { 'data-nav': true, onclick: save }, 'Guardar')));
    }, { focus: '.rr-slider' });
  };

  R.finish = (play) => {
    A.dialogs.confirm(`¿Has terminado ${play.name}?`,
      'Pasa a tus valorados con la media de tus apuntes como nota y tus apuntes como opinión. Después podrás retocarlo.',
      'Terminado', async () => {
        if (await edit({ op: 'finish', kind: 0, id: play.id }, `${play.name} pasa a valorados`)) {
          if (detail) detail.layer.close();
          const g = games().find((x) => Number(x.id) === Number(play.id));
          if (g) R.editGame(g);
        }
      });
  };

  // Empezar en Ronrón un juego de la biblioteca (o ponerlo en la lista)
  async function newItemFrom(game) {
    const id = Number(game.sgdbKnown) || null;
    if (!id) return null;
    const art = await A.api.ronronArt(id).catch(() => ({ cover: '', hero: '' }));
    return { id, name: game.name, cover: art.cover || '', hero: art.hero || '', logo: art.logo || '' };
  }
  R.start = async (game, status = 1) => {
    const base = await newItemFrom(game);
    if (!base) { A.app.toast('No sé qué juego es en SteamGridDB: corrígelo antes en sus opciones (Corregir juego).', 'error'); return; }
    const existing = plays().find((p) => Number(p.id) === base.id);
    const item = existing ? { ...existing, status, start: existing.start || (status === 1 ? todayDay() : 0) }
      : { ...base, status, entries: [], chapter: 0, every: 3, start: status === 1 ? todayDay() : 0, end: 0, creator: '', platform: 'PC', pages: 0, minutes: Math.round(A.play.best(game).ms / 60000), ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '', parts: [] };
    await edit({ op: 'savePlay', kind: 0, item }, status === 1 ? `Jugando a ${game.name}` : `${game.name} en tu lista`);
  };

  // ---------------------------------------------------------------- Valorar un juego nuevo
  R.newRating = (preset) => {
    if (preset && preset.sgdbKnown) {
      const ex = games().find((g) => Number(g.id) === Number(preset.sgdbKnown));
      if (ex) { R.editGame(ex); return; }
      newItemFrom(preset).then((base) => base && R.editGame({ ...base, platform: 'PC' }, { isNew: true }));
      return;
    }
    if (!A.app.state().config.sgdbKey) { A.app.toast('Para buscar juegos nuevos necesitas tu clave de SteamGridDB (Ajustes).', 'error'); return; }
    A.dialogs.open('center', (sheet, layer) => {
      const inp = A.dialogs.input(preset ? preset.name : '', { 'data-autofocus': true, placeholder: 'Nombre del juego' });
      const results = h('div.results');
      const search = async () => {
        const q = inp.value.trim();
        if (!q) return;
        results.replaceChildren(h('div.state-msg', 'Buscando…'));
        try {
          const list = await A.api.sgdbSearch(q);
          results.replaceChildren(...list.map((r) => h('button.result', {
            'data-nav': true,
            onclick: async () => {
              layer.close();
              const ex = games().find((g) => Number(g.id) === Number(r.id));
              if (ex) { A.app.toast('Ya lo tenías valorado'); R.editGame(ex); return; }
              const art = await A.api.ronronArt(r.id).catch(() => ({ cover: '', hero: '' }));
              R.editGame({ id: r.id, name: r.name, cover: art.cover, hero: art.hero, logo: art.logo || '' }, { isNew: true });
            },
          }, h('span', r.name), r.year ? h('span.y', String(r.year)) : null)));
          if (!list.length) results.append(h('div.state-msg', 'Sin resultados. Prueba con menos palabras o el nombre en inglés.'));
          else A.nav.focus(results.firstChild);
        } catch (e) { results.replaceChildren(h('div.state-msg', A.app.cleanError(e))); }
      };
      inp.addEventListener('submit-input', search);
      sheet.append(
        h('div.sheet-head', h('h2', 'Valorar un juego')),
        h('div.sheet-body', h('p.note', 'Búscalo en SteamGridDB (el mismo buscador que en el móvil, así la ficha coincide).'),
          h('div.input-row', inp, h('button.btn.primary', { 'data-nav': true, onclick: search }, 'Buscar')), results),
        h('div.sheet-foot', h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Cerrar')));
      if (preset) search();
    }, { focus: 'input' });
  };

  // ---------------------------------------------------------------- Tarjeta en la página de un juego
  R.card = (game) => {
    if (!R.signed()) return null;
    const m = entryFor(game);
    const box = h('div.rr-card');
    const head = (t) => h('div.rr-card-head', h('span.rr-dot'), h('span', t));
    if (!m) {
      box.append(head('Tu ficha'),
        h('p.rr-card-text', 'Aún no tiene ficha: ni valorado ni en curso.'),
        h('div.rr-card-actions',
          h('button.btn', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.start(game, 1) }, 'Empezar a jugarlo'),
          h('button.btn.quiet', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.newRating(game) }, 'Valorar')));
      box.classList.add('slim');
      return box;
    }
    const it = m.item;
    if (m.type === 'play') {
      const b = baremo(it);
      const lastNote = ((it.entries || []).filter((e) => e.note).slice(-1)[0] || {}).note;
      const pct = it.parts && it.parts.length ? Math.round(100 * it.parts.filter((p) => p.d).length / it.parts.length) : null;
      box.append(head(`${STATUS[it.status] || 'En curso'}`),
        h('div.rr-card-main', b ? ring(b.general) : null,
          h('div.rr-card-info',
            h('span.rr-card-sub', [it.entries && it.entries.length ? A.plural(it.entries.length, 'apunte', 'apuntes') : 'Sin apuntes', pct !== null ? `${pct} % hecho` : null, it.start ? `desde el ${fmtDay(it.start)}` : null].filter(Boolean).join(' · ')),
            lastNote ? h('p.rr-card-text', lastNote) : null)),
        h('div.rr-card-actions',
          it.status === 1
            ? h('button.btn', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.addNote(it) }, A.icon('edit'), 'Apuntar')
            : h('button.btn', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.start(game, 1) }, 'Empezar'),
          it.status === 1 ? h('button.btn.quiet', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.finish(it) }, 'Terminado') : null,
          h('button.btn.quiet', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.openPlay(it.id) }, 'Ver')));
      return box;
    }
    box.append(head('Valorado'),
      h('div.rr-card-main', ring(num(it.general)),
        h('div.rr-card-info',
          h('span.rr-card-sub', [`media ${avg4(it)}`, it.end ? `terminado el ${fmtDay(it.end)}` : null].filter(Boolean).join(' · ')),
          it.review ? h('p.rr-card-text', it.review) : null)),
      h('div.rr-card-actions',
        h('button.btn', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.openGame(it.id) }, 'Ver valoración'),
        h('button.btn.quiet', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.editGame(it) }, 'Editar'),
        h('button.btn.quiet', { 'data-nav': true, 'data-zone': 'rr-card', onclick: () => R.start(game, 1) }, 'Rejugar')));
    return box;
  };

  // ---------------------------------------------------------------- Calendario
  // Lo de Ronrón por días: cuándo terminaste o empezaste cada juego y tus apuntes
  let evCache = null;
  let evData = null;
  const keyOfEpochDay = (d) => new Date(d * 86400000).toISOString().slice(0, 10);
  R.events = () => {
    if (!R.signed() || !st.data) return new Map();
    if (evData === st.data && evCache) return evCache;
    const out = new Map();
    const add = (k, e) => { if (!out.has(k)) out.set(k, []); out.get(k).push(e); };
    for (const g of games()) {
      if (g.end > 0) add(keyOfEpochDay(g.end), { type: 'fin', kind: 'g', item: g });
      if (g.start > 0 && g.start !== g.end) add(keyOfEpochDay(g.start), { type: 'ini', kind: 'g', item: g });
    }
    for (const p of plays()) {
      if (p.start > 0) add(keyOfEpochDay(p.start), { type: 'ini', kind: 'p', item: p });
      for (const e of p.entries || []) if (e.time) add(A.play.dayKey(new Date(e.time)), { type: 'nota', kind: 'p', item: p, entry: e });
    }
    evData = st.data;
    evCache = out;
    return out;
  };
  R.eventsOn = (date) => R.events().get(A.play.dayKey(date)) || [];

  // Como el calendario de Ronrón: cada juego ocupa todos los días desde que lo empezaste hasta que lo
  // terminaste (o hasta hoy si sigue en curso). Solo videojuegos.
  let spCache = null;
  let spData = null;
  function spans() {
    if (!R.signed() || !st.data) return [];
    if (spData === st.data && spCache) return spCache;
    const t = todayDay();
    const out = [];
    const add = (item, kind) => {
      const start = num(item.start);
      const end = num(item.end);
      if (start <= 0 && end <= 0) return;
      const s0 = start > 0 ? start : end;
      const e0 = end > 0 ? Math.max(end, s0) : Math.max(t, s0);
      out.push({ item, kind, s: s0, e: e0, ended: end > 0, lib: libGameFor(item) });
    };
    for (const p of plays()) add(p, 'p');
    for (const g of games()) add(g, 'g');
    spData = st.data;
    spCache = out;
    return out;
  }
  const dayNum = (date) => Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
  R.spansOn = (date) => { const d = dayNum(date); return spans().filter((x) => d >= x.s && d <= x.e).map((x) => ({ ...x, isStart: d === x.s, isEnd: x.ended && d === x.e })); };
  R.dayNum = dayNum;
  R.fmtDay = fmtDay;
  R.coverOf = (item) => artOf(item).cover;
  R.ringOf = (v) => ring(v);
  R.scoreOf = (e) => (e.type === 'fin' ? num(e.item.general) : e.type === 'nota' ? num(e.entry.general) : null);
  R.open = (e) => (e.kind === 'g' ? R.openGame(e.item.id) : R.openPlay(e.item.id));

  // ---------------------------------------------------------------- Ajustes
  R.settings = (redraw) => {
    const s = st.status || {};
    const wrap = h('div.set-block',
      h('h2', 'Cuenta'),
      h('p.lead', 'Tu cuenta de Atrio: la pestaña Valorados y lo que estés jugando en la página de cada juego. Atrio es un dispositivo más de tu cuenta: lo que cambies aquí llega al móvil en unos segundos, y al revés.'));
    if (!s.signed) {
      wrap.append(loginForm(() => redraw()));
      return wrap;
    }
    wrap.append(
      h('div.lib-card',
        h('div.info', h('b', s.user), h('small', s.err ? s.err : `${A.plural(s.count || 0, 'ficha', 'fichas')} · sincronizado ${s.lastSync ? A.timeAgo(s.lastSync) : 'nunca'}`)),
        h('button.btn', { 'data-nav': true, onclick: async () => { try { set(await A.api.ronronSync(), 'status'); A.app.toast('Al día'); } catch (e) { A.app.toast(A.app.cleanError(e), 'error'); } redraw(); } }, A.icon('refresh'), 'Sincronizar'),
        h('button.btn.quiet', { 'data-nav': true, onclick: async () => { set(await A.api.ronronLogout(), 'status'); redraw(); } }, 'Cerrar sesión')),
      h('button.toggle', {
        'data-nav': true, 'aria-pressed': String(A.app.state().config.behavior.ronronAuto !== false),
        onclick: async () => { await A.app.patchConfig({ behavior: { ronronAuto: A.app.state().config.behavior.ronronAuto === false } }, { render: false }); redraw(); },
      }, h('span.tx', h('span', 'Lo que juegues pasa a «Jugando»'),
        h('small', 'Cuando Atrio ve que llevas 2 minutos con un juego que no tienes en curso, lo pone en Jugando desde ese día. Así sale en el calendario cada día hasta que lo marques como terminado. Los que ya tienes valorados no se tocan.')),
      h('span.knob')),
      h('p.note', 'La contraseña no se guarda: Atrio guarda un pase de sesión en tu carpeta de configuración, solo legible por tu usuario.'));
    return wrap;
  };

  A.ronron = R;
  R.load();
})();
