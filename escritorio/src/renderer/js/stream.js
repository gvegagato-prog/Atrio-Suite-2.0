'use strict';
/* global A */
// Ajustes → Streaming: Steam de este PC para jugar desde el móvil con Steam Link (la app de Valve).
(function () {
  const h = A.h;
  const S = {};
  let info = null;

  function status(i) {
    if (!i) return h('p.note', 'Mirando Steam…');
    const dot = (on) => h('span.st-dot', { 'data-on': String(!!on) });
    return h('div.st-status',
      h('div.st-row', dot(i.installed), h('span', i.installed ? 'Steam instalado' : 'Steam no está instalado en este PC')),
      h('div.st-row', dot(i.running), h('span', i.running ? `Steam abierto en «${i.name}»` : 'Steam cerrado (Atrio lo abre al jugar desde el móvil)')),
      i.ips && i.ips.length ? h('div.st-row', dot(true), h('span', `En casa: ${i.ips.join(', ')}`)) : null);
  }

  S.settings = (redraw) => {
    const c = A.app.state().config.stream || {};
    const set = (patch) => A.app.patchConfig({ stream: patch }, { render: false }).then(() => { info = null; redraw(); });
    const box = h('div', status(info));
    if (!info) A.api.streamInfo().then((i) => { info = i; box.replaceChildren(status(i)); }).catch(() => {});

    return h('div.set-block',
      h('h2', 'Streaming'),
      h('p.lead', 'Juega desde el móvil a los juegos de este PC con Steam Link, la app gratuita de Valve. En el móvil, cada juego tiene «Streaming»: Atrio abre Steam y el juego en este PC, y el móvil abre Steam Link.'),
      box,
      h('div.btn-row',
        h('button.btn.primary', { 'data-nav': true, onclick: async () => { try { A.app.toast('Abriendo Steam…'); info = await A.api.streamStart(); box.replaceChildren(status(info)); A.app.toast('Steam abierto'); } catch (e) { A.app.toast(A.app.cleanError(e), 'error'); } } }, A.icon('play'), 'Abrir Steam'),
        h('button.btn.quiet', { 'data-nav': true, onclick: () => { info = null; A.api.streamInfo().then((i) => { info = i; box.replaceChildren(status(i)); }); } }, A.icon('refresh'), 'Volver a mirar')),

      h('h3', 'En el móvil'),
      h('p.note', '1 · Instala Steam Link (está en Google Play: la app gratuita de Valve). 2 · Ábrelo y elige este PC: te enseña un PIN. Escríbelo en Steam de este PC, en la ventana que sale (o en Ajustes → Remote Play → Emparejar Steam Link); solo la primera vez. 3 · «Streaming» en cualquier juego. Fuera de casa, Steam Link también puede conectarse por internet.'),

      h('button.toggle', { 'data-nav': true, 'aria-pressed': String(c.remote !== false), onclick: () => set({ remote: c.remote === false }) },
        h('span.tx', h('span', 'El móvil puede preparar el streaming'), h('small', 'Abrir Steam y lanzar el juego')), h('span.knob')));
  };

  A.api.on('stream', (i) => { info = i; });
  A.stream = S;
})();
