'use strict';
/* global A, document, window */
// La suite en Atrio: el móvil (Ronrón) conectado en tiempo real. Indicador en la barra, panel con
// dispositivos y capturas, destello al capturar y ajustes de lo que puede hacer el móvil.
(function () {
  const h = A.h;
  const S = {};
  let st = { signed: false, conn: 'desconectado', devices: [], shots: [] };
  const fmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

  const phones = () => st.devices.filter((d) => d.app === 'ronron' && d.online);
  S.state = () => st;
  S.phoneOnline = () => phones().length > 0;

  // ---------- Indicador en la barra superior ----------
  function renderBadge() {
    const btn = A.$('#btn-suite');
    if (!btn) return;
    const on = phones();
    btn.classList.toggle('live', on.length > 0);
    btn.classList.toggle('off', !st.signed);
    btn.setAttribute('aria-label', !st.signed ? 'Móvil: entra con tu cuenta de Atrio'
      : on.length ? `Móvil conectado: ${on.map((d) => d.name).join(', ')}` : `Móvil: ${st.conn}`);
    btn.title = btn.getAttribute('aria-label');
  }

  // ---------- Destello al capturar ----------
  function flash() {
    const f = h('div.shot-flash');
    document.body.append(f);
    requestAnimationFrame(() => f.classList.add('go'));
    setTimeout(() => f.remove(), 700);
  }

  // ---------- Visor de una captura ----------
  S.view = (shot, onChange) => {
    A.dialogs.open('full', (sheet, layer) => {
      sheet.classList.add('shot-view');
      sheet.append(
        h('img.shot-img', { src: shot.url, alt: shot.game || 'Captura', draggable: 'false' }),
        h('div.shot-bar',
          h('div.shot-meta', h('b', shot.game || 'Escritorio'), h('small', `${shot.w}×${shot.h} · ${fmt.format(new Date(shot.t))} · ${Math.round(shot.bytes / 1024)} KB`)),
          h('button.btn', { 'data-nav': true, 'data-autofocus': true, onclick: () => A.api.suiteShotShow(shot.id) }, A.icon('folder'), 'Ver en la carpeta'),
          h('button.btn.quiet.danger', {
            'data-nav': true,
            onclick: () => A.dialogs.confirm('Borrar la captura', 'Se borra de este PC. Si ya llegó al móvil, allí se queda.', 'Borrar', async () => {
              await A.api.suiteShotDelete(shot.id);
              layer.close();
              if (onChange) onChange();
            }),
          }, A.icon('trash'), 'Borrar'),
          h('button.btn', { 'data-nav': true, onclick: () => layer.close() }, 'Cerrar')));
    }, { focus: '[data-autofocus]' });
  };

  // Rejilla de capturas (de un juego o todas)
  S.grid = async (key, limit) => {
    const box = h('div.shot-grid');
    const fill = async () => {
      const list = (await A.api.suiteShots(key)).slice(0, limit || 60);
      box.replaceChildren(...list.map((s) => h('button.shot-thumb', {
        'data-nav': true, 'data-zone': 'shots', 'aria-label': `Captura de ${s.game || 'escritorio'}`,
        onclick: () => S.view(s, fill),
      }, h('img', { src: s.url, alt: '', loading: 'lazy', decoding: 'async' }))));
      if (!list.length) box.append(h('p.gp-empty', key ? 'Aún no hay capturas de este juego. Pídelas desde el móvil (Atrio en el móvil → Mando) o con el botón Capturar.' : 'Aún no hay capturas.'));
    };
    await fill();
    return box;
  };

  // ---------- Panel de la suite ----------
  S.open = () => {
    A.dialogs.open('side', async (sheet, layer) => {
      sheet.classList.add('suite-panel');
      const draw = async () => {
        const devs = st.devices.slice().sort((a, b) => Number(b.online) - Number(a.online));
        sheet.replaceChildren(
          h('div.side-head.suite-head', h('img.suite-logo', { src: 'icon.png', alt: '' }), h('div', h('h2', 'Atrio en el móvil'),
            h('p', !st.signed ? 'Entra con tu cuenta de Atrio para conectar el móvil' : phones().length ? `Móvil conectado: ${phones().map((d) => d.name).join(', ')}` : st.conn === 'conectado' ? 'Esperando a que abras Atrio en el móvil' : `Sin conexión con la nube (${st.conn})`))),
          !st.signed
            ? h('div.menu', h('button.menu-item', { 'data-nav': true, onclick: () => { layer.close(); A.settings.open('ronron'); } }, A.icon('heart'), h('span', 'Entrar con mi cuenta de Atrio')))
            : h('div.suite-body',
              h('h3', 'Dispositivos'),
              devs.length ? h('ul.suite-devs', devs.map((d) => h('li', { 'data-on': String(d.online) },
                h('span.dot'), h('span.n', d.name), h('small', `${d.app === 'ronron' ? 'móvil' : 'PC'}${d.online ? '' : ` · ${A.timeAgo(d.at)}`}`)))) : h('p.note', 'Abre Atrio en el móvil con la misma cuenta.'),
              h('div.suite-actions',
                h('button.btn.primary', { 'data-nav': true, 'data-autofocus': true, onclick: () => S.shoot() }, A.icon('camera'), 'Capturar ahora'),
                h('button.btn', { 'data-nav': true, onclick: () => { layer.close(); A.settings.open('suite'); } }, A.icon('sliders'), 'Ajustes del móvil')),
              h('h3', 'Últimas capturas'),
              await S.grid(null, 12),
              h('p.note', 'Desde el móvil puedes: ver en directo lo que juegas, pedir capturas a su resolución original, apuntar ideas, valorar cada partida al terminarla, lanzar juegos, mandar avisos a esta pantalla y suspender o apagar el equipo.')));
        A.nav.focusFirst('[data-autofocus]');
      };
      layer.redraw = draw;
      S._panel = layer;
      await draw();
    }, { onClose: () => { S._panel = null; } });
  };

  S.shoot = async () => {
    try {
      A.app.toast('Capturando…');
      const r = await A.api.suiteShot();
      A.app.toast(`Captura guardada (${r.w}×${r.h})${S.phoneOnline() ? ' y enviada al móvil' : ''}`);
      if (S._panel && S._panel.redraw) S._panel.redraw();
    } catch (e) { A.app.toast(A.app.cleanError(e), 'error'); }
  };

  // ---------- Ajustes ----------
  S.settings = (redraw) => {
    const c = A.app.state().config.suite || {};
    const set = (patch) => A.app.patchConfig({ suite: patch }, { render: false }).then(redraw);
    const tg = (label, key, sub) => h('button.toggle', { 'data-nav': true, 'aria-pressed': String(c[key] !== false), onclick: () => set({ [key]: c[key] === false }) },
      h('span.tx', h('span', label), sub ? h('small', sub) : null), h('span.knob'));
    const methods = h('p.note', 'Buscando herramientas de captura…');
    A.api.suiteMethods().then((m) => {
      methods.textContent = `Herramientas disponibles en este equipo, por orden: ${m.join(', ')}. ${A.WIN ? '' : 'En KDE se usa Spectacle (sin avisos); en Sway o Hyprland, grim; en GNOME, gnome-screenshot.'}`;
    }).catch(() => {});
    return h('div.set-block',
      h('h2', 'Móvil'),
      h('p.lead', 'Atrio en el PC y Atrio en el móvil comparten tu cuenta y se hablan en tiempo real: el móvil ve lo que juegas, pide capturas y valora cada partida. Aquí decides qué puede hacer el móvil en este equipo.'),
      !st.signed ? h('p.note.warn', 'Para conectar el móvil, entra con tu cuenta en Ajustes → Cuenta.') : null,
      h('h3', 'Desde el móvil'),
      tg('Hacer capturas', 'remoteShot', 'A la resolución real de la pantalla'),
      tg('Lanzar juegos', 'remoteLaunch'),
      tg('Suspender, reiniciar o apagar', 'remotePower', 'El móvil siempre pide confirmación'),
      h('h3', 'Formato de las capturas'),
      A.dialogs.seg([['png', 'PNG (calidad original)'], ['jpg', 'JPG (más ligeras)']], c.shotFormat || 'png', (v) => set({ shotFormat: v })),
      h('h3', 'Qué se captura'),
      A.dialogs.seg([['juego', 'La pantalla donde juegas'], ['todas', 'Todas las pantallas']], c.shotScope || 'juego', (v) => set({ shotScope: v })),
      tg('Enviarlas al móvil', 'shotUpload', 'Si no, solo se guardan en este PC (Imágenes/Atrio)'),
      methods);
  };

  // ---------- Eventos del proceso principal ----------
  A.api.on('suite', (s) => { st = s; renderBadge(); if (S._panel && S._panel.redraw && !S._busy) { S._busy = true; S._panel.redraw().finally(() => { S._busy = false; }); } });
  A.api.on('shot-start', () => flash());
  A.api.on('shot-taken', (s) => {
    if (s.by) A.app.toast(`📷 Captura para ${s.by} · ${s.w}×${s.h}`);
    if (A.gamepage.isOpen()) A.gamepage.refresh('play');
  });
  A.api.on('remote', (r) => {
    if (r.op === 'page' && r.key && A.app.game(r.key)) { A.dialogs.closeAll(); A.gamepage.open(r.key); }
    if (r.op === 'guide' && r.key && A.app.game(r.key)) {
      if (A.gamepage.key() !== r.key) { A.dialogs.closeAll(); A.gamepage.open(r.key); }
      setTimeout(() => A.web.open(r.key, r.url || null), 120);
    }
  });

  A.api.suite().then((s) => { st = s; renderBadge(); }).catch(() => {});
  const btn = A.$('#btn-suite');
  if (btn) btn.addEventListener('click', () => S.open());
  A.suite = S;
})();
