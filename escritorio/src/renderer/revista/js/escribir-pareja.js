'use strict';
/* global window, document */
// Escribir con tu pareja (igual que EscribirParejaUi.kt en el móvil): «Terminado» y «Compartir con …» en capítulos y
// partes, lo que te anota tu pareja en cada capítulo, y la página «De …» para leer y anotar lo que te comparte.
// Los datos van por escritos-pareja.js (proceso principal).
(function (RV) {
  const h = RV.h;
  const E = RV.E;
  const api = RV.api;
  const num = RV.num;
  const op = (...a) => E.op(...a);
  const hay = () => !!(api && api.parejaInfo);

  // ---------------------------------------------------------------- Quién es tu pareja (se mira de vez en cuando)
  let info = { hay: false, nombre: 'tu pareja' };
  let mirada = 0;
  async function mira(forzar) {
    if (!hay() || (!forzar && Date.now() - mirada < 60_000)) return info;
    mirada = Date.now();
    try { const i = await api.parejaInfo(); if (i && (i.hay !== info.hay || i.nombre !== info.nombre)) { info = i; RV.refrescar(); } } catch { /* sin conexión */ }
    return info;
  }
  E.pareja = () => info;

  /** Los párrafos de una escena tal como se leen (sin vacíos ni «***»): lo que se puede anotar. */
  const parrafos = (t) => String(t || '').split('\n').map((l) => l.trim()).filter((l) => l && l !== '***');
  /** El principio de un párrafo, sin formato, para citarlo. */
  const cita = (p) => String(p || '').replace(/^(#{1,3} |> )/, '').replace(/\*\*/g, '').replace(/\*/g, '').slice(0, 160);
  E.parrafosPareja = parrafos;
  E.citaPareja = cita;
  const b = (o, k) => !!o && (o[k] === 1 || o[k] === true);
  E.compartido = (c) => b(c, 'comp') || b(c && c.aid ? E.get(c.aid) : null, 'comp');
  E.hecho = (c) => b(c, 'hecho') || b(c && c.aid ? E.get(c.aid) : null, 'hecho');

  /** Un párrafo con su formato (negrita y cursiva), como en «Leerlo entero». */
  function parrafo(t) {
    if (/^#{1,3} /.test(t)) return h('h3', t.replace(/^#+ /, ''));
    const p = h(t.startsWith('> ') ? 'blockquote' : 'p');
    for (const tr of t.replace(/^> /, '').split(/(\*\*[^*]+\*\*|\*[^*]+\*)/)) {
      if (!tr) continue;
      if (/^\*\*[^*]+\*\*$/.test(tr)) p.append(h('b', tr.slice(2, -2))); else if (/^\*[^*]+\*$/.test(tr)) p.append(h('i', tr.slice(1, -1))); else p.append(document.createTextNode(tr));
    }
    return p;
  }

  // ---------------------------------------------------------------- Tú, al escribir
  /** «Terminado» y «Compartir con …» de un capítulo o una parte. */
  function marcas(d) {
    mira();
    const parte = d.t === 'c' && d.aid ? E.get(d.aid) : null;
    const cambia = async (k, on) => { try { await op('edita', d.id, { [k]: on ? 1 : null }); } catch (e) { E.err(e); } };
    const hecho = b(d, 'hecho'); const comp = b(d, 'comp');
    let nota = null;
    if (b(parte, 'comp') && !comp) nota = `Compartido con ${info.nombre} porque lo está la parte.`;
    else if (b(parte, 'hecho') && !hecho) nota = 'Terminado porque lo está la parte.';
    else if (d.t === 'a' && comp) nota = `${info.nombre} puede leer todos los capítulos de esta parte (también los que añadas) y anotarlos.`;
    else if (comp) nota = `${info.nombre} puede leerlo en su Atrio y anotarlo.`;
    else if (!info.hay) nota = 'Para compartirlo, enlaza tu cuenta con la de tu pareja (en el móvil: Ajustes → Cuenta y pareja).';
    return h('div.e-marcas',
      h('div.opciones',
        h('button.opcion', { type: 'button', 'aria-pressed': String(hecho), onclick: () => cambia('hecho', !hecho) }, hecho ? '✓ Terminado' : 'Marcar como terminado'),
        info.hay ? h('button.opcion', { type: 'button', 'aria-pressed': String(comp), onclick: () => cambia('comp', !comp) }, comp ? `Compartido con ${info.nombre}` : `Compartir con ${info.nombre}`) : null),
      nota ? h('p.nota', nota) : null);
  }
  E.marcasCompartir = marcas;

  /** Lo que te ha anotado tu pareja en un capítulo (se rellena al llegar). */
  function notasDeTuPareja(cid) {
    const caja = h('section.e-notas-pareja');
    if (!hay()) return caja;
    api.parejaNotasDeMiPareja().then((l) => {
      const ns = (l || []).filter((n) => n.cid === cid);
      if (!ns.length) return;
      caja.append(RV.seccion(`Lo que te anota ${info.nombre} · ${ns.length}`));
      for (const n of ns) {
        const s = E.get(n.eid);
        caja.append(h('button.e-nota-pareja', { type: 'button', onclick: () => { if (s) RV.ir({ v: 'editor', id: s.id }); } },
          n.cita ? h('span.e-cita', `«${n.cita}»`) : null,
          h('span.e-nota-texto', n.texto),
          h('span.versal.apagado', [s && s.titulo, RV.hace ? RV.hace(n.u) : ''].filter(Boolean).join(' · '))));
      }
    }).catch(() => {});
    return caja;
  }
  E.notasDeTuPareja = notasDeTuPareja;

  // (las páginas del capítulo y de la parte llevan las marcas bajo la cabecera; el capítulo, además, las notas)
  const conMarcas = (vista, conNotas) => (ruta) => {
    const root = vista(ruta);
    const d = E.get(ruta.id);
    if (!d) return root;
    const cab = root.querySelector('header.cabeza');
    const m = marcas(d);
    if (cab) cab.after(m); else root.prepend(m);
    if (conNotas) root.append(notasDeTuPareja(d.id));
    return root;
  };
  RV.vistas.capitulo = conMarcas(RV.vistas.capitulo, true);
  RV.vistas.parte = conMarcas(RV.vistas.parte, false);

  // ---------------------------------------------------------------- En tus obras: lo que te comparte
  let compartido = { docs: [], nombre: 'tu pareja', cargado: false, cuando: 0 };
  async function bajaCompartido(forzar) {
    if (!hay() || (!forzar && Date.now() - compartido.cuando < 30_000)) return compartido;
    compartido.cuando = Date.now();
    try {
      const r = await api.parejaCompartido();
      const antes = JSON.stringify(compartido.docs);
      compartido = { docs: (r && r.docs) || [], nombre: (r && r.nombre) || info.nombre, cargado: true, cuando: Date.now() };
      if (JSON.stringify(compartido.docs) !== antes || forzar) RV.refrescar();
    } catch { compartido.cargado = true; }
    return compartido;
  }
  const de = (t) => compartido.docs.filter((o) => o.t === t);
  const ordenDoc = (a, x) => (num(a.orden) - num(x.orden)) || (num(a.u) - num(x.u));
  const capsDe = (pid) => RV.EST.estructura(de('c').filter((c) => c.pid === pid), de('a').filter((a) => a.pid === pid)).libro;
  const escenasDe = (cid) => de('e').filter((s) => s.cid === cid).sort(ordenDoc);
  const getP = (id) => compartido.docs.find((o) => o.id === id) || null;

  const vistaEscribir = RV.vistas.escribir;
  RV.vistas.escribir = (ruta) => {
    const root = vistaEscribir(ruta);
    mira(); bajaCompartido();
    const caps = de('c');
    if (!caps.length) return root;
    const tarjeta = h('button.e-te-comparte', { type: 'button', onclick: () => RV.ir({ v: 'pareja' }) },
      h('span.versal.acento', `Te comparte ${compartido.nombre}`),
      h('span.titular', de('p').map((o) => o.titulo || 'Sin título').join(' · ')),
      h('span.apagado', `${RV.plural(caps.length, 'capítulo', 'capítulos')} para leer y anotar →`));
    const obras = root.querySelector('.e-obras, .e-vacio');
    if (obras) obras.before(tarjeta); else root.append(tarjeta);
    return root;
  };

  // ---------------------------------------------------------------- Tu pareja, al leer: «De …»
  let misNotas = [];
  RV.vistas.pareja = () => {
    const root = h('div.hoja.escribir.e-pareja');
    root.append(h('header.cabeza',
      h('div.ante', h('button.enlace.e-atras', { type: 'button', onclick: () => RV.ir({ v: 'escribir' }) }, '← Escribir')),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, `De ${compartido.nombre}`),
        h('div.herramientas', h('button.enlace', { type: 'button', onclick: async () => { await bajaCompartido(true); try { misNotas = await api.parejaMisNotas(); } catch { /* */ } RV.refrescar(); } }, 'Actualizar'))),
      h('div.filete')));
    if (!compartido.cargado) { bajaCompartido(true); root.append(RV.vacio('Cargando…')); return root; }
    const obras = de('p');
    if (!obras.length) { root.append(RV.vacio(`${compartido.nombre} aún no te ha compartido nada.`)); return root; }
    for (const o of obras) {
      root.append(h('h2.titular.e-pareja-obra', o.titulo || 'Sin título'));
      if (o.sinopsis) root.append(h('p.entradilla', o.sinopsis));
      const rej = h('div.e-cuadros');
      capsDe(o.id).forEach((c, i) => {
        const esc = escenasDe(c.id);
        const parte = c.aid ? getP(c.aid) : null;
        const hecho = b(c, 'hecho') || b(parte, 'hecho');
        const mias = misNotas.filter((n) => n.cid === c.id).length;
        const pal = esc.reduce((s, e) => s + num(e.palabras), 0);
        rej.append(h('button.e-cuadro.e-cuadro-cap', { type: 'button', style: { '--c': E.colorDe(c.color, 'var(--tinta)') }, onclick: () => leerYAnotar(c) },
          h('span.e-cuadro-franja'),
          h('span.versal', [parte && parte.titulo, `Capítulo ${i + 1}`, hecho ? 'Terminado' : 'En marcha'].filter(Boolean).join(' · ')),
          h('span.e-cuadro-titulo', c.titulo || 'Sin título'),
          h('span.e-cuadro-texto', c.resumen || parrafos((esc[0] || {}).texto)[0] || 'Sin escribir todavía.'),
          h('span.e-cuadro-pie', `${E.cifra(pal)} palabras${mias ? ` · ${RV.plural(mias, 'nota tuya', 'notas tuyas')}` : ''}`)));
      });
      root.append(rej);
    }
    if (hay() && !RV.vistas.pareja.notasPedidas) { RV.vistas.pareja.notasPedidas = true; api.parejaMisNotas().then((l) => { misNotas = l || []; RV.refrescar(); }).catch(() => {}); }
    return root;
  };

  /** Leer un capítulo que te comparten y anotar sus párrafos (clic en un párrafo). */
  function leerYAnotar(c) {
    let dlg = null;
    const pinta = () => {
      const cuerpo = [h('p.nota', `Haz clic en un párrafo para anotarlo. Tus notas las ve ${compartido.nombre} en su Atrio.`)];
      escenasDe(c.id).forEach((s, i) => {
        if (i > 0) cuerpo.push(h('p.e-leer-salto', '* * *'));
        if (s.titulo) cuerpo.push(h('div.versal.apagado', s.titulo));
        parrafos(s.texto).forEach((t, p) => {
          const el = parrafo(t);
          el.classList.add('e-anotable');
          el.title = 'Anotar este párrafo';
          el.addEventListener('click', () => anotar(c, s, p, t, null, pinta));
          cuerpo.push(el);
          for (const n of misNotas.filter((x) => x.eid === s.id && x.p === p)) {
            cuerpo.push(h('button.e-mi-nota', { type: 'button', onclick: () => anotar(c, s, p, t, n, pinta) }, h('span.versal.acento', 'Tu nota'), h('span', n.texto)));
          }
        });
      });
      if (dlg && dlg.cuerpo) { dlg.cuerpo.replaceChildren(...cuerpo); return; }
      dlg = RV.dialogo({ ante: `De ${compartido.nombre}`, titulo: c.titulo || 'Sin título', ancho: true, clase: 'e-leer.e-leer-pareja', cuerpo,
        acciones: ['hueco', { texto: 'Cerrar', principal: true, fn: (d) => { d.cerrar(); RV.refrescar(); } }] });
    };
    pinta();
  }

  /** Escribir (o cambiar, o borrar) tu nota a un párrafo. */
  function anotar(c, s, p, t, n, despues) {
    const campo = h('textarea.entrada', { rows: '5', placeholder: 'Tu nota', 'aria-label': 'Tu nota' });
    campo.value = n ? n.texto : '';
    RV.dialogo({ ante: n ? 'Tu nota' : 'Anotar', titulo: `«${cita(t)}»`, clase: 'e-anotar', cuerpo: [campo],
      acciones: [
        n ? { texto: 'Borrar', peligro: true, fn: async (d) => { try { await api.parejaBorraNota(n.id); misNotas = misNotas.filter((x) => x.id !== n.id); d.cerrar(); despues(); } catch (e) { E.err(e); } } } : null,
        'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: async (d) => {
          const texto = campo.value.trim();
          if (!texto) return;
          try {
            const id = n ? n.id : `nl_${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36)}`;
            const g = await api.parejaAnota({ id, cid: c.id, eid: s.id, p, cita: cita(t), texto });
            misNotas = misNotas.filter((x) => x.id !== g.id).concat(g);
            d.cerrar(); despues();
          } catch (e) { E.err(e); }
        } }].filter(Boolean) });
    setTimeout(() => campo.focus(), 50);
  }
})(window.RV);
