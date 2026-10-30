'use strict';
/* global window */
// Escribir: el manuscrito en cuadrados (partes y capítulos sueltos), la página de cada parte (sus capítulos y lo vinculado:
// notas, resúmenes, esquemas, fichas) y la de cada capítulo (sus escenas, leerlo entero y sus notas). Los cuadrados llevan
// el color que elijas. Mismo modelo que el móvil (EscribirPartes.kt); la estructura está en escribir-estructura.js.
(function (RV) {
  const h = RV.h;
  const num = RV.num;
  const E = RV.E;
  const op = (...a) => E.op(...a);
  const vacio = (x) => x == null || x === false;
  const limpia = (l) => l.flat(Infinity).filter((x) => !vacio(x));
  const primeraLinea = (t) => String(t || '').split('\n').map((l) => l.trim()).find((l) => l && !l.startsWith('#') && l !== '***') || '';
  const NUMS = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

  // Arrastrar cuadrados: lo que se arrastra y dónde se suelta
  let arrastra = null;
  const suelta = async (fn) => { try { await fn(); } catch (e) { E.err(e); } };

  // ---------------------------------------------------------------- Piezas
  const atras = (texto, ruta) => h('button.enlace.e-atras', { type: 'button', onclick: () => RV.ir(ruta) }, `← ${texto}`);

  /** El cuadrado de un capítulo: número, título, resumen o primera línea, escenas y palabras; con su color. */
  function cuadroCapitulo(c, { arrastrable = true, alSoltar = null } = {}) {
    const esc = E.escenas(c.id);
    const pal = E.palabrasCapitulo(c.id);
    const ests = [0, 0, 0]; for (const s of esc) ests[Math.min(2, num(s.estado))]++;
    const el = h('button.e-cuadro.e-cuadro-cap', {
      type: 'button', 'data-id': c.id, style: { '--c': E.colorDe(c.color, 'var(--tinta)') }, draggable: arrastrable ? 'true' : null,
      onclick: () => RV.ir({ v: 'capitulo', id: c.id }),
      ondragstart: (ev) => { arrastra = { t: 'c', id: c.id }; ev.dataTransfer.effectAllowed = 'move'; el.classList.add('cogido'); },
      ondragend: () => { el.classList.remove('cogido'); arrastra = null; },
      ondragover: (ev) => { if (arrastra && arrastra.id !== c.id && alSoltar) { ev.preventDefault(); el.classList.add('sobre'); } },
      ondragleave: () => el.classList.remove('sobre'),
      ondrop: (ev) => { ev.preventDefault(); el.classList.remove('sobre'); const a = arrastra; arrastra = null; if (a && a.id !== c.id && alSoltar) alSoltar(a); },
    },
    h('span.e-cuadro-franja'),
    h('span.versal', `Capítulo ${E.numeroCapitulo(c)}`),
    h('span.e-cuadro-titulo', c.titulo || 'Sin título'),
    h('span.e-cuadro-texto', c.resumen || primeraLinea((esc[0] || {}).texto) || 'Sin escribir todavía.'),
    h('span.e-cuadro-pie', h('span.e-mini-estados', ests.map((n, i) => (n ? h('i', { style: { background: E.COL_ESTADO[i], flex: String(n) } }) : null))),
      `${RV.plural(esc.length, 'escena', 'escenas')} · ${E.cifra(pal)} palabras`));
    return el;
  }

  /** El cuadrado de una parte: su título, su resumen, sus capítulos y palabras. */
  function cuadroParte(a, caps, { alSoltar = null } = {}) {
    const pal = caps.reduce((s, c) => s + E.palabrasCapitulo(c.id), 0);
    const el = h('button.e-cuadro.e-cuadro-parte', {
      type: 'button', 'data-id': a.id, style: { '--c': E.colorDe(a.color) }, draggable: 'true',
      onclick: () => RV.ir({ v: 'parte', id: a.id }),
      ondragstart: (ev) => { arrastra = { t: 'a', id: a.id }; ev.dataTransfer.effectAllowed = 'move'; el.classList.add('cogido'); },
      ondragend: () => { el.classList.remove('cogido'); arrastra = null; },
      ondragover: (ev) => { if (arrastra && arrastra.id !== a.id) { ev.preventDefault(); el.classList.add('sobre'); } },
      ondragleave: () => el.classList.remove('sobre'),
      ondrop: (ev) => { ev.preventDefault(); el.classList.remove('sobre'); const x = arrastra; arrastra = null; if (x && x.id !== a.id && alSoltar) alSoltar(x); },
    },
    h('span.e-cuadro-franja'),
    h('span.versal.acento', 'Parte'),
    h('span.e-cuadro-titulo', a.titulo || 'Sin título'),
    a.resumen ? h('span.e-cuadro-texto', a.resumen) : null,
    caps.length ? h('span.e-cuadro-lista', caps.slice(0, 6).map((c) => h('span', `${E.numeroCapitulo(c)}. ${c.titulo || 'Sin título'}`)), caps.length > 6 ? h('span', `… y ${caps.length - 6} más`) : null) : h('span.e-cuadro-texto.apagado', 'Sin capítulos todavía.'),
    h('span.e-cuadro-pie', `${RV.plural(caps.length, 'capítulo', 'capítulos')} · ${E.cifra(pal)} palabras`));
    return el;
  }

  /** El cuadrado de una nota o una ficha vinculada. */
  function cuadroVinculado(d, pid) {
    const esFicha = d.t === 'f';
    const tipo = esFicha ? ((E.TIPOS_FICHA.find((t) => t[0] === d.tipo) || [])[2] || 'Ficha') : ((E.TIPOS_NOTA.find((t) => t[0] === d.tipo) || [])[2] || 'Nota');
    return h('button.e-cuadro.e-cuadro-nota', { type: 'button', style: { '--c': E.colorDe(d.color) }, onclick: () => (esFicha ? E.verFicha(pid, d) : E.verNota(pid, d)) },
      h('span.e-cuadro-franja'),
      h('span.versal', tipo),
      h('span.e-cuadro-titulo', esFicha ? d.nombre || 'Sin nombre' : d.titulo || tipo),
      h('span.e-cuadro-texto', esFicha ? d.resumen || primeraLinea(d.texto) : d.texto || ''));
  }

  const cuadroNuevo = (texto, fn) => h('button.e-cuadro.e-cuadro-nuevo', { type: 'button', onclick: fn }, h('span', '+'), h('span.versal', texto));

  /** Vincular notas y fichas que ya existen a una parte o un capítulo (o quitarlas). */
  function dlgVincular(pid, destino) {
    const todas = [...E.notas(pid), ...E.fichas(pid)];
    const sel = new Set(todas.filter((d) => Array.isArray(d.vin) && d.vin.includes(destino.id)).map((d) => d.id));
    const fila = (d) => {
      const b = h('input', { type: 'checkbox', checked: sel.has(d.id) ? '' : null, onchange: (e) => { if (e.target.checked) sel.add(d.id); else sel.delete(d.id); } });
      return h('label.e-check.e-vinc', b, h('i', { style: { background: E.colorDe(d.color) } }), h('b', d.t === 'f' ? d.nombre || 'Sin nombre' : d.titulo || 'Nota'),
        h('small', d.t === 'f' ? ` ${(E.TIPOS_FICHA.find((t) => t[0] === d.tipo) || [])[2] || 'Ficha'}` : ` ${(E.TIPOS_NOTA.find((t) => t[0] === d.tipo) || [])[2] || 'Nota'}`));
    };
    RV.dialogo({
      ante: 'Vincular', titulo: `A «${E.tituloDe(destino)}»`, ancho: true,
      cuerpo: todas.length ? [h('p.texto', 'Lo que marques sale también aquí. Las notas siguen en Notas y las fichas en Fichas.'),
        E.notas(pid).length ? h('div.versal', 'Notas') : null, ...E.notas(pid).map(fila),
        E.fichas(pid).length ? h('div.versal', 'Fichas') : null, ...E.fichas(pid).map(fila)].filter(Boolean) : [h('p.texto', 'Aún no hay notas ni fichas en esta obra.')],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: async (d) => {
        try {
          for (const x of todas) {
            const tiene = Array.isArray(x.vin) && x.vin.includes(destino.id);
            if (tiene !== sel.has(x.id)) await op('edita', x.id, { vin: sel.has(x.id) ? [...(x.vin || []), destino.id] : (x.vin || []).filter((v) => v !== destino.id) });
          }
          d.cerrar();
        } catch (e) { E.err(e); }
      } }],
    });
  }

  // ---------------------------------------------------------------- El manuscrito: cuadrados
  let vistaLista = false;
  const migradas = new Set();
  E.manuscrito = (obra) => {
    const pid = obra.id;
    // Las partes de antes (un texto en cada capítulo) pasan a ser partes de verdad, una sola vez
    if (!migradas.has(pid)) { migradas.add(pid); if (E.tipo('c', pid).some((c) => String(c.parte || '').trim() && !c.aid)) op('migraPartes', pid).catch(E.err); }
    const est = E.estructura(pid);
    const pal = E.palabrasObra(pid); const meta = num(obra.meta);
    const ests = [0, 0, 0]; for (const s of E.escenasDe(pid)) ests[Math.min(2, num(s.estado))]++;
    const raiz = h('div.e-manu');
    const sig = E.tarjetaGuia(obra);
    if (sig) raiz.append(sig);
    raiz.append(h('div.e-resumen', h('span', `${E.cifra(pal)}${meta ? ` de ${E.cifra(meta)} palabras` : ' palabras'} · ${RV.plural(E.partes(pid).length, 'parte', 'partes')} · ${RV.plural(est.libro.length, 'capítulo', 'capítulos')}`),
      meta ? E.barra(pal / meta, E.colorDe(obra.color)) : null,
      h('div.e-estados', E.ESTADOS.map((n, i) => h('span', h('i', { style: { background: E.COL_ESTADO[i] } }), `${ests[i]} ${n.toLowerCase()}`)),
        h('span.hueco'),
        h('div.e-chips', h('button.e-chip', { type: 'button', 'aria-pressed': vistaLista ? 'false' : 'true', onclick: () => { vistaLista = false; RV.refrescar(); } }, 'Cuadrados'),
          h('button.e-chip', { type: 'button', 'aria-pressed': vistaLista ? 'true' : 'false', onclick: () => { vistaLista = true; RV.refrescar(); } }, 'Lista')))));
    raiz.append(h('div.fila-botones.e-nuevos', h('button.boton-tinta', { type: 'button', onclick: () => E.dlgParte(pid, null) }, 'Nueva parte'), h('button.boton-borde', { type: 'button', onclick: () => E.dlgCapitulo(pid, null) }, 'Nuevo capítulo')));
    if (vistaLista) { E.manuscritoLista(obra, raiz); return raiz; }
    if (!est.items.length) {
      raiz.append(h('div.e-vacio', h('h2.titular', 'El manuscrito'), h('p', 'Crea partes (actos, libros…) y capítulos dentro, o capítulos sueltos si no quieres partes. Cada capítulo es un cuadrado: ábrelo para escribir sus escenas, leerlo entero o apuntar notas.')));
      return raiz;
    }
    // Arriba se ordenan partes y capítulos sueltos (arrastrando); un capítulo soltado sobre una parte entra en ella
    const arriba = est.items.map((x) => (x.tipo === 'parte' ? x.parte : x.cap));
    const moverArriba = (x, sobre) => {
      const otros = arriba.filter((y) => y.id !== x.id);
      const i = otros.findIndex((y) => y.id === sobre.id);
      const prev = i > 0 ? otros[i - 1].id : null;
      return x.t === 'c' ? op('mueveCapitulo', x.id, '', prev, sobre.id) : op('mueve', x.id, prev, sobre.id);
    };
    const rejilla = h('div.e-cuadros');
    for (const it of est.items) {
      if (it.tipo === 'parte') {
        rejilla.append(cuadroParte(it.parte, it.caps, { alSoltar: (x) => suelta(() => (x.t === 'c' ? op('mueveCapitulo', x.id, it.parte.id, null, null) : moverArriba(x, it.parte))) }));
      } else {
        rejilla.append(cuadroCapitulo(it.cap, { alSoltar: (x) => suelta(() => moverArriba(x, it.cap)) }));
      }
    }
    raiz.append(rejilla, h('p.apagado.e-ayuda', 'Arrastra los cuadrados para ordenarlos; suelta un capítulo sobre una parte para meterlo en ella.'));
    return raiz;
  };

  // ---------------------------------------------------------------- La página de una parte
  RV.vistas.parte = (ruta) => {
    const a = E.get(ruta.id);
    if (!a || a.t !== 'a') { RV.volver() || RV.ir({ v: 'escribir' }); return h('div.hoja'); }
    const pid = a.pid; const obra = E.get(pid) || {};
    const caps = E.estructura(pid).items.find((x) => x.tipo === 'parte' && x.parte.id === a.id);
    const lista = caps ? caps.caps : [];
    const vin = E.vinculados(a.id, pid);
    const pal = lista.reduce((s, c) => s + E.palabrasCapitulo(c.id), 0);
    const numero = E.partes(pid).findIndex((x) => x.id === a.id);
    const root = h('div.hoja.escribir.e-pagina', { style: { '--c': E.colorDe(a.color) } });
    root.append(h('header.cabeza',
      h('div.ante', atras(obra.titulo || 'La obra', { v: 'obra', id: pid, tab: 0 }), ` · Parte ${NUMS[numero] || numero + 1} · ${RV.plural(lista.length, 'capítulo', 'capítulos')} · ${E.cifra(pal)} palabras`),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, a.titulo || 'Sin título'),
        h('div.herramientas', h('button.enlace', { type: 'button', onclick: () => E.dlgParte(pid, a) }, 'Editar la parte'))),
      a.resumen ? h('p.entradilla', a.resumen) : null, h('div.e-franja-color'), h('div.filete')));
    // Los capítulos de la parte, en cuadrados que se ordenan arrastrando
    root.append(RV.seccion('Capítulos'));
    const rej = h('div.e-cuadros');
    // (soltar un capítulo sobre otro lo pone delante de él, dentro de esta parte)
    const antesDe = (x, c) => { const otros = lista.filter((y) => y.id !== x.id); const i = otros.findIndex((y) => y.id === c.id); return i > 0 ? otros[i - 1].id : null; };
    for (const c of lista) rej.append(cuadroCapitulo(c, { alSoltar: (x) => { if (x.t === 'c') suelta(() => op('mueveCapitulo', x.id, a.id, antesDe(x, c), c.id)); } }));
    rej.append(cuadroNuevo('Nuevo capítulo', () => E.dlgCapitulo(pid, null, a.id)));
    root.append(rej);
    // Lo vinculado: notas, resúmenes, esquemas y fichas
    root.append(RV.seccion('Notas y fichas', { texto: 'Vincular →', fn: () => dlgVincular(pid, a) }));
    const rv = h('div.e-cuadros.e-cuadros-notas', vin.map((d) => cuadroVinculado(d, pid)));
    rv.append(cuadroNuevo('Nota', () => E.dlgNota(pid, null, 'nota', [a.id])), cuadroNuevo('Resumen', () => E.dlgNota(pid, null, 'resumen', [a.id])), cuadroNuevo('Esquema', () => E.dlgNota(pid, null, 'esquema', [a.id])));
    root.append(rv);
    return root;
  };

  // ---------------------------------------------------------------- La página de un capítulo
  RV.vistas.capitulo = (ruta) => {
    const c = E.get(ruta.id);
    if (!c || c.t !== 'c') { RV.volver() || RV.ir({ v: 'escribir' }); return h('div.hoja'); }
    const pid = c.pid; const obra = E.get(pid) || {};
    const a = c.aid ? E.get(c.aid) : null;
    const esc = E.escenas(c.id);
    const pal = E.palabrasCapitulo(c.id);
    const vin = E.vinculados(c.id, pid);
    const root = h('div.hoja.escribir.e-pagina', { style: { '--c': E.colorDe(c.color, 'var(--tinta)') } });
    root.append(h('header.cabeza',
      h('div.ante', a ? atras(a.titulo || 'La parte', { v: 'parte', id: a.id }) : atras(obra.titulo || 'La obra', { v: 'obra', id: pid, tab: 0 }), ` · Capítulo ${E.numeroCapitulo(c)} · ${RV.plural(esc.length, 'escena', 'escenas')} · ${E.cifra(pal)} palabras`),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, c.titulo || 'Sin título'),
        h('div.herramientas',
          h('button.boton-tinta', { type: 'button', onclick: () => { const s = esc.slice().sort((x, y) => num(y.u) - num(x.u))[0]; if (s) RV.ir({ v: 'editor', id: s.id }); } }, 'Escribir'),
          h('button.enlace', { type: 'button', onclick: () => leerCapitulo(c) }, 'Leerlo entero'),
          h('button.enlace', { type: 'button', onclick: () => E.dlgCapitulo(pid, c) }, 'Editar'))),
      c.resumen ? h('p.entradilla', c.resumen) : null, h('div.e-franja-color'), h('div.filete')));
    root.append(RV.seccion('Escenas'));
    const rej = h('div.e-cuadros');
    esc.forEach((s, i) => {
      const prev = esc[i - 1];
      const el = h('button.e-cuadro.e-cuadro-esc', {
        type: 'button', style: { '--c': E.colorDe(s.color, E.COL_ESTADO[Math.min(2, num(s.estado))]) }, draggable: 'true', 'data-clave': `esc-${s.id}`,
        onclick: () => RV.ir({ v: 'editor', id: s.id }),
        oncontextmenu: (ev) => { ev.preventDefault(); E.menuEscena(s, E.capitulos(pid)); },
        ondragstart: (ev) => { arrastra = { t: 'e', id: s.id }; ev.dataTransfer.effectAllowed = 'move'; },
        ondragover: (ev) => { if (arrastra && arrastra.t === 'e' && arrastra.id !== s.id) { ev.preventDefault(); el.classList.add('sobre'); } },
        ondragleave: () => el.classList.remove('sobre'),
        ondrop: (ev) => { ev.preventDefault(); el.classList.remove('sobre'); const x = arrastra; arrastra = null; if (x && x.t === 'e' && x.id !== s.id) suelta(() => op('mueveEscena', x.id, c.id, prev && prev.id !== x.id ? prev.id : null, s.id)); },
      },
      h('span.e-cuadro-franja'),
      h('span.versal', `${E.ESTADOS[Math.min(2, num(s.estado))]}${s.cuando ? ` · ${s.cuando}` : ''}`),
      h('span.e-cuadro-titulo', s.titulo || 'Sin título'),
      h('span.e-cuadro-texto', s.resumen || primeraLinea(s.texto) || 'Sin escribir todavía.'),
      h('span.e-cuadro-pie', `${E.cifra(num(s.palabras))} palabras${(s.etq || []).length ? ` · ${(s.etq || []).map((x) => `#${x}`).join(' ')}` : ''}`),
      h('span.e-cuadro-mas', { role: 'button', title: 'Más (mover, duplicar, borrar…)', onclick: (ev) => { ev.stopPropagation(); E.menuEscena(s, E.capitulos(pid)); } }, '⋯'));
      rej.append(el);
    });
    rej.append(cuadroNuevo('Nueva escena', async () => { try { const n = await op('nuevo', 'e', pid, { cid: c.id, titulo: `Escena ${esc.length + 1}` }); RV.ir({ v: 'editor', id: n.id }); } catch (e) { E.err(e); } }));
    root.append(rej);
    root.append(RV.seccion('Notas y fichas', { texto: 'Vincular →', fn: () => dlgVincular(pid, c) }));
    const rv = h('div.e-cuadros.e-cuadros-notas', vin.map((d) => cuadroVinculado(d, pid)));
    rv.append(cuadroNuevo('Nota', () => E.dlgNota(pid, null, 'nota', [c.id])), cuadroNuevo('Resumen', () => E.dlgNota(pid, null, 'resumen', [c.id])));
    root.append(rv);
    return root;
  };

  /** Leer el capítulo entero, como un libro (sin poder escribir), con sus escenas una tras otra. */
  function leerCapitulo(c) {
    const esc = E.escenas(c.id);
    const cuerpo = [];
    esc.forEach((s, i) => {
      if (i > 0) cuerpo.push(h('p.e-leer-salto', '* * *'));
      if (s.titulo) cuerpo.push(h('div.versal.apagado', s.titulo));
      for (const l of String(s.texto || '').split('\n')) {
        const t = l.trim();
        if (!t) continue;
        if (t === '***') { cuerpo.push(h('p.e-leer-salto', '* * *')); continue; }
        if (/^#{1,3} /.test(t)) { cuerpo.push(h('h3', t.replace(/^#+ /, ''))); continue; }
        const p = h(t.startsWith('> ') ? 'blockquote' : 'p');
        // (negrita **así** y cursiva *así*, como en el editor)
        for (const tr of t.replace(/^> /, '').split(/(\*\*[^*]+\*\*|\*[^*]+\*)/)) {
          if (!tr) continue;
          if (/^\*\*[^*]+\*\*$/.test(tr)) p.append(h('b', tr.slice(2, -2))); else if (/^\*[^*]+\*$/.test(tr)) p.append(h('i', tr.slice(1, -1))); else p.append(document.createTextNode(tr));
        }
        cuerpo.push(p);
      }
    });
    RV.dialogo({ ante: `Capítulo ${E.numeroCapitulo(c)}`, titulo: c.titulo || 'Sin título', ancho: true, clase: 'e-leer', cuerpo: cuerpo.length ? cuerpo : [h('p.apagado', 'Todavía no hay nada escrito.')],
      acciones: ['hueco', { texto: 'Cerrar', principal: true, fn: (d) => d.cerrar() }] });
  }
  E.leerCapitulo = leerCapitulo;
})(window.RV);
