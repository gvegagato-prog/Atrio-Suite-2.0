'use strict';
/* global window, document, ResizeObserver */
// Guías y webs de un juego (wikis, builds…), a pantalla completa y con la estética de la revista.
// La web la pinta el proceso principal (una vista de Chromium aparte) justo encima del hueco que deja esta
// capa; arriba quedan la cabecera de la guía y las pestañas guardadas del juego.
// Mando: B cierra (o vuelve al inicio de guías), LB / RB atrás y adelante, Y guarda o quita la web.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const api = RV.api;

  // Webs comprobadas para juegos online conocidos (las mismas que en el lanzador clásico)
  const PRESETS = [
    [/deadlock/, [['Deadlock Wiki · héroes', 'https://deadlock.wiki/Heroes'], ['Statlocker', 'https://statlocker.gg'],
      ['Mobalytics · builds', 'https://mobalytics.gg/deadlock/build-stats'], ['Deadlock Labs', 'https://deadlocklabs.gg']]],
    [/league ?of ?legends|^lol$/, [['OP.GG · campeones', 'https://op.gg/champions'], ['U.GG · campeones', 'https://u.gg/lol/champions'],
      ['Mobalytics · campeones', 'https://mobalytics.gg/lol/champions']]],
    [/^dota ?2$/, [['Dotabuff · héroes', 'https://www.dotabuff.com/heroes']]],
  ];
  const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  const presets = (name) => { const n = norm(name); const p = PRESETS.find(([re]) => re.test(n)); return p ? p[1] : []; };
  const buscarUrl = (q) => `https://duckduckgo.com/?q=${encodeURIComponent(q)}`;
  const host = (u) => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch { return u; } };

  let abierta = null; // { key, capa, hueco, url, nombre, st, ro, cerrar }

  const juego = () => (abierta ? RV.juegoPc(abierta.key) : null);
  const enlaces = () => ((juego() && juego().links) || []);
  function limites() {
    if (!abierta || !abierta.hueco) return null;
    const r = abierta.hueco.getBoundingClientRect();
    return { x: r.left, y: r.top, width: r.width, height: r.height };
  }
  async function guardarEnlaces(list) {
    const ng = await api.override(abierta.key, { links: list.length ? list : null });
    if (ng) D.setJuegoPc(ng);
    pintar();
  }
  async function ir(url, nombre) {
    if (!abierta) return;
    if (!/^https?:\/\//i.test(url)) url = `https://${url}`;
    abierta.url = url;
    abierta.nombre = nombre || (enlaces().find((l) => l.u === url) || {}).n || '';
    pintar();
    try { abierta.st = await api.webOpen(url, limites()); if (abierta && abierta.capa.querySelector('.g-hueco .vacio')) abierta.capa.querySelector('.g-hueco .vacio').textContent = abierta.st && abierta.st.loading ? 'Cargando…' : ''; } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); abierta.url = null; pintar(); }
  }
  function inicio() {
    if (!abierta) return;
    abierta.url = null;
    api.webHide();
    pintar();
  }
  function guardarActual() {
    if (!abierta || !abierta.url) return;
    const st = abierta.st || {};
    const u = st.url || abierta.url;
    const l = enlaces();
    if (l.some((x) => x.u === u || x.u === abierta.url)) {
      guardarEnlaces(l.filter((x) => x.u !== u && x.u !== abierta.url));
      RV.aviso('Quitada de las guías de este juego');
    } else {
      guardarEnlaces([...l, { u, n: (abierta.nombre || st.title || host(u)).slice(0, 40) }]);
      RV.aviso('Guardada en las guías de este juego');
    }
  }

  function pintar() {
    if (!abierta) return;
    const g = juego();
    const st = abierta.st || {};
    const l = enlaces();
    const guardada = abierta.url && l.some((x) => x.u === st.url || x.u === abierta.url);
    const pest = h('div.g-pestanas', l.map((x) => h('button.g-pestana', {
      type: 'button', 'aria-pressed': String(!!abierta.url && (st.url === x.u || abierta.url === x.u)), title: x.u, onclick: () => ir(x.u, x.n),
    }, x.n || host(x.u))));
    const barra = h('div.g-barra',
      h('div.g-cab',
        h('span.ante', 'Guías y webs'),
        h('span.g-juego', g ? g.name : ''),
        h('span.hueco'),
        abierta.url ? h('button.enlace', { type: 'button', onclick: inicio }, 'Inicio de guías') : null,
        abierta.url ? h('button.enlace.apagado', { type: 'button', disabled: st.back ? null : true, onclick: () => api.webNav('back') }, '← Atrás') : null,
        abierta.url ? h('button.enlace.apagado', { type: 'button', disabled: st.fwd ? null : true, onclick: () => api.webNav('fwd') }, 'Adelante →') : null,
        abierta.url ? h('button.enlace.apagado', { type: 'button', onclick: () => api.webNav('reload') }, 'Recargar') : null,
        abierta.url ? h('button.enlace.acento', { type: 'button', onclick: guardarActual }, guardada ? '★ Guardada' : '☆ Guardar') : null,
        h('button.enlace', { type: 'button', onclick: () => abierta.cerrar() }, 'Cerrar ✕')),
      l.length ? pest : null,
      h('div.g-dir.versal', abierta.url ? (st.title ? `${st.title} · ${host(st.url || abierta.url)}` : host(abierta.url)) : ''),
      h('div.filete'));
    let cuerpo;
    if (abierta.url) {
      cuerpo = h('div.g-hueco', h('p.vacio', st.loading !== false ? 'Cargando…' : ''));
    } else {
      const sug = presets(g ? g.name : '').filter(([, u]) => !l.some((x) => x.u === u));
      const q = h('input', { type: 'search', placeholder: 'Pega una dirección o busca: «Haze build», «Ahri runas»…', 'data-autofocus': true, spellcheck: 'false', 'aria-label': 'Dirección o búsqueda' });
      const enviar = () => {
        const v = q.value.trim();
        if (!v) return;
        if (/^https?:\/\//i.test(v) || /^[\w-]+(\.[\w-]+)+(\/\S*)?$/.test(v)) ir(v);
        else ir(buscarUrl(`${g ? g.name : ''} ${v}`.trim()));
      };
      q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); enviar(); } });
      const tarjeta = (n, u, mia) => h('button.g-tarjeta', { type: 'button', onclick: () => ir(u, n) }, h('span.tit', n), h('span.versal', mia ? host(u) : `${host(u)} · sugerida`));
      cuerpo = h('div.g-inicio.hoja',
        h('label.buscador', { style: { maxWidth: 'none' } }, h('span.lupa', 'Ir'), q),
        h('div', { style: { display: 'flex', gap: '22px', flexWrap: 'wrap', margin: '14px 0 6px' } },
          h('button.boton-tinta', { type: 'button', onclick: enviar }, 'Ir →'),
          h('button.enlace', { type: 'button', onclick: () => ir(buscarUrl(`${g ? g.name : ''} guía`)) }, `Buscar guías de ${g ? g.name : 'este juego'}`)),
        l.length ? h('section', { style: { marginTop: '24px' } }, RV.seccion('Guardadas'), h('div.g-tarjetas', l.map((x) => tarjeta(x.n || host(x.u), x.u, true)))) : null,
        sug.length ? h('section', { style: { marginTop: '24px' } }, RV.seccion('Sugerencias'), h('div.g-tarjetas', sug.map(([n, u]) => tarjeta(n, u, false)))) : null,
        h('p.versal', { style: { marginTop: '24px' } }, 'La web se abre aquí mismo. «Guardar» la deja en las pestañas de este juego (también la ves en el móvil, y desde el móvil puedes mandar webs con Compartir → Atrio). B o Esc para cerrar.'));
    }
    abierta.capa.replaceChildren(barra, cuerpo);
    const antes = abierta.hueco;
    abierta.hueco = abierta.url ? cuerpo : null;
    if (abierta.ro) abierta.ro.disconnect();
    if (abierta.hueco) {
      abierta.ro = new ResizeObserver(() => { if (abierta && !abierta.tapada) api.webBounds(limites()); });
      abierta.ro.observe(abierta.hueco);
      if (!antes && st.open && !abierta.tapada) api.webShow(limites());
    } else {
      const f = abierta.capa.querySelector('[data-autofocus]');
      if (f) f.focus({ preventScroll: true });
    }
  }

  RV.guia = (key, url) => {
    if (!RV.juegoPc(key)) return;
    if (abierta && abierta.key === key) { if (url) ir(url); return; }
    if (abierta) abierta.cerrar();
    const capa = h('div.g-capa', { role: 'dialog', 'aria-label': 'Guías' });
    abierta = { key, capa, hueco: null, url: null, nombre: '', st: {}, ro: null, tapada: false };
    abierta.cerrar = RV.superponer(capa, () => {
      if (abierta && abierta.ro) abierta.ro.disconnect();
      abierta = null;
      api.webClose();
    });
    pintar();
    const g = RV.juegoPc(key);
    if (url) ir(url);
    else if (g && (g.links || []).length === 1) ir(g.links[0].u, g.links[0].n);
  };
  RV.guiaAbierta = () => !!abierta;

  // Un diálogo encima de la web (el menú, por ejemplo): la web se esconde mientras tanto
  const dialogo = RV.dialogo;
  RV.dialogo = (o) => {
    if (!abierta || !abierta.url) return dialogo(o);
    abierta.tapada = true;
    api.webHide();
    const alCerrar = o.alCerrar;
    return dialogo({ ...o, alCerrar: () => { if (alCerrar) alCerrar(); if (abierta) { abierta.tapada = false; if (abierta.url) api.webShow(limites()); } } });
  };

  api.on('web', (s) => { if (abierta) { abierta.st = s || {}; if (s && s.open === false && abierta.url) { abierta.url = null; } pintarBarra(); } });
  api.on('web-error', (e) => { if (abierta && e && e.desc) RV.aviso(`No se ha podido abrir: ${e.desc}`, 'error'); });
  // Esc con la web activa (la tecla la recibe la web): como B
  api.on('web-key', (k) => { if (k === 'escape' && abierta) volver(); if (k === 'f11') api.toggleFullscreen(); });
  // Solo la barra (sin rehacer el hueco de la web, que parpadearía)
  const pintarBarra = RV.debounce(() => { if (abierta) pintar(); }, 120);
  function volver() {
    if (!abierta) return false;
    if (RV.hayDialogo() && abierta.tapada) return false;
    if (abierta.url && enlaces().length !== 1) inicio(); else abierta.cerrar();
    return true;
  }
  RV.mando.al({
    b: () => (abierta && !abierta.tapada ? volver() : false),
  });
  // LB / RB con la guía abierta: atrás y adelante en la web; Y: guardar o quitar la web (los llama app.js)
  RV.guiaNav = (paso) => {
    if (!abierta || !abierta.url || abierta.tapada) return false;
    api.webNav(paso < 0 ? 'back' : 'fwd');
    return true;
  };
  RV.guiaY = () => {
    if (!abierta || !abierta.url || abierta.tapada) return false;
    guardarActual();
    return true;
  };
})(window.RV);
