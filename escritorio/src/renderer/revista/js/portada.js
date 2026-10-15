'use strict';
/* global window, localStorage */
// La portada (RevistaHome del móvil, a varias columnas): la cabecera «ATRIO», el número del día, «En portada»
// con lo que llevas entre manos (la única imagen de la revista), «Ahora mismo», las notas más altas, lo último
// que has terminado y el sumario.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // Qué va en portada: lo eliges tú (CAMBIAR TÍTULO); si no, lo más reciente. Es una preferencia de este equipo.
  const elegido = () => { try { return localStorage.getItem('revista:portada') || ''; } catch { return ''; } };
  const elegir = (v) => { try { localStorage.setItem('revista:portada', v); } catch { /* */ } };
  const clave = (e) => `${e.kind.id}:${e.item.id}`;

  // La imagen de portada: el arte del PC si es el mismo juego (a máxima calidad); si no, el fondo o la portada
  // de la ficha (solo direcciones de internet: las del móvil, content://, aquí no se pueden abrir)
  const remota = (u) => (u && /^https?:\/\//i.test(u) ? u : '');
  function imagenDe(e) {
    const g = D.pcDe(e.kind, e.item);
    const a = (g && g.art) || {};
    return a.herohd || a.hero || a.wide || remota(e.item.hero) || remota(e.item.cover) || a.cover || '';
  }

  // La acción principal de algo en curso (como primaryLabel del móvil)
  function principal(e) {
    const g = D.pcDe(e.kind, e.item);
    if (g) return { texto: 'Jugar en el PC →', fn: () => RV.jugar(g) };
    return { texto: 'Apuntar →', fn: () => RV.ficha.apuntar(e.kind, e.item) };
  }

  function enPortada(lista) {
    const sel = elegido();
    const front = lista.find((e) => clave(e) === sel) || lista[0];
    if (!front) return { front: null, resto: lista };
    const { kind, item } = front;
    const abrir = () => RV.ir({ v: 'ficha', k: kind.id, t: 'p', id: item.id });
    const box = h('section.en-portada', RV.seccion('En portada'));
    const src = imagenDe(front);
    if (src) {
      const img = h('img', { alt: '', decoding: 'async', draggable: 'false' });
      const fig = h('figure', img);
      img.addEventListener('error', () => fig.remove());
      img.src = src;
      fig.addEventListener('click', abrir);
      fig.style.cursor = 'pointer';
      box.append(fig);
    }
    if (lista.length > 1) {
      box.append(h('div', { style: { display: 'flex', gap: '20px' } },
        h('button.enlace', { type: 'button', onclick: () => elegirPortada(lista, front) }, 'Cambiar título')));
    }
    box.append(h(`h2.titular.ajusta.gran-titular${RV.tallaTitular(item.name)}`, { onclick: abrir, 'data-min': '28' }, item.name));
    const meta = [kind.short, item.creator, !kind.isBook && num(item.minutes) > 0 ? RV.horas(item.minutes) : '',
      kind.isBook && num(item.chapter) > 0 ? `capítulo ${item.chapter}` : ''].filter(Boolean).join(' · ');
    if (meta) box.append(h('p.meta', meta));
    const p = principal(front);
    box.append(h('div.acciones',
      h('button.enlace.acento', { type: 'button', onclick: p.fn }, p.texto),
      h('button.enlace', { type: 'button', onclick: abrir }, 'Abrir la ficha →')));
    // Lo último que apuntaste, como cita
    const ult = [...(item.entries || []).filter((x) => x.note && x.note.trim()).map((x) => ({ t: num(x.time), x: x.note })),
      ...D.memos(item).filter((m) => m.x && m.x.trim()).map((m) => ({ t: num(m.t), x: m.x }))].sort((a, b) => b.t - a.t)[0];
    if (ult) {
      const txt = ult.x.trim();
      box.append(h('blockquote.cita', `«${txt.length > 280 ? `${txt.slice(0, 277).trim()}…` : txt}»`,
        h('span.de.versal', `Tu apunte · ${RV.fmtMomento(ult.t)}`)));
    }
    return { front: box, resto: lista.filter((e) => e !== front) };
  }
  function elegirPortada(lista, actual) {
    const ul = h('ul.resultados');
    for (const e of lista) {
      ul.append(h('li', h('button.resultado', {
        type: 'button', style: { width: '100%', textAlign: 'left' },
        onclick: () => { elegir(clave(e)); d.cerrar(); RV.refrescar(); },
      }, h('span.tit', { style: e === actual ? { color: 'var(--acento)' } : {} }, e.item.name), h('span.versal', e.kind.short))));
    }
    const d = RV.dialogo({ ante: 'En portada', titulo: '¿Qué va en portada?', cuerpo: ul });
  }

  // Sin cuenta: la portada invita a entrar (y lo del PC sigue funcionando)
  function invitacion() {
    return h('section.invita',
      RV.seccion('En portada'),
      h('h2.titular.ajusta.gran-titular', { 'data-min': '30' }, 'Tu colección, en papel'),
      h('p', 'Entra con tu cuenta de Atrio para ver aquí lo que estás jugando, leyendo o viendo, tus notas y tus opiniones. Lo mismo que en el móvil, al día.'),
      h('div', { style: { display: 'flex', gap: '22px', flexWrap: 'wrap' } },
        h('button.boton-tinta', { type: 'button', onclick: () => RV.ir({ v: 'ajustes' }) }, 'Entrar con mi cuenta →'),
        h('button.enlace', { type: 'button', onclick: () => RV.ir({ v: 'pc' }) }, 'Ir a Tu PC →')));
  }

  // Últimos juegos del PC (en la columna de la derecha si no hay cuenta o no hay nada en curso)
  function ultimosPc() {
    const games = D.juegosPc().filter((g) => g.lastPlayed > 0).sort((a, b) => b.lastPlayed - a.lastPlayed).slice(0, 8);
    const box = h('section', RV.seccion('Últimos en tu PC', { texto: 'Todos →', fn: () => RV.ir({ v: 'pc' }) }));
    if (!games.length) { box.append(RV.vacio('Aún no has jugado a nada desde Atrio.')); return box; }
    const ol = h('ol.numerada');
    games.forEach((g, i) => ol.append(h('li', h('button', { type: 'button', onclick: () => RV.jugar(g) },
      h('span.num', String(i + 1).padStart(2, '0')),
      h('span', h('span.tit.ajusta', { 'data-min': '14' }, g.name),
        h('span.versal', [g.sourceLabel, `jugado ${RV.hace(g.lastPlayed)}`, 'jugar →'].filter(Boolean).join(' · ')))))));
    box.append(ol);
    return box;
  }

  RV.vistas.portada = () => {
    const hoy = new Date();
    const st = D.cuenta().status || {};
    const nombre = st.user || '';
    const root = h('div.hoja.portada');
    root.append(h('h1.masthead.ajusta', { 'data-min': '60' }, 'ATRIO'));
    root.append(h('div.folio',
      h('span.ante', `Nº ${RV.diaDelAno(hoy)}`),
      h('span.entradilla', nombre ? `La colección de ${nombre}, al día` : 'Tu colección, al día'),
      h('span.ante', RV.fmtLargo(hoy).toUpperCase())));

    // En directo: lo que se está jugando ahora mismo en este PC
    const ahora = D.jugandoAhora();
    if (ahora) {
      root.append(h('div.directo',
        h('span.ante', 'En directo'),
        h('span.que', ahora.game.name),
        h('span.versal', `En este PC desde ${RV.hace(ahora.desde)}`)));
    }

    if (!D.conCuenta()) {
      root.append(h('div.p-fila1', invitacion(), ultimosPc()));
      root.append(sumario(false));
      return root;
    }

    const curso = D.enCurso();
    const { front, resto } = enPortada(curso);
    const fila1 = h('div.p-fila1');
    if (front) fila1.append(front);
    else {
      fila1.append(h('section.invita', RV.seccion('En portada'),
        h('h2.titular.ajusta.gran-titular', { 'data-min': '30' }, 'Nada entre manos'),
        h('p', 'No tienes nada en curso ahora mismo. Empieza algo de tu lista y saldrá aquí, en grande.'),
        h('button.enlace.acento', { type: 'button', onclick: () => RV.ir({ v: 'seccion', k: 0, tab: 1 }) }, 'Ver tu lista de juegos →')));
    }
    // Ahora mismo: lista numerada con el resto de lo que llevas entre manos
    if (resto.length) {
      const box = h('section', RV.seccion('Ahora mismo'));
      const ol = h('ol.numerada');
      resto.slice(0, 8).forEach((e, i) => ol.append(h('li', h('button', { type: 'button', onclick: () => RV.ir({ v: 'ficha', k: e.kind.id, t: 'p', id: e.item.id }) },
        h('span.num', String(i + 1).padStart(2, '0')),
        h('span', h('span.tit.ajusta', { 'data-min': '14' }, e.item.name),
          h('span.versal', [e.kind.short, e.item.creator].filter(Boolean).join(' · ')))))));
      if (resto.length > 8) ol.append(h('li', h('p.versal', { style: { padding: '10px 0' } }, `Y ${resto.length - 8} más`)));
      box.append(ol);
      fila1.append(box);
    } else fila1.append(ultimosPc());
    root.append(fila1);

    // Segunda fila: notas más altas · lo último terminado · sumario
    const todos = D.todo();
    const valorados = todos.filter((e) => e.t === 'g');
    const mejores = [...valorados].sort((a, b) => num(b.item.general) - num(a.item.general) || D.actividad(b.item, 'g') - D.actividad(a.item, 'g')).slice(0, 5);
    const altas = h('section', RV.seccion('Las notas más altas'));
    if (!mejores.length) altas.append(RV.vacio('Aún no has valorado nada.'));
    else {
      const ul = h('ul.notas-altas');
      for (const e of mejores) {
        ul.append(h('li', h('button', { type: 'button', onclick: () => RV.ir({ v: 'ficha', k: e.kind.id, t: 'g', id: e.item.id }) },
          h('span.cifra', String(num(e.item.general))),
          h('span', h('span.tit.ajusta', { 'data-min': '14' }, e.item.name), h('span.ante', e.kind.short)))));
      }
      altas.append(ul);
    }
    const ultimos = valorados.filter((e) => num(e.item.end) > 0).sort((a, b) => num(b.item.end) - num(a.item.end) || num(b.item.updated) - num(a.item.updated)).slice(0, 6);
    const term = h('section', RV.seccion('Lo último que has terminado', { texto: 'Diario →', fn: () => RV.ir({ v: 'diario' }) }));
    if (!ultimos.length) term.append(RV.vacio('Todavía no has terminado nada con fecha.'));
    else {
      const ul = h('ul.terminados');
      for (const e of ultimos) {
        ul.append(h('li', h('button', { type: 'button', onclick: () => RV.ir({ v: 'ficha', k: e.kind.id, t: 'g', id: e.item.id }) },
          h('span', h('span.versal', `${RV.fmtDia(e.item.end)} · ${e.kind.short}`), h('span.tit.ajusta', { 'data-min': '14' }, e.item.name)),
          h('span.cifra', String(num(e.item.general))))));
      }
      term.append(ul);
    }
    root.append(h('div.p-fila2', altas, term, sumario(true)));
    root.append(h('p.pie', 'Atrio · hecho en casa'));
    return root;
  };

  // Sumario: el índice de toda la revista, con puntos guía
  function sumario(enFila) {
    const fila = (t, pag, fn) => h('li', h('button', { type: 'button', onclick: fn }, h('span.tit', t), h('span.guia'), h('span.pag', pag)));
    const ul = h('ul.sumario');
    if (D.conCuenta()) {
      for (const k of D.tipos()) ul.append(fila(k.title, String(D.valorados(k).length + D.fichas(k).length), () => RV.ir({ v: 'seccion', k: k.id })));
      ul.append(fila('Diario', 'cal.', () => RV.ir({ v: 'diario' })));
      ul.append(fila('Cifras', '→', () => RV.ir({ v: 'cifras' })));
    }
    ul.append(fila('Tu PC', String(D.juegosPc().length), () => RV.ir({ v: 'pc' })));
    ul.append(fila('Ajustes', '→', () => RV.ir({ v: 'ajustes' })));
    const box = h('section', RV.seccion('Sumario'), ul);
    if (enFila) return box;
    return h('div.p-fila2', { style: { gridTemplateColumns: 'minmax(0, 1fr)' } }, box);
  }
})(window.RV);
