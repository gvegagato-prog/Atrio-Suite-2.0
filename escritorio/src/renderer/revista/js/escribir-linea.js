'use strict';
/* global window */
// Escribir: la línea temporal. Una línea vertical con lo que pasa en la historia, en el orden en que pasa (no el del
// libro). De cualquier punto sale una rama a un lado: un suceso suelto o una trama entera, con sus sucesos, que baja en
// paralelo y puede volver a la principal. Dónde va cada cosa lo calcula escribir-estructura.js (igual que el móvil).
(function (RV) {
  const h = RV.h;
  const num = RV.num;
  const E = RV.E;
  const op = (...a) => E.op(...a);
  const COL = 30; // ancho de cada columna de líneas (px)
  let arrastra = null;

  // ---------------------------------------------------------------- Diálogos
  /** Un suceso: qué pasa, cuándo (texto libre), en qué línea, la escena donde se cuenta y una nota. */
  E.dlgHito = (pid, hito, nuevo = {}) => {
    const ti = RV.entrada(hito ? hito.titulo : '', { 'data-autofocus': true });
    const cu = RV.entrada(hito ? hito.cuando : '', { placeholder: 'Día 3, primavera del año 12, 1887…' });
    const no = RV.areaTexto(hito ? hito.nota : '', { rows: '5' });
    let eid = hito && hito.eid ? hito.eid : '';
    let rama = hito ? hito.rama || '' : nuevo.rama || '';
    let color = hito ? hito.color || '' : '';
    const es = E.selector(eid, [['', 'Ninguna'], ...E.escenasDe(pid).map((e) => [e.id, e.titulo || 'Sin título'])], (v) => { eid = v; });
    const li = E.selector(rama, [['', 'La línea principal'], ...E.ramas(pid).map((r) => [r.id, `Rama · ${r.titulo || 'Sin título'}`])], (v) => { rama = v; });
    RV.dialogo({
      ante: hito ? 'Editar el suceso' : 'Nuevo suceso', titulo: hito ? hito.titulo : 'Algo que pasa', ancho: true,
      cuerpo: [RV.campo('Qué pasa', ti), RV.campo('Cuándo', cu), RV.campo('En qué línea', li), RV.campo('Qué más', no), RV.campo('Se cuenta en la escena', es), RV.campo('Color', E.colores(color, (c) => { color = c; }))],
      acciones: [hito ? { texto: 'Borrar', peligro: true, fn: (d) => { d.cerrar(); E.aPapelera(hito); } } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: async (d) => {
          if (!ti.value.trim()) { RV.aviso('Cuenta qué pasa.', 'error'); return; }
          try {
            const c = { titulo: ti.value.trim(), cuando: cu.value.trim(), nota: no.value, eid: eid || null, rama: rama || null, color: color || null };
            if (hito) await op('edita', hito.id, c); else await op('nuevo', 'h', pid, { ...c, ...(nuevo.orden != null ? { orden: nuevo.orden } : {}) });
            d.cerrar();
          } catch (e) { E.err(e); }
        } }],
    });
  };

  E.verHito = (pid, x) => {
    const e = x.eid ? E.get(x.eid) : null;
    const r = x.rama ? E.get(x.rama) : null;
    RV.dialogo({
      ante: [r ? `Rama · ${r.titulo || ''}` : 'Línea principal', x.cuando].filter(Boolean).join(' · '), titulo: x.titulo || 'Suceso', ancho: true, clase: 'e-lee',
      cuerpo: [h('span.e-lee-color', { style: { background: E.colorDe(x.color || (r && r.color)) } }), x.nota ? h('div.e-lee-sec', ...E.parrafos(x.nota)) : h('p.apagado', 'Sin más detalles.'),
        e ? h('div.e-lee-sec', h('div.versal', 'Se cuenta en'), h('button.e-chip', { type: 'button', onclick: () => { RV.cerrarDialogo(); RV.ir({ v: 'editor', id: e.id }); } }, e.titulo || 'la escena')) : null],
      acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, { texto: 'Editar', principal: true, fn: (d) => { d.cerrar(); E.dlgHito(pid, x); } }],
    });
  };

  /** Una rama: su nombre, su color, de qué suceso de la principal nace y si vuelve a ella (después de cuál). */
  E.dlgRama = (pid, rama, nueva = {}) => {
    const ti = RV.entrada(rama ? rama.titulo : '', { 'data-autofocus': true, placeholder: 'La huida de Juan, el pasado de Ana…' });
    const de = RV.areaTexto(rama ? rama.texto : '', { rows: '3' });
    let color = rama ? rama.color || RV.COLORES[3] : RV.COLORES[(E.ramas(pid).length + 3) % RV.COLORES.length];
    const principal = E.hitos(pid).filter((x) => !x.rama || !E.get(x.rama));
    let desde = rama ? num(rama.desde) : nueva.desde != null ? num(nueva.desde) : null;
    let hasta = rama && rama.hasta != null && rama.hasta !== '' ? num(rama.hasta) : null;
    const opc = (l) => l.map((x) => [String(num(x.orden)), x.titulo || 'Sin título']);
    const sDesde = E.selector(desde == null ? '' : String(desde), [['', 'Desde el principio'], ...opc(principal)], (v) => { desde = v === '' ? null : num(v); });
    const sHasta = E.selector(hasta == null ? '' : String(hasta), [['', 'No vuelve (acaba en su último suceso)'], ...opc(principal)], (v) => { hasta = v === '' ? null : num(v); });
    RV.dialogo({
      ante: rama ? 'Editar la rama' : 'Nueva rama', titulo: rama ? rama.titulo || 'Rama' : 'Una línea que sale de la principal', ancho: true,
      cuerpo: [RV.campo('Nombre', ti), RV.campo('De qué va', de), RV.campo('Nace después de', sDesde), RV.campo('Vuelve a la principal después de', sHasta), RV.campo('Color', E.colores(color, (c) => { color = c; }))],
      acciones: [rama ? { texto: 'Borrar la rama', peligro: true, fn: (d) => RV.confirmar('¿Borrar la rama?', 'Irá a la papelera con sus sucesos. Podrás recuperarla durante 30 días.', 'A la papelera', async () => { d.cerrar(); await E.aPapelera(rama); }) } : null,
        'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: rama ? 'Guardar' : 'Crear', principal: true, fn: async (d) => {
          try {
            const c = { titulo: ti.value.trim() || 'Rama', texto: de.value, color, desde: desde == null ? (num((principal[0] || {}).orden) - 1) : desde, hasta };
            if (rama) await op('edita', rama.id, c);
            else {
              const r = await op('nuevo', 'r', pid, c);
              d.cerrar();
              // Su primer suceso, justo después de donde nace
              E.dlgHito(pid, null, { rama: r.id, orden: nueva.siguiente != null ? (c.desde + nueva.siguiente) / 2 : c.desde + 500 });
              return;
            }
            d.cerrar();
          } catch (e) { E.err(e); }
        } }],
    });
  };

  // ---------------------------------------------------------------- La línea
  E.linea = (obra) => {
    const pid = obra.id;
    const hs = E.hitos(pid);
    const rs = E.ramas(pid);
    const L = RV.EST.linea(hs, rs);
    const raiz = h('div.e-linea2', { style: { '--cols': String(L.columnas) } });
    raiz.append(h('p.cursiva', 'Lo que pasa en tu historia, en el orden en que pasa. De cualquier suceso puede salir una rama: una trama que baja en paralelo y, si quieres, vuelve a la principal. Arrastra los sucesos para cambiarlos de sitio.'));
    raiz.append(h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: () => E.dlgHito(pid, null, { orden: (hs.reduce((m, x) => Math.max(m, num(x.orden)), 0) || 0) + 1000 }) }, 'Nuevo suceso'),
      h('button.boton-borde', { type: 'button', onclick: () => E.dlgRama(pid, null, { desde: hs.length ? num(hs[hs.length - 1].orden) : 0 }) }, 'Nueva rama')));
    if (!L.filas.length) {
      raiz.append(h('div.e-vacio', h('h2.titular', 'La línea de tu historia'), h('p', 'Apunta lo que pasa (aunque no salga en el libro, o salga en otro orden) y saca ramas para las tramas que van a la vez.')));
      return raiz;
    }
    const porId = new Map([...hs, ...rs].map((x) => [x.id, x]));
    // El color de una columna en una fila: el de la rama que la ocupa ahí (dos ramas que no se pisan comparten columna)
    const colorEn = (c, o) => { if (!c) return 'var(--tinta)'; const r = rs.find((x) => L.tramos[x.id] && L.tramos[x.id].col === c && o >= L.tramos[x.id].ini && o <= L.tramos[x.id].fin); return E.colorDe(r ? r.color : null); };
    // Los sucesos en orden, para poner uno nuevo entre dos y para arrastrar
    const orden = L.filas.map((f) => ({ f, d: porId.get(f.id), o: f.tipo === 'hito' ? num((porId.get(f.id) || {}).orden) : f.tipo === 'inicio' ? L.tramos[f.id].ini : L.tramos[f.id].fin }));
    const ordenTras = (i) => (orden[i + 1] ? (orden[i].o + orden[i + 1].o) / 2 : orden[i].o + 1000);
    // Dónde empieza y acaba cada línea (para cortarla en el punto, no en el borde de la fila)
    const extremo = (c, i, f) => {
      if (f.tipo === 'inicio' && c === f.col) return 'desde-medio';
      if (f.tipo === 'fin' && c === f.col) return 'hasta-medio';
      if (c === 0 && i === 0) return 'desde-medio';
      if (c === 0 && i === orden.length - 1) return 'hasta-medio';
      // una rama que no vuelve acaba en su último suceso
      if (c > 0 && f.tipo === 'hito' && f.col === c && !orden.slice(i + 1).some((x) => x.f.activas.includes(c) && (x.f.tipo !== 'inicio' || x.f.col !== c))) return 'hasta-medio';
      return '';
    };
    const lista = h('div.e-ln');
    orden.forEach(({ f, d, o }, i) => {
      if (!d) return;
      const colorCol = { get: (c) => colorEn(c, o) };
      // Las líneas que cruzan esta fila (y la curva de una rama que nace o vuelve)
      const lineas = h('div.e-ln-lineas', { style: { width: `${L.columnas * COL}px` } },
        f.activas.map((c) => h(`i.e-ln-v${extremo(c, i, f) ? `.${extremo(c, i, f)}` : ''}`, {
          style: { left: `${c * COL + COL / 2 - 1}px`, background: colorCol.get(c) || 'var(--tinta)' } })),
        f.tipo !== 'hito' ? h('i.e-ln-h', { style: { left: `${COL / 2}px`, width: `${f.col * COL}px`, background: colorCol.get(f.col) } }) : null,
        h(`i.e-ln-punto${f.tipo === 'hito' ? '' : '.e-ln-nudo'}`, { style: { left: `${f.col * COL + COL / 2 - 6}px`, background: f.tipo === 'hito' ? E.colorDe(d.color || (d.rama && porId.get(d.rama) && porId.get(d.rama).color), f.col ? colorCol.get(f.col) : 'var(--tinta)') : colorCol.get(f.col) } }));
      let tarjeta;
      if (f.tipo === 'hito') {
        const e = d.eid ? E.get(d.eid) : null;
        tarjeta = h('div.e-ln-tarj', {
          draggable: 'true',
          ondragstart: (ev) => { arrastra = d.id; ev.dataTransfer.effectAllowed = 'move'; },
          ondragover: (ev) => { if (arrastra && arrastra !== d.id) { ev.preventDefault(); fila.classList.add('sobre'); } },
          ondragleave: () => fila.classList.remove('sobre'),
          ondrop: (ev) => {
            ev.preventDefault(); fila.classList.remove('sobre');
            const x = arrastra; arrastra = null; if (!x || x === d.id) return;
            // Se pone justo antes de este, en su misma línea
            const prev = orden.slice(0, i).reverse().find((y) => y.f.id !== x);
            op('edita', x, { orden: prev ? (prev.o + num(d.orden)) / 2 : num(d.orden) - 1000, rama: d.rama || null }).catch(E.err);
          },
        },
        h('button.e-ln-cuerpo', { type: 'button', onclick: () => E.verHito(pid, d) },
          d.cuando ? h('span.versal.acento', d.cuando) : null, h('span.e-ln-titulo', d.titulo || 'Sin título'),
          d.nota ? h('span.e-ln-nota', String(d.nota).split('\n')[0].slice(0, 180)) : null,
          e ? h('span.e-ln-esc', `→ ${e.titulo || 'escena'}`) : null),
        h('div.e-ln-mas',
          h('button.enlace', { type: 'button', title: 'Un suceso justo después, en esta misma línea', onclick: () => E.dlgHito(pid, null, { rama: d.rama || '', orden: ordenTras(i) }) }, '+ Suceso'),
          h('button.enlace', { type: 'button', title: 'Una rama que nace aquí', onclick: () => E.dlgRama(pid, null, { desde: num(d.orden), siguiente: orden[i + 1] ? orden[i + 1].o : null }) }, '+ Rama desde aquí')));
      } else {
        tarjeta = h('button.e-ln-rama', { type: 'button', style: { '--c': colorCol.get(f.col) }, onclick: () => E.dlgRama(pid, d) },
          h('span.versal', f.tipo === 'inicio' ? 'Nace la rama' : 'Vuelve a la principal'), h('span.e-ln-titulo', d.titulo || 'Rama'),
          f.tipo === 'inicio' && d.texto ? h('span.e-ln-nota', String(d.texto).split('\n')[0].slice(0, 160)) : null);
      }
      const fila = h(`div.e-ln-fila.${f.tipo}`, lineas, tarjeta);
      lista.append(fila);
    });
    raiz.append(lista);
    // Las escenas con fecha (lo que se ponía antes en «cuándo pasa» del panel de la escena)
    const conFecha = E.escenasDe(pid).filter((e) => e.cuando);
    if (conFecha.length) {
      raiz.append(RV.seccion('Escenas con fecha'));
      for (const e of conFecha) raiz.append(h('button.e-fecha', { type: 'button', onclick: () => RV.ir({ v: 'editor', id: e.id }) }, h('span.versal.acento', e.cuando), h('span.titulo', e.titulo || 'Sin título')));
    }
    return raiz;
  };
})(window.RV);
