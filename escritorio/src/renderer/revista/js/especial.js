'use strict';
/* global window */
// «Tal día como hoy» (para la portada) y el número especial: tu año en la revista, como NumeroEspecial del móvil.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  const fecha = (d) => new Date(d * RV.DIA); // los días de la cuenta son UTC
  const verbo = (k) => (k.isGame ? 'Lo pasaste' : k.isBook ? 'Lo leíste' : k.isMovie || k.isTv ? 'La viste' : 'Lo terminaste');
  const abrir = (e) => () => RV.ir({ v: 'ficha', k: e.kind.id, t: 'g', id: e.item.id });

  // Lo que terminaste esta misma fecha en otros años: [{ e, anos }]
  RV.efemerides = (hoy = new Date()) => D.todo().filter((e) => e.t === 'g' && num(e.item.end) > 0).map((e) => {
    const f = fecha(num(e.item.end));
    const y = f.getUTCFullYear();
    return f.getUTCMonth() === hoy.getMonth() && f.getUTCDate() === hoy.getDate() && y < hoy.getFullYear()
      ? { e, anos: hoy.getFullYear() - y } : null;
  }).filter(Boolean).sort((a, b) => a.anos - b.anos);

  RV.seccionEfemerides = () => {
    const hits = RV.efemerides();
    if (!hits.length) return null;
    const ul = h('ul.efemerides');
    for (const { e, anos } of hits.slice(0, 4)) {
      ul.append(h('li', h('button', { type: 'button', onclick: abrir(e) },
        h('span.hace', h('span.cifra', String(anos)), h('span.ante', anos === 1 ? 'año' : 'años')),
        h('span', h('span.tit.ajusta', { 'data-min': '15' }, e.item.name),
          h('span.versal.acento', [verbo(e.kind), `nota ${num(e.item.general)}`].join(' · '))))));
    }
    return h('section', RV.seccion('Tal día como hoy'), ul);
  };

  // Los años en los que terminaste algo (y el actual), del más reciente al más antiguo
  RV.anosEspecial = () => [...new Set([new Date().getFullYear(),
    ...D.todo().filter((e) => e.t === 'g' && num(e.item.end) > 0).map((e) => fecha(num(e.item.end)).getUTCFullYear())])].sort((a, b) => b - a);

  const MES = new Intl.DateTimeFormat('es-ES', { month: 'short', timeZone: 'UTC' });

  RV.vistas.especial = (ruta) => {
    const anos = RV.anosEspecial();
    const y = anos.includes(Number(ruta.y)) ? Number(ruta.y) : anos[0];
    const st = D.cuenta().status || {};
    const root = h('div.hoja.especial');
    const i = anos.indexOf(y);
    const cambiar = (n) => RV.ir({ v: 'especial', y: n }, { reemplazar: true });
    root.append(h('div.folio',
      h('span.ante.acento', 'Número especial'),
      h('span.entradilla', st.user ? `El año de ${st.user}, en cifras y en titulares` : 'Tu año, en cifras y en titulares'),
      h('span', { style: { display: 'flex', gap: '18px' } },
        i < anos.length - 1 ? h('button.enlace', { type: 'button', onclick: () => cambiar(anos[i + 1]) }, `← ${anos[i + 1]}`) : null,
        i > 0 ? h('button.enlace', { type: 'button', onclick: () => cambiar(anos[i - 1]) }, `${anos[i - 1]} →`) : null)));
    root.append(h('h1.masthead.ajusta', { 'data-min': '60' }, `Tu ${y}`));
    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (en Ajustes) para ver tu año.', true));
      return root;
    }

    const tipos = D.tipos();
    const hecho = D.todo().filter((e) => e.t === 'g' && num(e.item.end) > 0 && fecha(num(e.item.end)).getUTCFullYear() === y)
      .sort((a, b) => num(b.item.general) - num(a.item.general) || num(a.item.end) - num(b.item.end));
    if (!hecho.length) {
      root.append(RV.vacio(y === new Date().getFullYear()
        ? 'Aún no has terminado nada este año. Este número se escribe solo en cuanto acabes el primero.'
        : 'Ese año no terminaste nada.', true));
      return root;
    }

    // Cifras: cuántos de cada cosa, horas jugadas y páginas leídas
    const cifra = (v, etq) => h('div', h('div.cifra', String(v)), h('div.ante', etq));
    const cifras = tipos.map((k) => [k, hecho.filter((e) => e.kind.id === k.id).length]).filter(([, n]) => n > 0)
      .map(([k, n]) => cifra(n, k.doneWord));
    const minutos = hecho.filter((e) => e.kind.isGame).reduce((s, e) => s + num(e.item.minutes), 0);
    const paginas = hecho.filter((e) => e.kind.isBook).reduce((s, e) => s + num(e.item.pages), 0);
    if (minutos >= 60) cifras.push(cifra(Math.round(minutos / 60).toLocaleString('es-ES'), 'Horas jugadas'));
    if (paginas > 0) cifras.push(cifra(paginas.toLocaleString('es-ES'), 'Páginas leídas'));
    root.append(h('div.resumen', cifras));

    // La portada del año: lo mejor valorado
    const top = hecho[0];
    const portada = h('section.en-portada', RV.seccion('La portada del año'),
      h(`h2.titular.ajusta.gran-titular${RV.tallaTitular(top.item.name)}`, { onclick: abrir(top), 'data-min': '28' }, top.item.name),
      h('p.esp-nota', h('span.cifra', String(num(top.item.general))), h('small', '/100'),
        h('span.versal', [top.kind.title, top.item.creator, RV.fmtDia(num(top.item.end))].filter(Boolean).join(' · '))));
    const rev = String(top.item.review || '').trim();
    if (rev) portada.append(h('blockquote.cita', `«${rev.length > 320 ? `${rev.slice(0, 317).trim()}…` : rev}»`, h('span.de.versal', 'Tu crítica')));

    // Lo mejor del año (del segundo al undécimo)
    const mejor = h('section', RV.seccion('Lo mejor del año'));
    if (hecho.length < 2) mejor.append(RV.vacio('Solo has terminado una cosa este año: está en portada.'));
    else {
      const ol = h('ol.numerada');
      hecho.slice(1, 11).forEach((e, j) => ol.append(h('li', h('button', { type: 'button', onclick: abrir(e) },
        h('span.num', String(j + 2).padStart(2, '0')),
        h('span', h('span.tit.ajusta', { 'data-min': '14' }, e.item.name),
          h('span.versal', [e.kind.short, `nota ${num(e.item.general)}`].join(' · ')))))));
      mejor.append(ol);
    }
    root.append(h('div.p-fila1', portada, mejor));

    // Mes a mes
    const meses = new Map();
    for (const e of [...hecho].sort((a, b) => num(a.item.end) - num(b.item.end))) {
      const m = fecha(num(e.item.end)).getUTCMonth();
      if (!meses.has(m)) meses.set(m, []);
      meses.get(m).push(e);
    }
    const secMeses = h('section', RV.seccion('Mes a mes'));
    const ulM = h('ul.esp-meses');
    for (const [m, l] of [...meses].sort((a, b) => a[0] - b[0])) {
      const p = h('p');
      l.forEach((e, j) => {
        if (j) p.append(' · ');
        p.append(h('button.enlace-texto', { type: 'button', onclick: abrir(e) }, e.item.name));
      });
      ulM.append(h('li', h('span.ante.acento', MES.format(new Date(Date.UTC(y, m, 1))).replace('.', '')), p));
    }
    secMeses.append(ulM);

    // La nota media de cada cosa y la decepción (si hay de qué hablar)
    const secMedia = h('section', RV.seccion('Tu nota media'));
    const ulN = h('ul.barras');
    for (const k of tipos) {
      const l = hecho.filter((e) => e.kind.id === k.id).map((e) => num(e.item.general, 50));
      if (!l.length) continue;
      const m = l.reduce((s, x) => s + x, 0) / l.length;
      const fila = h('li.barra-fila',
        h('span.k.serif', { title: k.title }, k.title),
        h('span.pista', h('i', { style: { width: `${Math.max(0.5, m)}%` } })),
        h('span.v', m.toLocaleString('es-ES', { maximumFractionDigits: 1 })));
      ulN.append(fila);
    }
    secMedia.append(ulN);
    const peor = hecho[hecho.length - 1];
    if (hecho.length >= 4 && num(peor.item.general, 50) < 60) {
      secMedia.append(h('div', { style: { marginTop: '26px' } }, RV.seccion('La decepción'),
        h('ul.notas-altas', h('li', h('button', { type: 'button', onclick: abrir(peor) },
          h('span.cifra', { style: { color: 'var(--gris)' } }, String(num(peor.item.general))),
          h('span', h('span.tit.ajusta', { 'data-min': '14' }, peor.item.name), h('span.ante', peor.kind.short)))))));
    }
    root.append(h('div.p-fila2.esp-fila', secMeses, secMedia));
    root.append(h('p.pie', `Atrio · número especial · ${y}`));
    return root;
  };
})(window.RV);
