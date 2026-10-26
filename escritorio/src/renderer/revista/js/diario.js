'use strict';
/* global window */
// El diario (el mismo que el del móvil): cada día, lo que escribiste y lo que hiciste con cada cosa (lo terminaste,
// lo empezaste, lo dejaste, leíste o jugaste un rato, apuntaste algo). Arriba el mes en calendario de periódico; debajo,
// sus días como páginas, del más nuevo al más viejo. Lo escrito es la lista «diario» de la cuenta: se escribe aquí o en
// el móvil y está en los dos.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // Lo que se recuerda mientras la revista está abierta
  const est = { y: null, m: null, k: 'todas' };
  const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
  const fFecha = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
  const fSemana = new Intl.DateTimeFormat('es-ES', { weekday: 'long', timeZone: 'UTC' });
  const fecha = (dia) => { const t = fFecha.format(new Date(dia * RV.DIA)); return t.charAt(0).toUpperCase() + t.slice(1); };
  const tiempo = (ms) => { const m = Math.round(ms / 60000); return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h${m % 60 ? ` ${m % 60} min` : ''}`; };
  const diaLocal = (t) => RV.diaDe(new Date(t));

  RV.diario = {};
  // Las notas (lo escrito) de la cuenta
  RV.diario.notas = () => (Array.isArray(D.datos().diario) ? D.datos().diario.filter((n) => n && Number(n.id) && Number.isFinite(Number(n.dia))) : []);

  // Lo jugado en este PC (y en tus otros PC, que Atrio ya junta): día → clave del juego → ms (partiendo a medianoche)
  RV.diario.jugado = (sessions) => {
    const out = new Map();
    for (const [k, s, e] of sessions || []) {
      let a = Number(s); const b = Number(e);
      if (!k || !(b > a)) continue;
      while (a < b) {
        const d = new Date(a);
        const fin = Math.min(b, new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime());
        const dia = diaLocal(a);
        if (!out.has(dia)) out.set(dia, new Map());
        const m = out.get(dia);
        m.set(k, (m.get(k) || 0) + (fin - a));
        a = fin;
      }
    }
    return out;
  };

  // Lo leído con el lector (lista «lectura» de la cuenta): día → id del libro → ms
  RV.diario.leido = (lectura) => {
    const out = new Map();
    for (const o of lectura || []) {
      const days = o && o.s && o.s.stats && o.s.stats.days;
      if (!days) continue;
      for (const [iso, ms] of Object.entries(days)) {
        const t = Date.parse(`${iso}T00:00:00Z`);
        if (!(num(ms) > 0) || !Number.isFinite(t)) continue;
        const dia = Math.floor(t / RV.DIA);
        if (!out.has(dia)) out.set(dia, new Map());
        out.get(dia).set(Number(o.id), num(ms));
      }
    }
    return out;
  };

  // Los días con algo: [{ dia, notas, toques }] del más nuevo al más viejo (igual que diasDelDiario en el móvil)
  RV.diario.dias = (tipos, opciones = {}) => {
    const porDia = new Map();
    const toque = (dia, kind, t, item) => {
      if (!porDia.has(dia)) porDia.set(dia, new Map());
      const m = porDia.get(dia);
      const clave = item ? `${kind.id}:${item.id}` : `pc:${RV.normName(t)}`;
      if (!m.has(clave)) m.set(clave, { kind, t: item ? t : null, item, nombre: item ? item.name : t, terminado: false, nota: null, opinion: '', empezado: false, dejado: false, leido: 0, jugado: 0, apuntes: [], ult: 0 });
      return m.get(clave);
    };
    const ficha = (kind, id) => {
      const p = D.fichas(kind).find((o) => Number(o.id) === Number(id));
      if (p) return ['p', p];
      const g = D.valorados(kind).find((o) => Number(o.id) === Number(id));
      return g ? ['g', g] : null;
    };
    for (const k of tipos) {
      for (const g of D.valorados(k)) {
        const [t, item] = ficha(k, g.id);
        if (num(g.end) > 0) Object.assign(toque(num(g.end), k, t, item), { terminado: true, nota: num(g.general), opinion: g.review || '' });
        if (num(g.start) > 0 && num(g.start) !== num(g.end)) toque(num(g.start), k, t, item).empezado = true;
      }
      for (const p of D.fichas(k)) {
        if (num(p.start) > 0) toque(num(p.start), k, 'p', p).empezado = true;
        if (num(p.status) === 2 && num(p.end) > 0) toque(num(p.end), k, 'p', p).dejado = true;
        for (const m of D.memos(p)) if (num(m.t) > 0) { const x = toque(diaLocal(num(m.t)), k, 'p', p); x.apuntes.push({ t: num(m.t), texto: m.x || '' }); x.ult = Math.max(x.ult, num(m.t)); }
        for (const e of p.entries || []) if (num(e.time) > 0) { const x = toque(diaLocal(num(e.time)), k, 'p', p); x.apuntes.push({ t: num(e.time), texto: e.note || '', nota: num(e.general) }); x.ult = Math.max(x.ult, num(e.time)); }
      }
    }
    const libros = tipos.find((k) => k.isBook);
    if (libros) for (const [dia, m] of opciones.leido || new Map()) for (const [id, ms] of m) {
      if (ms < 60000) continue;
      const f = ficha(libros, id);
      if (f) toque(dia, libros, f[0], f[1]).leido += ms;
    }
    const juegos = tipos.find((k) => k.isGame);
    if (juegos) for (const [dia, m] of opciones.jugado || new Map()) for (const [key, ms] of m) {
      if (ms < 60000) continue;
      const g = (opciones.juegosPc || []).find((x) => x.key === key || (x.aliasKeys || []).includes(key));
      const nombre = g ? g.name : String(key).replace(/^[^:]*:/, '');
      const sg = g && Number(g.sgdbKnown);
      const n = RV.normName(nombre);
      const id = (sg && ficha(juegos, sg) ? sg : null)
        || (D.fichas(juegos).find((o) => RV.normName(o.name) === n) || D.valorados(juegos).find((o) => RV.normName(o.name) === n) || {}).id;
      const f = id ? ficha(juegos, id) : null;
      if (f) toque(dia, juegos, f[0], f[1]).jugado += ms;
      else toque(dia, juegos, nombre, null).jugado += ms;
    }
    const ids = new Set(tipos.map((k) => k.id));
    const notas = new Map();
    for (const n of RV.diario.notas()) {
      if (opciones.soloDe && !(n.sobre || []).some((r) => ids.has(Number(String(r).split(':')[0])))) continue;
      if (!notas.has(num(n.dia))) notas.set(num(n.dia), []);
      notas.get(num(n.dia)).push(n);
    }
    const dias = [...new Set([...porDia.keys(), ...notas.keys()])].sort((a, b) => b - a);
    return dias.map((dia) => ({
      dia,
      notas: (notas.get(dia) || []).sort((a, b) => num(a.id) - num(b.id)),
      toques: [...(porDia.get(dia) || new Map()).values()].sort((a, b) => (b.terminado - a.terminado) || ((b.jugado + b.leido) - (a.jugado + a.leido)) || (b.ult - a.ult)),
    }));
  };

  // «Lo terminaste · 2 h jugando · un apunte»
  RV.diario.resumen = (x) => {
    const p = [];
    if (x.terminado) p.push(x.kind.isMovie || x.kind.isTv ? 'La terminaste' : 'Lo terminaste');
    else if (x.dejado) p.push('Lo dejaste');
    if (x.empezado && !x.terminado) p.push(x.kind.isMovie || x.kind.isTv ? 'La empezaste' : 'Lo empezaste');
    if (x.jugado >= 60000) p.push(`${tiempo(x.jugado)} jugando`);
    if (x.leido >= 60000) p.push(`${tiempo(x.leido)} leyendo`);
    const s = p.join(' · ');
    return s ? s.charAt(0).toUpperCase() + s.slice(1) : '';
  };

  // Escribir (o cambiar) una entrada del diario
  RV.diario.escribir = (dia, nota) => {
    const texto = RV.areaTexto(nota ? nota.x : '', { placeholder: '¿Qué tal el día? Lo que has jugado, leído o visto, lo que te ha gustado, lo que no…', rows: '9' });
    const cuando = RV.entrada(RV.isoDia(nota ? num(nota.dia) : dia), { type: 'date', max: RV.isoDia(RV.hoy()) });
    const guardar = async (d) => {
      const x = texto.value.trim();
      const t = Date.parse(`${cuando.value}T00:00:00Z`);
      const nuevoDia = Number.isFinite(t) ? Math.min(Math.floor(t / RV.DIA), RV.hoy()) : dia;
      if (!x && !(nota && nota.foto)) { if (nota) await borrar(d); else d.cerrar(); return; }
      const item = { ...(nota || {}), id: nota ? num(nota.id) : Date.now(), dia: nuevoDia, x };
      if (await RV.editar({ op: 'diarioPut', item }, nota ? 'Entrada guardada' : 'Guardado en tu diario')) d.cerrar();
    };
    const borrar = async (d) => { if (await RV.editar({ op: 'diarioDel', id: num(nota.id) }, 'Entrada borrada')) d.cerrar(); };
    RV.dialogo({
      ante: nota ? 'Tu diario' : 'Escribir en tu diario', titulo: fecha(nota ? num(nota.dia) : dia), ancho: true,
      cuerpo: [RV.campo('Día', cuando), RV.campo('Texto', texto),
        nota && nota.foto ? h('p.vacio', 'Tiene una foto: se ve en el móvil.') : null],
      acciones: [
        nota ? { texto: 'Borrar la entrada', peligro: true, fn: (d) => RV.confirmar('¿Borrar esta entrada?', 'Se borra en todos tus dispositivos.', 'Borrar', () => borrar(d)) } : null,
        'hueco',
        { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: guardar },
      ],
    });
    requestAnimationFrame(() => texto.focus());
  };

  const abrir = (x) => { if (x.item) RV.ir({ v: 'ficha', k: x.kind.id, t: x.t, id: x.item.id }); };

  // Un día como página: la fecha, lo escrito y lo hecho
  const pagina = (d, conEscribir) => h('article.dia-pagina', { 'data-dia': String(d.dia) },
    h('header.dia-cab',
      h('span.dia-num', String(new Date(d.dia * RV.DIA).getUTCDate())),
      h('span.dia-sem', fSemana.format(new Date(d.dia * RV.DIA))),
      h('span.hueco'),
      conEscribir ? h('button.enlace', { type: 'button', onclick: () => RV.diario.escribir(d.dia, null) }, 'Escribir') : null),
    ...d.notas.map((n) => h('button.nota-diario', { type: 'button', onclick: () => RV.diario.escribir(d.dia, n) }, n.x || '(una foto)')),
    d.toques.length ? h('ul.toques', d.toques.map((x) => h('li', h('button', { type: 'button', disabled: !x.item, onclick: () => abrir(x) },
      h('span',
        h('span.tit.ajusta', { 'data-min': '14' }, x.nombre),
        RV.diario.resumen(x) ? h(`span.que${x.terminado ? '.acento' : ''}`, RV.diario.resumen(x)) : null,
        x.terminado && x.opinion ? h('span.opinion', x.opinion.length > 180 ? `${x.opinion.slice(0, 178).trimEnd()}…` : x.opinion) : null,
        ...x.apuntes.sort((a, b) => a.t - b.t).map((a) => (a.texto || a.nota != null ? h('span.apunte', a.nota != null ? h('b', String(a.nota)) : null, a.texto) : null))),
      x.terminado && x.nota != null ? h('span.cifra', String(x.nota)) : null)))) : null);

  RV.vistas.diario = () => {
    const ahora = new Date();
    if (est.y === null) { est.y = ahora.getFullYear(); est.m = ahora.getMonth(); }
    const root = h('div.hoja.diario');
    root.append(h('header.cabeza',
      h('div.ante', 'Lo que escribes y lo que haces, día a día'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Diario'),
        D.conCuenta() ? h('button.boton-tinta', { type: 'button', onclick: () => RV.diario.escribir(RV.hoy(), null) }, 'Escribir hoy') : null),
      h('div.filete')));
    if (!D.conCuenta()) {
      root.append(RV.vacio('Entra con tu cuenta de Atrio (en Ajustes) para ver tu diario.', true));
      return root;
    }

    const tipos = D.tipos();
    if (est.k !== 'todas' && !tipos.some((k) => k.id === est.k)) est.k = 'todas';
    const elegidos = est.k === 'todas' ? tipos : tipos.filter((k) => k.id === est.k);
    const pc = D.pc();
    const dias = RV.diario.dias(elegidos, {
      leido: RV.diario.leido(D.datos().lectura), jugado: RV.diario.jugado((pc.play || {}).sessions), juegosPc: pc.games || [], soloDe: est.k !== 'todas',
    });
    const porDia = new Map(dias.map((d) => [d.dia, d]));

    // El mes
    const d0 = Math.floor(Date.UTC(est.y, est.m, 1) / RV.DIA);
    const diasMes = new Date(Date.UTC(est.y, est.m + 1, 0)).getUTCDate();
    const d1 = d0 + diasMes - 1;
    const delMes = dias.filter((d) => d.dia >= d0 && d.dia <= d1);
    const terminados = delMes.reduce((s, d) => s + d.toques.filter((x) => x.terminado).length, 0);
    const leido = delMes.reduce((s, d) => s + d.toques.reduce((a, x) => a + x.leido, 0), 0);
    const jugado = delMes.reduce((s, d) => s + d.toques.reduce((a, x) => a + x.jugado, 0), 0);
    const entradas = delMes.reduce((s, d) => s + d.notas.length, 0);
    const resumen = [terminados ? RV.plural(terminados, 'terminado', 'terminados') : '', leido >= 60000 ? `${tiempo(leido)} leyendo` : '',
      jugado >= 60000 ? `${tiempo(jugado)} jugando` : '', entradas ? RV.plural(entradas, 'entrada', 'entradas') : ''].filter(Boolean).join(' · ');

    const mover = (n) => { const d = new Date(est.y, est.m + n, 1); est.y = d.getFullYear(); est.m = d.getMonth(); RV.refrescar(); };
    const esEste = est.y === ahora.getFullYear() && est.m === ahora.getMonth();
    root.append(h('div.diario-mes',
      h('button.enlace', { type: 'button', onclick: () => mover(-1), 'aria-label': 'Mes anterior' }, `← ${MESES[(est.m + 11) % 12]}`),
      h('span.mes', `${MESES[est.m]} ${est.y}`),
      h('button.enlace', { type: 'button', onclick: () => mover(1), 'aria-label': 'Mes siguiente' }, `${MESES[(est.m + 1) % 12]} →`),
      esEste ? null : h('button.enlace.acento', { type: 'button', onclick: () => { est.y = ahora.getFullYear(); est.m = ahora.getMonth(); RV.refrescar(); } }, 'Hoy'),
      h('span.hueco'),
      h('span.versal', resumen)));
    root.append(h('div.pestanas', { role: 'tablist', style: { borderBottom: '1px solid var(--linea)' } },
      [['todas', 'Todo'], ...tipos.map((k) => [k.id, k.title])].map(([id, n]) => h('button.pestana', {
        type: 'button', role: 'tab', 'aria-selected': String(est.k === id), onclick: () => { est.k = id; RV.refrescar(); },
      }, n))));

    // El calendario (la semana empieza en lunes): lo terminado con su nota, ✎ si escribiste y el tiempo leído y jugado
    const cal = h('div.cal');
    for (const s of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']) cal.append(h('div.sem', s));
    const hueco = (new Date(est.y, est.m, 1).getDay() + 6) % 7;
    for (let i = 0; i < hueco; i++) cal.append(h('div.dia.fuera'));
    const hoy = RV.hoy();
    for (let n = 1; n <= diasMes; n++) {
      const dia = d0 + n - 1;
      const d = porDia.get(dia);
      const ms = d ? d.toques.reduce((a, x) => a + x.jugado + x.leido, 0) : 0;
      cal.append(h(`div.dia${dia === hoy ? '.hoy' : ''}`,
        h('button.num', { type: 'button', title: d ? 'Ver ese día' : 'Escribir ese día', disabled: dia > hoy, onclick: () => {
          const el = d && document.querySelector(`.dia-pagina[data-dia="${dia}"]`);
          if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' }); else if (dia <= hoy) RV.diario.escribir(dia, null);
        } }, String(n), d && d.notas.length ? h('span.pluma', ' ✎') : null),
        h('div', d ? d.toques.filter((x) => x.terminado).map((x) => h('button.tit', { type: 'button', onclick: () => abrir(x) },
          est.k === 'todas' ? h('span.que', x.kind.short) : null, x.nombre, x.nota != null ? h('b', String(x.nota)) : null)) : null),
        ms >= 60000 ? h('span.tiempo', tiempo(ms)) : null));
    }
    const resto = (7 - ((hueco + diasMes) % 7)) % 7;
    for (let i = 0; i < resto; i++) cal.append(h('div.dia.fuera'));
    root.append(cal);

    // Los días del mes, como páginas
    const lista = h('section.mes-lista', RV.seccion(`Los días de ${MESES[est.m]}`, { texto: 'Escribir hoy', fn: () => RV.diario.escribir(RV.hoy(), null) }));
    if (!delMes.length) lista.append(RV.vacio(esEste ? 'Aún no hay nada este mes. Lo que termines, empieces, leas o juegues aparecerá aquí solo; y lo que quieras escribir.' : 'Ese mes no hubo nada.'));
    else for (const d of delMes) lista.append(pagina(d, true));
    root.append(lista);
    return root;
  };
})(window.RV);
