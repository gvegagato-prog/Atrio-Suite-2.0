'use strict';
/* global window */
// Sagas (las crea el móvil): libros, juegos, películas… juntos bajo una portada, en su orden. Cada uno sigue en su
// sitio (terminado con su nota, en curso o en la lista); la saga solo los junta. Aquí se ven, se abren sus fichas
// y se le puede cambiar la portada (SteamGridDB o una dirección).
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // «El nombre del viento (Crónica del asesino de reyes, #1)» → «El nombre del viento» (dentro de la saga ya va su número)
  const SERIE = /\(([^()#]+?),?\s*#\s*(\d+(?:[.,]\d+)?)\)\s*$/;
  const limpio = (t) => String(t || '').replace(SERIE, '').trim() || String(t || '');

  // Las de una categoría (kind; sin él, todas). Las de antes de haber sagas de todo eran de libros.
  const tipoDe = (s) => (s.kind == null ? 1 : Number(s.kind));
  D.sagas = (kind) => (Array.isArray(D.datos().sagas) ? D.datos().sagas : [])
    .filter((s) => s && s.id && !D.datos().tombs?.[`sagas:${s.id}`] && (kind == null || tipoDe(s) === Number(kind)));
  D.saga = (id) => D.sagas().find((s) => Number(s.id) === Number(id)) || null;

  // Los libros de una saga en su orden: { id, name, cover, creator, play, game, leido, leyendo, dejado, nota }
  D.librosDeSaga = (s) => {
    const k = D.tipo(tipoDe(s));
    if (!k) return [];
    const vs = D.valorados(k);
    const fs = D.fichas(k);
    return (s.items || []).map((id) => {
      const g = vs.find((x) => Number(x.id) === Number(id));
      const p = fs.find((x) => Number(x.id) === Number(id));
      if (!g && !p) return null;
      const leyendo = !!p && num(p.status) === 1;
      return {
        id: Number(id), kind: k, name: limpio((g || p).name), cover: (g && g.cover) || (p && p.cover) || '', creator: (g && g.creator) || (p && p.creator) || '',
        play: p, game: g, leyendo, leido: !!g && !leyendo, dejado: !g && !!p && num(p.status) === 2, nota: g ? D.nota(g, 'g') : null,
      };
    }).filter(Boolean);
  };

  const media = (ls) => { const n = ls.filter((b) => b.leido && b.nota != null).map((b) => b.nota); return n.length ? Math.round(n.reduce((a, b) => a + b, 0) / n.length) : null; };
  const autores = (ls) => { const a = [...new Set(ls.map((b) => String(b.creator || '').trim()).filter(Boolean))]; return a.length > 2 ? `${a[0]} y otros` : a.join(' y '); };
  const unidades = (k) => `${k.unit}s`;
  const art = (k) => (k.isMovie || k.isTv ? 'las' : 'los');
  function linea(ls, k) {
    const l = ls.filter((b) => b.leido).length;
    const m = media(ls);
    return [RV.plural(ls.length, k.unit, unidades(k)), l ? `${l} ${(l === 1 ? k.doneOne : k.doneWord).toLowerCase()}` : '',
      ls.some((b) => b.leyendo) ? k.statuses[1].toLowerCase() : '', m != null ? `media ${m}` : ''].filter(Boolean).join(' · ');
  }

  // La portada: la elegida o las de sus libros en abanico
  function portada(s, ls, grande) {
    const covers = ls.map((b) => b.cover).filter(Boolean).map((c) => D.imagen(c)).filter(Boolean);
    const elegida = s.cover ? D.imagen(s.cover) : '';
    const box = h(`div.saga-tapa${grande ? '.grande' : ''}`);
    if (elegida || covers.length <= 1) {
      const src = elegida || covers[0];
      box.append(src ? h('img', { src, alt: '', draggable: 'false', decoding: 'async' }) : h('span.saga-ini', String(s.name || '·').slice(0, 1).toUpperCase()));
      return box;
    }
    box.classList.add('abanico');
    covers.slice(0, 3).reverse().forEach((src, i, arr) => box.append(h('img', { src, alt: '', draggable: 'false', decoding: 'async', style: { '--i': String(arr.length - 1 - i) } })));
    return box;
  }

  const verbo = (k) => (k.isBook ? 'leyendo' : k.isGame ? 'jugando' : k.isMovie || k.isTv ? 'viendo' : '');

  function barra(ls) {
    return h('div.saga-barra', ls.map((b) => h(`span${b.leido ? '.leido' : b.leyendo ? '.leyendo' : ''}`)));
  }

  // La pestaña «Sagas» de una categoría
  RV.sagasLista = (k) => {
    k = k || D.tipo(1);
    const ss = [...D.sagas(k.id)].sort((a, b) => num(b.updated) - num(a.updated));
    if (!ss.length) return [RV.vacio(`Aún no tienes sagas aquí. En el móvil, en ${k.title} → Sagas, juntas bajo una portada ${art(k)} ${unidades(k)} de una saga.`, true)];
    return [h('div.sagas', ss.map((s) => {
      const ls = D.librosDeSaga(s);
      return h('button.saga-item', { type: 'button', onclick: () => RV.ir({ v: 'saga', id: s.id }) },
        portada(s, ls),
        h('span.saga-txt',
          h('span.item-tit.ajusta', { 'data-min': '18' }, s.name),
          autores(ls) ? h('span.cursiva', { style: { display: 'block' } }, autores(ls)) : null,
          h('span.versal.acento', { style: { display: 'block' } }, linea(ls, k)),
          ls.length ? barra(ls) : null));
    }))];
  };

  // La página de una saga
  RV.vistas.saga = (ruta) => {
    const s = D.saga(ruta.id);
    const root = h('div.hoja.saga-pag');
    if (!s) { root.append(RV.vacio('Esta saga ya no existe.', true)); return root; }
    const ls = D.librosDeSaga(s);
    const k = D.tipo(tipoDe(s)) || D.tipo(1);
    root.append(h('header.cabeza',
      h('div.ante', ['Saga', k ? k.title : '', autores(ls)].filter(Boolean).join(' · ')),
      h('h1.titular.ajusta', { 'data-min': '36' }, s.name),
      h('div.filete')));
    const leidos = ls.filter((b) => b.leido).length;
    const m = media(ls);
    const quedan = ls.filter((b) => !b.leido && !b.dejado).length;
    const sig = ls.find((b) => b.leyendo) || ls.find((b) => !b.leido && !b.dejado);
    const abre = (b) => RV.ir({ v: 'ficha', k: b.kind.id, t: b.play && (b.leyendo || !b.game) ? 'p' : 'g', id: b.id });
    root.append(h('div.saga-arriba',
      portada(s, ls, true),
      h('div.saga-datos',
        h('div.saga-cifras',
          h('div', h('span.cifra', `${leidos}/${ls.length}`), h('span.versal', k.doneWord)),
          h('div', h('span.cifra', m != null ? String(m) : '—'), h('span.versal', 'Tu media')),
          h('div', h('span.cifra', String(quedan)), h('span.versal', quedan === 1 ? 'Te queda' : 'Te quedan'))),
        ls.length ? barra(ls) : null,
        sig ? h('div.saga-siguiente',
          h('div.ante', sig.leyendo ? 'Ahora mismo' : 'El siguiente'),
          h('p.item-tit', sig.name),
          h('button.enlace.acento', { type: 'button', onclick: () => abre(sig) }, sig.leyendo && verbo(k) ? `Seguir ${verbo(k)} →` : 'Su ficha →')) : null,
        h('button.enlace', { type: 'button', onclick: () => RV.arteSaga(s) }, 'Cambiar la portada →'))));
    root.append(RV.seccion(k.isBook ? 'En orden de lectura' : 'En orden'));
    if (!ls.length) root.append(RV.vacio(`Aún no tiene ${unidades(k)}.`));
    root.append(h('ol.saga-libros', ls.map((b, i) => h('li', h('button.item', { type: 'button', onclick: () => abre(b) },
      h('span.saga-n', String(i + 1).padStart(2, '0')),
      b.cover ? h('img.saga-mini', { src: D.imagen(b.cover), alt: '', draggable: 'false', decoding: 'async' }) : h('span.saga-mini'),
      h('span',
        h('span.item-tit.ajusta', { 'data-min': '16' }, b.name),
        h(`span.versal${b.leyendo ? '.acento' : ''}`, { style: { display: 'block' } },
          b.leyendo ? [b.kind.statuses[1], b.kind.isBook && num(b.play.chapter) > 0 ? `cap. ${b.play.chapter}` : ''].filter(Boolean).join(' · ')
            : b.leido ? b.kind.doneOne : b.dejado ? b.kind.statuses[2] : b.kind.statuses[0])),
      b.nota != null && b.leido ? h('span.cifra', String(b.nota)) : h('span'))))));
    root.append(h('p.pie', `Las sagas se crean y se ordenan en el móvil (${k.title} → Sagas).`));
    return root;
  };
}(window.RV));
