'use strict';
/* global window */
// El diario: un calendario mensual de periódico. Cada día con su filete arriba, el número en serif (hoy, en el
// acento) y los títulos que terminaste ese día. Debajo, el mes en lista (lo que se lee mejor en ventana estrecha).
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // Lo que se recuerda mientras la revista está abierta
  const est = { y: null, m: null, k: 'todas' };
  const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

  RV.vistas.diario = () => {
    const ahora = new Date();
    if (est.y === null) { est.y = ahora.getFullYear(); est.m = ahora.getMonth(); }
    const root = h('div.hoja.diario');
    root.append(h('header.cabeza',
      h('div.ante', 'Lo que has terminado, día a día'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Diario')),
      h('div.filete')));
    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (en Ajustes) para ver tu diario.', true));
      return root;
    }

    const tipos = D.tipos();
    if (est.k !== 'todas' && !tipos.some((k) => k.id === est.k)) est.k = 'todas';
    const elegidos = est.k === 'todas' ? tipos : tipos.filter((k) => k.id === est.k);

    // Terminados de este mes, por día (días desde 1970)
    const d0 = Math.floor(Date.UTC(est.y, est.m, 1) / RV.DIA);
    const diasMes = new Date(Date.UTC(est.y, est.m + 1, 0)).getUTCDate();
    const d1 = d0 + diasMes - 1;
    const porDia = new Map();
    for (const k of elegidos) {
      for (const g of D.valorados(k)) {
        const e = num(g.end);
        if (e < d0 || e > d1) continue;
        if (!porDia.has(e)) porDia.set(e, []);
        porDia.get(e).push({ kind: k, item: g });
      }
    }
    for (const l of porDia.values()) l.sort((a, b) => num(b.item.general) - num(a.item.general));
    const total = [...porDia.values()].reduce((s, l) => s + l.length, 0);

    // Mes, flechas y categorías
    const mover = (n) => { const d = new Date(est.y, est.m + n, 1); est.y = d.getFullYear(); est.m = d.getMonth(); RV.refrescar(); };
    const esEste = est.y === ahora.getFullYear() && est.m === ahora.getMonth();
    root.append(h('div.diario-mes',
      h('button.enlace', { type: 'button', onclick: () => mover(-1), 'aria-label': 'Mes anterior' }, `← ${MESES[(est.m + 11) % 12]}`),
      h('span.mes', `${MESES[est.m]} ${est.y}`),
      h('button.enlace', { type: 'button', onclick: () => mover(1), 'aria-label': 'Mes siguiente' }, `${MESES[(est.m + 1) % 12]} →`),
      esEste ? null : h('button.enlace.acento', { type: 'button', onclick: () => { est.y = ahora.getFullYear(); est.m = ahora.getMonth(); RV.refrescar(); } }, 'Hoy'),
      h('span.hueco'),
      h('span.versal', total ? `${RV.plural(total, 'terminado', 'terminados')} este mes` : '')));
    root.append(h('div.pestanas', { role: 'tablist', style: { borderBottom: '1px solid var(--linea)' } },
      [['todas', 'Todo'], ...tipos.map((k) => [k.id, k.title])].map(([id, n]) => h('button.pestana', {
        type: 'button', role: 'tab', 'aria-selected': String(est.k === id), onclick: () => { est.k = id; RV.refrescar(); },
      }, n))));

    // El calendario (la semana empieza en lunes)
    const cal = h('div.cal');
    for (const s of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']) cal.append(h('div.sem', s));
    const primero = new Date(est.y, est.m, 1).getDay(); // 0 domingo
    const hueco = (primero + 6) % 7;
    for (let i = 0; i < hueco; i++) cal.append(h('div.dia.fuera'));
    const hoy = RV.hoy();
    for (let n = 1; n <= diasMes; n++) {
      const dia = d0 + n - 1;
      const cosas = porDia.get(dia) || [];
      cal.append(h(`div.dia${dia === hoy ? '.hoy' : ''}`,
        h('span.num', String(n)),
        h('div', cosas.map(({ kind, item }) => h('button.tit', {
          type: 'button', onclick: () => RV.ir({ v: 'ficha', k: kind.id, t: 'g', id: item.id }),
        }, est.k === 'todas' ? h('span.que', kind.short) : null, item.name, h('b', String(num(item.general))))))));
    }
    const resto = (7 - ((hueco + diasMes) % 7)) % 7;
    for (let i = 0; i < resto; i++) cal.append(h('div.dia.fuera'));
    root.append(cal);

    // El mes en lista
    const lista = h('section.mes-lista', RV.seccion(`Terminados en ${MESES[est.m]}`));
    if (!total) lista.append(RV.vacio(esEste ? 'Este mes todavía no has terminado nada. Todo llegará.' : 'Este mes no terminaste nada.'));
    else {
      const ul = h('ul.terminados');
      for (const dia of [...porDia.keys()].sort((a, b) => b - a)) {
        for (const { kind, item } of porDia.get(dia)) {
          ul.append(h('li', h('button', { type: 'button', onclick: () => RV.ir({ v: 'ficha', k: kind.id, t: 'g', id: item.id }) },
            h('span', h('span.versal', `${RV.fmtDiaCorto(dia)} · ${kind.short}`), h('span.tit.ajusta', { 'data-min': '14' }, item.name)),
            h('span.cifra', String(num(item.general))))));
        }
      }
      lista.append(ul);
    }
    root.append(lista);
    return root;
  };
})(window.RV);
