'use strict';
/* global window */
// Cifras (estadísticas): un resumen con cifras gigantes, lo terminado por año, lo mejor y lo peor valorado,
// la media por criterio y el reparto de notas, con barras finas de tinta.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};
  let elegido = 'todas';

  const media = (l) => (l.length ? Math.round(l.reduce((s, x) => s + x, 0) / l.length) : null);
  // Una barra fina: etiqueta, la barra (proporcional al máximo) y la cifra
  function barra(k, v, max, opts = {}) {
    const w = max > 0 ? Math.max(0.5, (v / max) * 100) : 0;
    return h('li.barra-fila',
      h(`span.k${opts.serif ? '.serif' : ''}`, { title: k }, k),
      h('span.pista', h(`i${opts.acento ? '.acento' : ''}`, { style: { width: `${w}%` } })),
      h('span.v', String(opts.texto != null ? opts.texto : v), opts.unidad ? h('small', opts.unidad) : null));
  }

  RV.vistas.cifras = (ruta) => {
    if (ruta && ruta.k != null) { elegido = Number(ruta.k); delete ruta.k; }
    const root = h('div.hoja.cifras');
    const tipos = D.tipos();
    if (elegido !== 'todas' && !tipos.some((k) => k.id === elegido)) elegido = 'todas';
    const k1 = elegido === 'todas' ? null : D.tipo(elegido);
    root.append(h('header.cabeza',
      h('div.ante', k1 ? `Cifras · ${k1.title}` : 'Cifras · toda la colección'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Cifras')),
      h('div.filete')));
    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (en Ajustes) para ver tus cifras.', true));
      return root;
    }
    root.append(h('div.pestanas', { role: 'tablist', style: { borderBottom: '1px solid var(--linea)' } },
      [['todas', 'Todo'], ...tipos.map((k) => [k.id, k.title])].map(([id, n]) => h('button.pestana', {
        type: 'button', role: 'tab', 'aria-selected': String(elegido === id), onclick: () => { elegido = id; RV.refrescar(); },
      }, n))));

    const ks = k1 ? [k1] : tipos;
    const vals = [];
    const plays = [];
    for (const k of ks) {
      for (const g of D.valorados(k)) vals.push({ kind: k, item: g });
      for (const p of D.fichas(k)) plays.push({ kind: k, item: p });
    }
    const enCurso = plays.filter((e) => num(e.item.status) === 1).length;
    const enLista = plays.filter((e) => num(e.item.status) === 0).length;
    const notaMedia = media(vals.map((e) => num(e.item.general, 50)));
    // Horas: lo apuntado en las fichas (los libros no cuentan horas)
    const minutos = [...vals, ...plays].filter((e) => !e.kind.isBook).reduce((s, e) => s + num(e.item.minutes), 0);
    const hTotal = Math.round(minutos / 60);

    const cifra = (v, etq, peq) => h('div', h('div.cifra', String(v), peq ? h('small', peq) : null), h('div.ante', etq));
    root.append(h('div.resumen',
      cifra(vals.length, k1 ? k1.doneWord : 'Terminados'),
      cifra(enCurso, 'En curso'),
      cifra(enLista, 'En lista'),
      cifra(notaMedia == null ? '—' : notaMedia, 'Nota media'),
      cifra(hTotal.toLocaleString('es-ES'), 'Horas', 'h')));

    if (!vals.length && !plays.length) {
      root.append(RV.vacio('Aún no hay nada que contar aquí.', true));
      return root;
    }

    // Por año (según la fecha de fin)
    const porAno = new Map();
    for (const e of vals) {
      const end = num(e.item.end);
      if (end <= 0) continue;
      const y = new Date(end * RV.DIA).getUTCFullYear();
      if (!porAno.has(y)) porAno.set(y, []);
      porAno.get(y).push(num(e.item.general, 50));
    }
    const anos = [...porAno.keys()].sort((a, b) => b - a);
    const maxAno = Math.max(0, ...[...porAno.values()].map((l) => l.length));
    const secAnos = h('section', RV.seccion('Por año'));
    if (!anos.length) secAnos.append(RV.vacio('Sin fechas de fin todavía.'));
    else {
      const ul = h('ul.barras');
      for (const y of anos) {
        const l = porAno.get(y);
        ul.append(barra(String(y), l.length, maxAno, { serif: true, acento: y === new Date().getFullYear() }));
        ul.lastChild.querySelector('.v').title = `Nota media ${media(l)}`;
      }
      secAnos.append(ul, h('p.versal', { style: { marginTop: '8px' } }, `Nota media de cada año: ${anos.map((y) => `${y} · ${media(porAno.get(y))}`).join('  ·  ')}`));
    }

    // Reparto de notas (de diez en diez)
    const tramos = Array.from({ length: 10 }, () => 0);
    for (const e of vals) tramos[Math.min(9, Math.floor(num(e.item.general, 50) / 10))]++;
    const maxT = Math.max(...tramos);
    const secReparto = h('section', RV.seccion('Reparto de notas'));
    if (!vals.length) secReparto.append(RV.vacio('Aún no has valorado nada.'));
    else {
      const ul = h('ul.barras');
      for (let i = 9; i >= 0; i--) ul.append(barra(i === 9 ? '90 – 100' : `${i * 10} – ${i * 10 + 9}`, tramos[i], maxT, { acento: i >= 9 }));
      secReparto.append(ul);
    }

    // Mejor y peor valorados
    const orden = [...vals].sort((a, b) => num(b.item.general) - num(a.item.general) || num(b.item.end) - num(a.item.end));
    const lista = (l) => {
      if (!l.length) return RV.vacio('Nada todavía.');
      const ul = h('ul.notas-altas');
      for (const e of l) {
        ul.append(h('li', h('button', { type: 'button', 'data-ficha': `${e.kind.id}:g:${e.item.id}`, onclick: () => RV.ir({ v: 'ficha', k: e.kind.id, t: 'g', id: e.item.id }) },
          h('span.cifra', String(num(e.item.general))),
          h('span', h('span.tit.ajusta', { 'data-min': '14' }, e.item.name), h('span.ante', [e.kind.short, e.item.end ? RV.fmtDia(e.item.end) : ''].filter(Boolean).join(' · '))))));
      }
      return ul;
    };
    const n5 = Math.min(5, Math.ceil(orden.length / 2));
    const secMejor = h('section', RV.seccion('Lo mejor valorado'), lista(orden.slice(0, n5)));
    const secPeor = h('section', RV.seccion('Lo peor valorado'), lista(orden.slice(-n5).reverse().filter((e) => !orden.slice(0, n5).includes(e))));

    // Media por criterio (cada categoría tiene los suyos) o, en «Todo», la media de cada categoría
    let secCrit;
    if (k1) {
      secCrit = h('section', RV.seccion('Media por criterio'));
      if (!vals.length) secCrit.append(RV.vacio('Aún no has valorado nada.'));
      else {
        const ul = h('ul.barras');
        for (let i = 0; i <= k1.crit; i++) {
          const f = D.CAMPOS[i];
          const m = media(vals.map((e) => num(e.item[f], 50)));
          ul.append(barra(k1.labels[i], m, 100, { acento: i === 0 }));
        }
        secCrit.append(ul);
      }
    } else {
      secCrit = h('section', RV.seccion('Media por categoría'));
      const ul = h('ul.barras');
      for (const k of tipos) {
        const l = D.valorados(k).map((g) => num(g.general, 50));
        if (l.length) ul.append(barra(k.title, media(l), 100, { serif: true }));
      }
      secCrit.append(ul.children.length ? ul : RV.vacio('Aún no has valorado nada.'));
    }

    root.append(h('div.p-fila2', { style: { marginTop: '34px' } }, secAnos, secReparto, secCrit));
    root.append(h('div.p-fila2', { style: { gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)' } }, secMejor, secPeor));
    return root;
  };
})(window.RV);
