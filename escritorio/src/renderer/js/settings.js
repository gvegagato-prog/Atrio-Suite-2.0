'use strict';
/* global A */
(function () {
  const h = A.h;
  const settings = {};
  let layer = null;
  let section = 'apariencia';
  let body = null;
  let navBox = null;

  const SECTIONS = [
    ['apariencia', 'Apariencia', 'layout'],
    ['bibliotecas', 'Bibliotecas', 'library'],
    ['mando', 'Mando', 'gamepad'],
    ['emuladores', 'Emuladores', 'chip'],
    ['propios', 'Juegos añadidos', 'plus'],
    ['steamgriddb', 'SteamGridDB', 'image'],
    ['ocultos', 'Juegos ocultos', 'hide'],
    ['ronron', 'Cuenta', 'heart'],
    ['suite', 'Móvil', 'phone'],
    ['streaming', 'Streaming', 'cast'],
    ['comportamiento', 'Comportamiento', 'sliders'],
  ];

  const SCHEMES = [
    ['noche', 'Noche', '#0e1116', '#e7e9ec'],
    ['oled', 'OLED', '#000000', '#f2f2f2'],
    ['grafito', 'Grafito', '#1d1e20', '#c8bfae'],
    ['arena', 'Arena', '#17130f', '#d9b98a'],
    ['niebla', 'Niebla', '#e4e7eb', '#2d4a6b'],
    ['papel', 'Papel', '#f7f7f5', '#121212'],
  ];
  const ACCENTS = [
    ['auto', 'Del juego'], ['#a9b8c9', 'Acero'], ['#c8bfae', 'Piedra'], ['#d9b98a', 'Ámbar'],
    ['#9fc3b0', 'Salvia'], ['#c7a3c4', 'Malva'], ['#e8e8e8', 'Blanco'], ['#2d4a6b', 'Tinta'],
  ];

  const cfg = () => A.app.state().config;
  const theme = (patch) => A.app.patchConfig({ theme: patch }).then(() => redrawKeepFocus());
  const behavior = (patch) => A.app.patchConfig({ behavior: patch }, { render: false }).then(() => redrawKeepFocus());

  function toggle(label, value, onChange, sub) {
    return h('button.toggle', { 'data-nav': true, 'aria-pressed': String(!!value), onclick: () => onChange(!value) },
      h('span.tx', h('span', label), sub ? h('small', sub) : null), h('span.knob'));
  }

  // Redibuja la sección conservando el foco en el mismo control
  function redrawKeepFocus() {
    if (!layer) return;
    const cur = A.nav.current();
    const all = cur ? A.$$('[data-nav]', body) : [];
    const idx = all.indexOf(cur);
    draw();
    const again = A.$$('[data-nav]', body);
    if (idx >= 0 && again[idx]) A.nav.focus(again[idx], { noScroll: true });
  }

  // ---------- Secciones ----------
  function apariencia() {
    const t = cfg().theme;
    const cards = A.layouts.list.map((l) => h('button.lay-card', {
      'data-nav': true, 'aria-pressed': String(l.id === t.layout), onclick: () => theme({ layout: l.id }),
    },
    h('div.mini', l.mini.map(([cls, x, y, w, hh]) => h(`i${cls ? '.' + cls : ''}`, {
      style: { left: `${x}%`, top: `${y}%`, width: `${w}%`, height: hh ? `${hh}%` : undefined },
    }))),
    h('b', l.name), h('small', l.hint)));

    const swatch = (id, name, bg, fg) => h('button.swatch', {
      'data-nav': true, 'aria-pressed': String(id === t.scheme), onclick: () => theme({ scheme: id }),
    }, h('span.dot', { style: { background: bg, '--sw-fg': fg } }), h('small', name));
    const E = A.ESTILOS || { schemes: [], fonts: [], combos: [] };
    const schemes = SCHEMES.map(([id, name, bg, fg]) => swatch(id, name, bg, fg));
    const groups = ['Atrio', 'Editores', 'Clásicos', 'Claros'].map((g) => {
      const list = E.schemes.filter((x) => x.group === g);
      return list.length ? h('div', h('h4.sw-group', g === 'Atrio' ? 'Paletas de Atrio (las mismas del móvil)' : g === 'Editores' ? 'De editores de código' : g),
        h('div.swatches', list.map((x) => swatch(x.id, x.name, x.bg, x.accent)))) : null;
    });
    const fontOf = (f) => (f.display ? `'${f.display}'` : ({ serif: "'Newsreader'", mono: "'IBM Plex Mono'", redonda: "'Nunito'" })[f.id] || "'Instrument Sans'");
    const fonts = E.fonts.map((f) => h('button.font-card', {
      'data-nav': true, 'aria-pressed': String(f.id === t.font), onclick: () => theme({ font: f.id }),
    }, h('span.aa', { style: { fontFamily: fontOf(f) } }, 'Aa'), h('small', f.name)));
    const combos = E.combos.map((c) => {
      const sc = E.schemes.find((x) => x.id === c.scheme) || { bg: (SCHEMES.find((x) => x[0] === c.scheme) || [])[2], fg: (SCHEMES.find((x) => x[0] === c.scheme) || [])[3] };
      const f = E.fonts.find((x) => x.id === c.font) || {};
      const fo = (E.forms || []).find((x) => x.id === c.form) || {};
      const on = t.scheme === c.scheme && t.font === c.font && (t.form || 'clasico') === (c.form || 'clasico') && !t.gauge;
      return h('button.combo-card', {
        'data-nav': true, 'aria-pressed': String(on), style: { background: sc.bg, color: sc.fg },
        onclick: () => theme({ scheme: c.scheme, font: c.font, form: c.form || 'clasico', gauge: '' }),
      }, h('b', { style: { fontFamily: fontOf(f) } }, c.name), h('small', [f.name, fo.name].filter(Boolean).join(' · ')));
    });
    // Las formas se aplican al momento: al elegir una, toda la pantalla (esta incluida) se redibuja con ella
    const forms = (E.forms || []).map((fo) => h('button.form-card', {
      'data-nav': true, 'aria-pressed': String((t.form || 'clasico') === fo.id), onclick: () => theme({ form: fo.id }),
    }, h('b', fo.name), h('small', fo.desc)));
    // Notas aparte de la forma: se puede mezclar, por ejemplo, tarjetas de Píxel con estrellas
    const formGauge = (A.GAUGE_OF_FORM || {})[t.form || 'clasico'] || 'ring';
    const gname = (id) => ((E.gauges || []).find((g) => g.id === id) || {}).name || id;
    const gauges = [h('button.form-card', {
      'data-nav': true, 'aria-pressed': String(!t.gauge), onclick: () => theme({ gauge: '' }),
    }, h('b', 'Las de la forma'), h('small', `Ahora: ${gname(formGauge)}`))].concat((E.gauges || []).map((g) => h('button.form-card', {
      'data-nav': true, 'aria-pressed': String(t.gauge === g.id), onclick: () => theme({ gauge: g.id }),
    }, h('b', g.name), h('small', g.desc))));
    // Sorpréndeme: colores, letras, forma y notas al azar
    const pickOne = (l) => l[Math.floor(Math.random() * l.length)];
    const surprise = () => theme({
      scheme: pickOne(E.schemes.filter((x) => x.id !== t.scheme)).id,
      font: pickOne(E.fonts.filter((x) => x.id !== t.font)).id,
      form: pickOne((E.forms || []).filter((x) => x.id !== t.form)).id,
      gauge: Math.random() < 0.5 ? '' : pickOne(E.gauges || [{ id: '' }]).id,
    });

    const accents = ACCENTS.map(([v, name]) => h('button.swatch', {
      'data-nav': true, 'aria-pressed': String(v === t.accent), onclick: () => theme({ accent: v }),
    }, h(`span.dot${v === 'auto' ? '.accent-auto' : ''}`, { style: v === 'auto' ? {} : { background: v, '--sw-fg': v } }), h('small', name)));

    return h('div.set-block',
      h('h2', 'Apariencia'),
      h('p.lead', 'Los cambios se aplican al momento. Combina diseño, colores y tipografía hasta dar con el que te guste.'),
      h('h3', 'Diseño de la biblioteca'), h('div.lay-grid', cards),
      h('h3', 'Combinaciones'), h('p.note', 'Colores y letras que van bien juntos (los mismos estilos que en el móvil).'), h('div.combo-grid', combos),
      h('div.btn-row', h('button.btn.primary', { 'data-nav': true, onclick: surprise }, A.icon('refresh'), 'Sorpréndeme')),
      h('h3', 'Forma'), h('p.note', 'Cómo se dibuja todo: portadas, tarjetas, botones y el fondo. Cada combinación trae la suya.'), h('div.form-grid', forms),
      h('h3', 'Notas'), h('p.note', 'Cómo se ven las notas, aparte de la forma.'), h('div.form-grid', gauges),
      h('h3', 'Colores'), h('div.swatches', schemes), groups,
      h('h3', 'Color de acento'), h('div.swatches', accents),
      h('h3', 'Tipografía'), h('div.font-grid', fonts),
      h('h3', 'Tamaño de las portadas'),
      A.dialogs.seg([['compacta', 'Pequeño'], ['normal', 'Normal'], ['amplia', 'Grande']], t.density, (v) => theme({ density: v })),
      h('h3', 'Fondo'),
      A.dialogs.seg([['arte', 'Arte del juego'], ['difuso', 'Arte difuminado'], ['liso', 'Color liso']], t.backdrop, (v) => theme({ backdrop: v })),
      h('h3', 'Tamaño del fondo'),
      A.dialogs.seg([['nitido', 'Máxima nitidez'], ['llenar', 'Llenar la pantalla']], t.heroFit || 'nitido', (v) => theme({ heroFit: v })),
      h('p.note', 'Máxima nitidez muestra el fondo a lo ancho de la pantalla sin ampliarlo de más y lo funde por abajo. Llenar la pantalla lo amplía hasta cubrirla entera, que en una ultrapanorámica suele suponer ampliarlo un poco.'),
      h('h3', 'Detalles'),
      toggle('Animaciones', t.motion, (v) => theme({ motion: v })),
      toggle('Reloj', t.clock, (v) => theme({ clock: v })),
      toggle('Pistas de botones', t.labels, (v) => theme({ labels: v }), 'La fila de controles de la esquina inferior'),
      toggle('Ampliar con calidad los fondos pequeños', t.sharpen !== false, (v) => theme({ sharpen: v }),
        'Solo afecta a fondos claramente más pequeños que tu pantalla. Los de 4K se muestran tal cual, sin ningún filtro.'));
  }

  function bibliotecas() {
    const c = cfg();
    const info = A.app.state().scanInfo || {};
    const games = A.app.state().games;
    const count = (src) => games.filter((g) => g.source === src).length;
    const card = (id, name, status) => h('div.lib-card',
      h('div.info', h('span', name), h('small', status)),
      h('span.count', c.sources[id] ? A.plural(count(id), 'juego', 'juegos') : 'Desactivada'),
      h('button.toggle', {
        'data-nav': true, style: { width: 'auto' }, 'aria-pressed': String(!!c.sources[id]), 'aria-label': `Activar ${name}`,
        onclick: () => A.app.patchConfig({ sources: { [id]: !c.sources[id] } }).then(redrawKeepFocus),
      }, h('span.knob')));

    const steamStatus = info.steam && info.steam.roots && info.steam.roots.length
      ? `Leyendo ${info.steam.roots.join(', ')}` : 'No he encontrado una instalación de Steam';
    const lutrisStatus = info.lutris && info.lutris.available ? 'Leído con el comando lutris' : 'No he encontrado Lutris';

    const extraSteam = A.dialogs.pathInput('', {
      title: 'Carpeta de Steam', directory: true, placeholder: 'Carpeta de Steam adicional',
    });
    const extraList = c.extraSteamPaths.map((p) => h('div.item',
      h('div.info', h('span', p), h('small', 'Carpeta de Steam adicional')),
      h('button.btn.quiet', { 'data-nav': true, onclick: () => A.app.patchConfig({ extraSteamPaths: c.extraSteamPaths.filter((x) => x !== p) }).then(redrawKeepFocus) }, 'Quitar')));

    return h('div.set-block',
      h('h2', 'Bibliotecas'),
      h('p.lead', 'Atrio solo lee tus bibliotecas: los juegos se siguen instalando y actualizando desde cada tienda.'),
      card('steam', 'Steam', steamStatus),
      card('heroic', 'Heroic (Epic, GOG y Amazon)', 'Juegos instalados con Heroic'),
      A.WIN ? null : card('lutris', 'Lutris', lutrisStatus),
      card('emulators', 'Emuladores', c.emulators.length ? A.plural(c.emulators.length, 'sistema configurado', 'sistemas configurados') : 'Ningún sistema configurado'),
      card('custom', 'Juegos añadidos por ti', c.customGames.length ? A.plural(c.customGames.length, 'juego', 'juegos') : 'Ninguno todavía'),
      h('h3', 'Otra instalación de Steam'),
      h('p.note', A.WIN ? 'Normalmente no hace falta: Atrio encuentra Steam y todas sus carpetas de biblioteca.' : 'Normalmente no hace falta: Atrio encuentra Steam nativo y flatpak, y todas sus carpetas de biblioteca.'),
      h('div.input-row', extraSteam.el, h('button.btn', {
        'data-nav': true,
        onclick: () => {
          const v = extraSteam.input.value.trim();
          if (v) A.app.patchConfig({ extraSteamPaths: [...c.extraSteamPaths, v] }).then(redrawKeepFocus);
        },
      }, 'Añadir')),
      h('div.item-list', extraList),
      h('button.btn.primary', { 'data-nav': true, onclick: () => A.app.rescan(true).then(redrawKeepFocus) }, A.icon('refresh'), 'Volver a buscar juegos'));
  }

  function emuladores() {
    const c = cfg();
    const games = A.app.state().games;
    return h('div.set-block',
      h('h2', 'Emuladores'),
      h('p.lead', 'Añade un sistema por cada consola: la carpeta donde están sus juegos y el comando del emulador que los abre.'),
      h('div.item-list', c.emulators.length ? c.emulators.map((e) => h('button.item', {
        'data-nav': true, onclick: () => A.dialogs.editEmulator(e.id),
      }, h('div.info', h('span', e.name), h('small', `${A.plural(games.filter((g) => g.key.startsWith(`rom:${e.id}:`)).length, 'juego', 'juegos')} en ${e.romDir}`)), A.icon('edit')))
        : h('p.note', 'Todavía no hay sistemas.')),
      h('button.btn.primary', { 'data-nav': true, onclick: () => A.dialogs.editEmulator(null) }, A.icon('plus'), 'Añadir sistema'));
  }

  function propios() {
    const c = cfg();
    const runner = A.WIN ? { native: 'Ejecutable', umu: 'Ejecutable', wine: 'Ejecutable', command: 'Comando propio' }
      : { native: 'Nativo de Linux', umu: 'Windows con Proton', wine: 'Windows con Wine', command: 'Comando propio' };
    return h('div.set-block',
      h('h2', 'Juegos añadidos'),
      h('p.lead', A.WIN ? 'Cualquier juego que no venga de una tienda: su .exe o un comando propio.' : 'Cualquier juego que no venga de una tienda: ejecutables de Linux, AppImage, scripts o juegos de Windows con Proton.'),
      h('div.item-list', c.customGames.length ? c.customGames.map((g) => h('button.item', {
        'data-nav': true, onclick: () => A.dialogs.editCustom(g.id),
      }, h('div.info', h('span', g.name), h('small', `${runner[g.runner] || ''}: ${g.runner === 'command' ? g.command : g.exec}`)), A.icon('edit')))
        : h('p.note', 'Todavía no has añadido ningún juego.')),
      h('button.btn.primary', { 'data-nav': true, onclick: () => A.dialogs.editCustom(null) }, A.icon('plus'), 'Añadir juego'));
  }

  function steamgriddb() {
    const c = cfg();
    const inp = A.dialogs.input(c.sgdbKey, { placeholder: 'Pega aquí tu clave', 'aria-label': 'Clave de la API de SteamGridDB' });
    inp.classList.add('mono');
    const msg = h('p.note');
    const save = async () => {
      const key = inp.value.trim();
      if (!key) { await A.app.patchConfig({ sgdbKey: '' }, { render: false }); msg.textContent = 'Clave borrada.'; return; }
      msg.textContent = 'Comprobando…';
      try {
        await A.api.sgdbTest(key);
        await A.app.patchConfig({ sgdbKey: key }, { render: false });
        msg.textContent = 'Clave correcta. Descargando portadas, logos y fondos que falten.';
      } catch (e) { msg.textContent = A.app.cleanError(e); }
    };
    inp.addEventListener('submit-input', save);
    return h('div.set-block',
      h('h2', 'SteamGridDB'),
      h('p.lead', 'Con una clave gratuita, Atrio descarga portadas, fondos y logos para todos tus juegos, incluidas ROMs y juegos añadidos a mano.'),
      h('p.note', 'Crea una cuenta en steamgriddb.com y genera la clave en Preferencias, apartado API. ',
        h('span.link', { onclick: () => A.api.openExternal('https://www.steamgriddb.com/profile/preferences/api') }, 'Abrir la página de la clave')),
      A.dialogs.field('Clave de la API', h('div.input-row', inp, h('button.btn.primary', { 'data-nav': true, onclick: save }, 'Comprobar y guardar'))),
      msg,
      h('h3', 'Arte'),
      h('p.note', 'Sin clave, Atrio usa el arte que Steam ya tiene en tu disco y, si puede, el de la tienda de Steam.'),
      h('button.btn', { 'data-nav': true, onclick: () => { A.api.refetchArt(null); A.app.toast('Buscando el arte que falta…'); } }, A.icon('refresh'), 'Buscar el arte que falta'));
  }

  // ---------- Mando ----------
  const LABELS = {
    up: 'Arriba', down: 'Abajo', left: 'Izquierda', right: 'Derecha', a: 'A', b: 'B', x: 'X', y: 'Y',
    prev: 'LB', next: 'RB', start: 'Menú', select: 'Vista',
  };
  let padTimer = null;
  function padName(id) {
    return String(id).replace(/\s*\((STANDARD GAMEPAD\s*)?Vendor: ([0-9a-f]{4}) Product: ([0-9a-f]{4})\)/i, '').trim() || 'Mando';
  }
  function mando() {
    const b = cfg().behavior;
    const list = h('div.item-list');
    const live = h('div.pad-live', A.nav.ACTIONS.map((a) => h('span.pad-key', { 'data-act': a }, LABELS[a])));
    const lastBox = h('p.note.pad-last', 'Pulsa algo en el mando.');
    const steamStatus = h('p.note.steam-status', 'Comprobando si Atrio está en Steam…');
    A.api.autostart().then((st) => {
      steamStatus.textContent = st.steam
        ? `Atrio está añadido a Steam${st.viaSteam ? ' y se abrirá a través de Steam al iniciar sesión' : ''}.`
        : 'Atrio todavía no está añadido a Steam.';
      steamStatus.classList.toggle('ok', !!st.steam);
    }).catch(() => { steamStatus.textContent = ''; });
    const drawPads = () => {
      const pads = A.nav.pads();
      const maps = b.padMaps || {};
      const sig = pads.map((p) => p.id + p.mapping + (maps[p.id] ? 1 : 0)).join('|');
      if (list.dataset.sig === sig) return;
      list.dataset.sig = sig;
      list.replaceChildren(...(pads.length ? pads.map((p) => h('div.lib-card',
        h('div.info', h('span', padName(p.id)),
          h('small', maps[p.id] ? 'Botones configurados a mano' : p.mapping === 'standard' ? 'Reconocido como mando estándar' : 'Sin mapeo estándar: conviene configurar los botones')),
        maps[p.id] ? h('button.btn.quiet', { 'data-nav': true, onclick: () => saveMap(p.id, null) }, 'Quitar configuración') : null,
        h('button.btn', { 'data-nav': true, onclick: () => calibrate(p.id) }, 'Configurar botones')))
        : [h('p.note', 'Ahora mismo no llega ningún mando como tal. Pulsa cualquier botón para que aparezca.')]));
    };
    // Prueba en vivo: qué acción entiende Atrio en cada momento
    clearInterval(padTimer);
    padTimer = setInterval(() => {
      if (!document.body.contains(list)) { clearInterval(padTimer); return; }
      drawPads();
      const now = new Set();
      for (const p of A.nav.pads()) A.nav.readActions(p, now);
      for (const k of live.children) k.classList.toggle('on', now.has(k.dataset.act));
      const li = A.nav.lastInput();
      if (li) {
        const secs = Math.round((Date.now() - li.t) / 1000);
        const kind = { tecla: 'una tecla', 'ratón': 'el ratón', rueda: 'la rueda', mando: 'el mando como mando' }[li.type] || li.type;
        lastBox.textContent = `Lo último que ha llegado a Atrio: ${kind} (${li.detail}), ${secs < 2 ? 'ahora mismo' : `hace ${secs} s`}.`;
      }
    }, 80);
    drawPads();
    return h('div.set-block',
      h('h2', 'Mando'),
      h('p.lead', 'Atrio funciona con mando, con teclado y con ratón a la vez. Pulsa botones para comprobar qué entiende.'),
      h('h3', 'Mandos detectados'), list,
      h('h3', 'Prueba'), live, lastBox,
      h('h3', 'Steam Controller con Steam abierto'),
      h('p.note', 'Con Steam abierto, Steam se queda el mando. Fuera de los juegos lo usa en modo escritorio, como ratón, y por eso a Atrio solo le llega el ratón. Hay dos formas de que llegue como mando:'),
      h('ol.steps',
        h('li', h('b', 'Al momento: '), 'mantén pulsado un instante el botón de menú (☰). Steam muestra un aviso y cambia entre modo escritorio y modo mando. En modo mando, Atrio recibe el mando completo y la prueba de arriba se ilumina.'),
        h('li', h('b', 'Para siempre: '), 'añade Atrio a Steam (en Steam: Añadir un juego, Añadir un juego que no es de Steam, y elige Atrio en la lista). En las propiedades de Atrio dentro de Steam, en Mando, activa Steam Input. A partir de ahí, al iniciar sesión Atrio se abrirá a través de Steam con el mando funcionando.')),
      steamStatus,
      h('p.note', 'Otra opción es cambiar la configuración de escritorio del mando en Steam (Ajustes, Mando, Configuración de escritorio) y asignar teclas: A a Intro, B a Escape, cruceta a flechas, Y a Espacio, X a /, LB a RePág, RB a AvPág, Vista a C y Menú a S.'),
      h('h3', 'Opciones'),
      toggle('Intercambiar A y B', b.swapAB, (v) => behavior({ swapAB: v }), 'Para mandos con distribución de Nintendo'),
      toggle('Volver a Atrio al cerrar el juego', b.returnOnExit !== false, (v) => behavior({ returnOnExit: v }), 'Atrio reaparece en cuanto detecta que el juego ha terminado'),
      A.WIN ? null : toggle('Esconder Steam al abrir un juego', b.hideSteamLaunch !== false, (v) => behavior({ hideSteamLaunch: v }),
        'Solo en KDE Plasma. La ventana «Preparando para iniciar» de Steam no sale por encima. Si Steam necesita algo (una actualización, un aviso), vuelve a aparecer a los 90 segundos'));
  }

  async function saveMap(id, map) {
    const maps = { ...(cfg().behavior.padMaps || {}) };
    if (map) maps[id] = JSON.stringify(map); else maps[id] = '';
    // Los mapas vacíos se limpian al leer; así se pueden "borrar" con una mezcla parcial
    await A.app.patchConfig({ behavior: { padMaps: maps } }, { render: false });
    A.nav.setPadMaps(cleanMaps(cfg().behavior.padMaps));
    redrawKeepFocus();
  }
  function cleanMaps(maps) {
    const out = {};
    for (const [k, v] of Object.entries(maps || {})) if (v) out[k] = v;
    return out;
  }
  settings.cleanMaps = cleanMaps;

  // Asistente: pide cada botón y guarda lo que se pulse (botón o eje)
  function calibrate(id) {
    const steps = A.nav.ACTIONS;
    const map = {};
    let i = 0;
    let base = null;
    let waitRelease = false;
    A.dialogs.open('center', (sheet, layer) => {
      const prompt = h('div.cal-prompt');
      const progress = h('div.cal-progress');
      const finish = async () => {
        A.nav.release();
        layer.close();
        await saveMap(id, map);
        A.app.toast('Botones guardados');
      };
      const draw = () => {
        if (i >= steps.length) { finish(); return; }
        prompt.replaceChildren(h('span.cal-key', LABELS[steps[i]]), h('span.cal-text', describe(steps[i])));
        progress.textContent = `${i + 1} de ${steps.length}`;
      };
      const next = () => { i++; base = null; waitRelease = true; draw(); };
      layer.onClose = () => A.nav.release();
      A.nav.capture((pads) => {
        const p = pads.find((x) => x.id === id);
        if (!p) return;
        const btns = p.buttons.map((b) => (b.pressed || b.value > 0.5));
        const axes = [...p.axes];
        if (waitRelease) {
          if (btns.some(Boolean) || (base && axes.some((v, k) => Math.abs(v - base.axes[k]) > 0.35))) return;
          waitRelease = false;
        }
        if (!base) { base = { btns, axes }; return; }
        const bi = btns.findIndex((v, k) => v && !base.btns[k]);
        if (bi >= 0) { map[steps[i]] = { t: 'b', i: bi }; next(); return; }
        const ai = axes.findIndex((v, k) => Math.abs(v - base.axes[k]) > 0.6);
        if (ai >= 0) { map[steps[i]] = { t: 'a', i: ai, d: axes[ai] > base.axes[ai] ? 1 : -1 }; next(); }
      });
      sheet.append(
        h('div.sheet-head', h('h2', 'Configurar botones'), progress),
        h('div.sheet-body', h('p.note', `Mando: ${padName(id)}. Pulsa en el mando lo que se te pide.`), prompt),
        h('div.sheet-foot',
          h('button.btn.quiet', { 'data-nav': true, onclick: () => { layer.close(); } }, 'Cancelar'),
          h('span', { style: { flex: 1 } }),
          h('button.btn', { 'data-nav': true, onclick: () => next() }, 'Saltar este')));
      draw();
    }, { narrow: true });
  }
  function describe(act) {
    return {
      up: 'Arriba en la cruceta', down: 'Abajo en la cruceta', left: 'Izquierda en la cruceta', right: 'Derecha en la cruceta',
      a: 'El botón para aceptar y jugar', b: 'El botón para volver', x: 'El botón para buscar', y: 'El botón de opciones',
      prev: 'El gatillo superior izquierdo', next: 'El gatillo superior derecho', start: 'El botón de menú (abre Ajustes)', select: 'El botón de vista (abre la búsqueda)',
    }[act];
  }

  // Arranque automático: solo con el AppImage (copia instalada en una carpeta fija)
  function autostartBlock() {
    const box = h('div', h('p.note', 'Comprobando…'));
    A.api.autostart().then((st) => {
      if (!st.available) {
        box.replaceChildren(h('p.note', A.WIN ? 'Para abrir Atrio al iniciar sesión, instálalo con el instalador.' : 'Para abrir Atrio al iniciar sesión, usa la versión AppImage.'));
        return;
      }
      box.replaceChildren(
        A.dialogs.seg([['visible', 'Abrir a pantalla completa'], ['hidden', 'Abrir en segundo plano'], ['off', 'No abrir solo']], st.mode, async (v) => {
          try { await A.api.setAutostart(v); A.app.toast(v === 'off' ? 'Atrio ya no se abrirá al iniciar sesión' : 'Hecho: Atrio se abrirá al iniciar sesión'); } catch (e) { A.app.toast(A.app.cleanError(e), 'error'); }
        }),
        h('p.note', 'Al iniciar sesión, Atrio se abre solo y cuenta también lo que juegues desde Steam. En segundo plano no se ve hasta que lo abras desde el menú; en ese modo, cerrar la ventana solo la oculta.'),
        st.windows ? h('p.note', `Atrio está instalado en ${st.path}. Para actualizarlo, abre el instalador de la versión nueva.`)
          : st.installed ? h('p.note', `Atrio está instalado en ${st.path} y aparece en tu menú de aplicaciones. Cuando construyas una versión nueva, ábrela una vez y sustituirá a esta.`) : null);
    });
    return box;
  }

  function ocultos() {
    const list = A.app.state().games.filter((g) => g.hidden).sort((a, b) => a.sortName.localeCompare(b.sortName, 'es'));
    return h('div.set-block',
      h('h2', 'Juegos ocultos'),
      h('p.lead', 'No aparecen en la biblioteca ni en la búsqueda. Desde aquí puedes abrirlos o devolverlos a la biblioteca.'),
      list.length ? h('div.item-list', list.map((g) => {
        const cover = g.art.cover || g.art.wide;
        return h('div.item.hidden-item',
          h('span.hcv', { style: cover ? { backgroundImage: `url("${cover}")` } : { '--ph-h': String(A.layouts.hueOf(g.name)) } }),
          h('div.info', h('span', g.name), h('small', g.source === 'emulators' && g.platform ? g.platform : g.sourceLabel)),
          h('button.btn.primary', { 'data-nav': true, onclick: () => A.gamepage.open(g.key) }, A.icon('play'), 'Abrir'),
          h('button.btn', {
            'data-nav': true,
            onclick: async () => {
              const ng = await A.api.override(g.key, { hidden: false });
              A.app.updateGame(ng, true);
              A.app.toast(`${g.name} vuelve a la biblioteca`);
              redrawKeepFocus();
            },
          }, A.icon('show'), 'Mostrar'));
      })) : h('p.note', 'No tienes juegos ocultos. Para ocultar uno, abre sus opciones y elige Ocultar de la biblioteca.'));
  }

  function comportamiento() {
    const b = cfg().behavior;
    return h('div.set-block',
      h('h2', 'Comportamiento'),
      h('h3', 'Al pulsar un juego'),
      A.dialogs.seg([['page', 'Abrir su página'], ['play', 'Jugar directamente']], b.onSelect || 'page', (v) => behavior({ onSelect: v }).then(() => A.app.renderHints())),
      h('h3', 'Al abrir un juego'),
      A.dialogs.seg([['minimize', 'Minimizar Atrio'], ['none', 'Dejarlo abierto'], ['quit', 'Cerrar Atrio']], b.onLaunch, (v) => behavior({ onLaunch: v })),
      h('h3', 'Orden de la biblioteca'),
      A.dialogs.seg([['recent', 'Jugados recientemente'], ['alpha', 'Alfabético'], ['source', 'Por tienda']], b.sort, (v) => A.app.patchConfig({ behavior: { sort: v } }).then(redrawKeepFocus)),
      h('h3', 'Al iniciar sesión'),
      autostartBlock(),
      h('h3', 'Pantalla'),
      toggle('Pantalla completa', b.fullscreen, (v) => behavior({ fullscreen: v }), 'También con F11'),
      h('p.kbd-note', 'Teclado: flechas para moverte, Intro para jugar, Espacio o M para opciones, / para buscar, Tab para cambiar de filtro y Esc para volver. La rueda del ratón también recorre la biblioteca.'));
  }

  const ronron = () => A.ronron.settings(() => redrawKeepFocus());
  const suite = () => A.suite.settings(() => redrawKeepFocus());
  const streaming = () => A.stream.settings(() => redrawKeepFocus());
  const BUILDERS = { apariencia, bibliotecas, mando, emuladores, propios, steamgriddb, ocultos, ronron, suite, streaming, comportamiento };

  function draw() {
    for (const b of navBox.querySelectorAll('[data-sec]')) b.setAttribute('aria-current', String(b.dataset.sec === section));
    body.replaceChildren(BUILDERS[section]());
  }

  settings.open = (sec, opts = {}) => {
    if (sec && BUILDERS[sec]) section = sec;
    if (layer) { draw(); return; }
    layer = A.dialogs.open('full', (sheet, l) => {
      navBox = h('nav.set-nav',
        h('h1', 'Ajustes'),
        SECTIONS.map(([id, label, icon]) => h('button.menu-item', {
          'data-nav': true, 'data-zone': 'setnav', 'data-sec': id,
          onclick: () => { section = id; draw(); A.nav.focusFirst(); A.nav.focus(navBox.querySelector(`[data-sec="${id}"]`), { noScroll: true }); },
        }, A.icon(icon), h('span', label))),
        h('div.spacer'),
        h('button.menu-item', { 'data-nav': true, 'data-zone': 'setnav', onclick: () => A.api.toggleFullscreen() }, A.icon('expand'), h('span', 'Pantalla completa')),
        h('button.menu-item', { 'data-nav': true, 'data-zone': 'setnav', onclick: () => A.api.minimize() }, A.icon('minimize'), h('span', 'Minimizar')),
        h('button.menu-item', { 'data-nav': true, 'data-zone': 'setnav', onclick: () => A.dialogs.power() }, A.icon('power'), h('span', 'Apagar, reiniciar o suspender')),
        h('button.menu-item', { 'data-nav': true, 'data-zone': 'setnav', onclick: () => A.api.quit() }, A.icon('hide'), h('span', 'Salir de Atrio')));
      body = h('div.set-body.scroller');
      sheet.append(navBox, body);
      l.tabs = (step) => {
        const i = SECTIONS.findIndex(([id]) => id === section);
        section = SECTIONS[(i + step + SECTIONS.length) % SECTIONS.length][0];
        draw();
        A.nav.focus(navBox.querySelector(`[data-sec="${section}"]`), { noScroll: true });
      };
      draw();
    }, { onClose: () => { layer = null; }, focus: `[data-sec="${section}"]` });
    if (opts.add && section === 'propios') A.dialogs.editCustom(null);
  };
  settings.isOpen = () => !!layer;
  settings.refresh = () => { if (layer) redrawKeepFocus(); };

  A.settings = settings;
})();
