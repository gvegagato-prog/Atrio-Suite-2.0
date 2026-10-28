'use strict';
/* global window */
// La ficha, maquetada en casillas como una revista (Revista.kt y Maqueta.kt del móvil): titular enorme,
// antetítulo, entradilla en cursiva y filas de casillas separadas por filetes (Nota, Opinión, Datos, Apuntes,
// Apuntes rápidos, Horas, Partes, Jugar en el PC, Capturas). Todas las ediciones van por atrio.ronronEdit.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const num = RV.num;
  RV.vistas = RV.vistas || {};
  const F = {};
  RV.ficha = F;

  // Lo último de una ficha (por si ha cambiado desde que se abrió un diálogo: otro dispositivo, una partida…)
  const fresca = (kind, t, item) => D.buscar(kind, t, item.id) || item;
  const palabraEmpezar = (k) => (k.isGame ? 'Rejugar' : k.isBook ? 'Releer' : 'Volver a empezar');

  // ---------------------------------------------------------------- Casillas
  function fila(celdas, cols, alto) {
    const cs = celdas.filter(Boolean);
    if (!cs.length) return null;
    const cls = cs.length === 1 ? 'c1' : cs.length === 2 ? 'c2' : 'c3';
    const style = cs.length === 2 && cols ? { '--a': `${cols[0]}fr`, '--b': `${cols[1]}fr` } : {};
    if (alto > 0) style.height = `${alto}px`;
    return h(`div.fila-cas.${cls}${alto > 0 ? '.fija' : ''}`, { style }, cs);
  }
  const casilla = (etq, accion, ...contenido) => h('section.casilla', RV.etiqueta(etq, accion), contenido);

  // Nota: la cifra gigante y los criterios con puntos guía
  function bloqueNota(kind, o, pie) {
    const v = D.notas(o);
    const out = [h('div.nota-gigante', h('span.cifra', String(v[0])), h('span.de', '/100'))];
    if (pie) out.push(h('span.versal', { style: { display: 'block', marginTop: '6px' } }, pie));
    if (kind.crit >= 1) {
      const ul = h('ul.datos');
      for (let i = 1; i <= kind.crit; i++) ul.append(RV.dato(kind.labels[i] || '', String(v[i]), true));
      out.push(ul);
    }
    return out;
  }

  // Texto con capitular: párrafos separados por líneas en blanco; si es largo, a dos columnas
  function cuerpo(texto) {
    const t = String(texto || '').trim();
    const ps = t.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
    return h(`div.cuerpo${t.length > 1100 ? '.columnas' : ''}`, ps.map((p) => h('p', p)));
  }

  function datos(kind, item, t) {
    const ul = h('ul.datos');
    const add = (k, v) => { if (v !== '' && v != null) ul.append(RV.dato(k, String(v))); };
    add(kind.creatorWord, item.creator);
    add(kind.platformWord, item.platform);
    if (t === 'p') add('Estado', D.estado(kind, item, t));
    if (kind.isBook && num(item.pages) > 0) add('Páginas', num(item.pages));
    if (kind.isBook && t === 'p' && num(item.chapter) > 0) add('Capítulo', num(item.chapter));
    add('Empezado', RV.fmtDia(num(item.start)));
    if (t === 'g') add('Terminado', RV.fmtDia(num(item.end)));
    const parts = item.parts || [];
    if (parts.length) add('Capítulos hechos', `${parts.filter((x) => x && (x.d || x.done)).length} de ${parts.length}`);
    if (t === 'p') { const pct = D.porcentaje(item); if (pct !== null) add('Hecho', `${pct} %`); }
    return ul.children.length ? ul : RV.vacio('Sin datos.');
  }

  function horas(kind, item, pcg) {
    const out = [];
    const mine = num(item.minutes);
    if (mine > 0) {
      const [a, b] = RV.horasPartes(mine);
      out.push(h('div.horas-gigantes', a, b ? h('small', b) : null), h('span.versal', 'Tus horas'));
    }
    const ul = h('ul.datos');
    if (num(item.ttbMain) > 0) ul.append(RV.dato('Historia', RV.horas(item.ttbMain)));
    if (num(item.ttbExtra) > 0) ul.append(RV.dato('+ Extras', RV.horas(item.ttbExtra)));
    if (num(item.ttbFull) > 0) ul.append(RV.dato('Completo', RV.horas(item.ttbFull)));
    if (pcg) { const ms = D.msPc(pcg); if (ms >= 60000) ul.append(RV.dato('En tus PC', RV.horas(ms / 60000))); }
    if (ul.children.length) out.push(ul);
    if (item.ttbSrc && (num(item.ttbMain) || num(item.ttbExtra) || num(item.ttbFull))) out.push(h('span.versal', { style: { display: 'block', marginTop: '8px' } }, `Duración media según ${item.ttbSrc}`));
    if (!out.length) out.push(RV.vacio('Sin horas todavía.'));
    return out;
  }

  function enElPc(pcg) {
    if (!pcg) return [RV.vacio('No está instalado en este PC (o tiene otro nombre en tu biblioteca).')];
    const sub = [pcg.sourceLabel, pcg.lastPlayed ? `jugado ${RV.hace(pcg.lastPlayed)}` : 'sin jugar desde Atrio'].filter(Boolean).join(' · ');
    return [h('div.en-pc',
      h('div.nombre', pcg.name),
      h('span.versal', sub),
      D.activoPc(pcg.key) ? h('p.ante', 'Abierto ahora mismo') : h('button.jugar', { type: 'button', onclick: () => RV.jugar(pcg) }, 'Jugar en el PC →'))];
  }

  // Apuntes con nota
  function apuntes(kind, item) {
    const ul = h('ul.apuntes.altos');
    const es = [...(item.entries || [])].sort((a, b) => num(b.time) - num(a.time));
    for (const e of es) {
      const extra = kind.isBook && num(e.chapter) > 0 ? ` · capítulo ${e.chapter}` : '';
      ul.append(h('li', h('button.ap', { type: 'button', onclick: () => F.apuntar(kind, item, { entry: e }) },
        h('span', h('span.cuando', `${RV.fmtMomento(num(e.time))}${extra}`),
          e.note && e.note.trim() ? h('span.texto', { style: { display: 'block' } }, e.note.trim()) : h('span.cursiva', { style: { display: 'block' } }, 'Solo notas, sin texto.')),
        h('span.cifra', String(num(e.general, 50))))));
    }
    if (!ul.children.length) return RV.vacio('Aún no has escrito nada.');
    return ul;
  }

  function rapidos(kind, item) {
    const ms = D.memos(item).sort((a, b) => num(b.t) - num(a.t));
    if (!ms.length) return RV.vacio('Ninguna idea suelta todavía.');
    const ul = h('ul.apuntes.rapidos.altos');
    for (const m of ms) {
      ul.append(h('li', h('button.ap', { type: 'button', onclick: () => F.rapido(kind, item, m) },
        h('span', h('span.cuando', `${RV.fmtMomento(num(m.t))}${m.sh ? ' · con captura' : ''}`),
          m.x && m.x.trim() ? h('span.texto', { style: { display: 'block' } }, m.x.trim()) : h('span.cursiva', { style: { display: 'block' } }, 'Sin texto.')),
        h('span'))));
    }
    return ul;
  }

  // Capturas del juego en este PC: se piden una vez y se guardan, para no parpadear al refrescar
  const capturas = new Map(); // clave del juego -> lista
  RV.olvidarCapturas = () => capturas.clear();
  function bloqueCapturas(pcg) {
    const box = h('div');
    const pintar = (list) => {
      if (!list || !list.length) { box.replaceChildren(RV.vacio('Aún no hay capturas de este juego. Se hacen desde el móvil o desde el lanzador.')); return; }
      const grid = h('div.capturas');
      list.forEach((s, i) => {
        const img = h('img', { alt: `Captura de ${pcg.name}`, loading: 'lazy', decoding: 'async', draggable: 'false' });
        img.addEventListener('error', () => { const w = img.closest('.capturas > div'); if (w) w.remove(); });
        img.src = s.url;
        grid.append(h('div', h('button', { type: 'button', onclick: () => visor(pcg, list, i) }, img),
          h('span.versal', s.t ? RV.fmtMomento(s.t) : '')));
      });
      box.replaceChildren(grid);
    };
    if (capturas.has(pcg.key)) pintar(capturas.get(pcg.key)); else box.append(h('p.vacio', 'Buscando capturas…'));
    RV.api.suiteShots(pcg.key).then((list) => {
      const l = Array.isArray(list) ? list : [];
      const antes = JSON.stringify(capturas.get(pcg.key) || null);
      capturas.set(pcg.key, l);
      if (JSON.stringify(l) !== antes || !box.firstChild || box.firstChild.classList.contains('vacio')) pintar(l);
    }).catch(() => { if (!capturas.has(pcg.key)) box.replaceChildren(RV.vacio('No he podido leer las capturas.')); });
    return box;
  }
  function visor(pcg, list, i0) {
    let i = i0;
    const img = h('img', { alt: '' });
    const pie = h('span.versal');
    const pintar = () => { const s = list[i]; img.src = s.url; pie.textContent = `${i + 1} de ${list.length}${s.t ? ` · ${RV.fmtMomento(s.t)}` : ''}`; };
    const capa = h('div.visor', { role: 'dialog', 'aria-label': `Capturas de ${pcg.name}` },
      h('div.barra-visor',
        h('span.ante.tinta', pcg.name), pie, h('span.hueco'),
        h('button.enlace', { type: 'button', onclick: () => { i = (i - 1 + list.length) % list.length; pintar(); } }, '← Anterior'),
        h('button.enlace', { type: 'button', onclick: () => { i = (i + 1) % list.length; pintar(); } }, 'Siguiente →'),
        h('button.enlace', { type: 'button', onclick: () => RV.api.suiteShotShow(list[i].id) }, 'Ver en la carpeta'),
        h('button.enlace.acento', { type: 'button', onclick: () => cerrar() }, 'Cerrar ✕')),
      img);
    const teclas = (e) => {
      if (e.key === 'ArrowLeft') { i = (i - 1 + list.length) % list.length; pintar(); }
      if (e.key === 'ArrowRight') { i = (i + 1) % list.length; pintar(); }
    };
    const cerrar = RV.superponer(capa, () => window.removeEventListener('keydown', teclas));
    window.addEventListener('keydown', teclas);
    pintar();
  }

  // ---------------------------------------------------------------- La maqueta de la ficha (la del móvil)
  // Lo que cada ficha tiene de maqueta (qué casilla va en cada fila, cuánto miden, sus textos y sus fotos) viaja con la
  // cuenta, y aquí se pinta igual. Las casillas que el ordenador todavía no tiene (Guías, Crítica…) no se pintan y se avisa.
  const NO_HAY = { critica: 'Crítica', donde: 'Dónde verla', lectura: 'Lectura', subrayados: 'Subrayados y notas' };

  // «El nombre del viento (Crónica del asesino de reyes, #1)» → «El nombre del viento»
  const titLimpio = (t) => String(t || '').replace(/\s*\([^()]*\)\s*$/, '').trim() || String(t || '');
  const abreFuera = (url) => { try { RV.api.openExternal(url); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); } };
  // Un enlace de la casilla Enlaces: texto, lo de abajo en versalitas y lo que hace
  const enlaceMaq = (texto, sub, fn, acento) => h(`button.enlace-maq${acento ? '.acento' : ''}`, { type: 'button', onclick: fn }, h('span.tit', texto), sub ? h('span.versal', sub) : null);
  const refDe = (kind, item) => `${kind.id}:${item.id}`;
  const diarioDe = (kind, item) => (RV.diario ? RV.diario.notas() : []).filter((n) => (n.sobre || []).map(String).includes(refDe(kind, item)))
    .sort((a, b) => (num(b.dia) - num(a.dia)) || (num(b.id) - num(a.id)));
  const fmtDiario = (dia) => { const t = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(num(dia) * RV.DIA)); return t.charAt(0).toUpperCase() + t.slice(1); };

  // Lo que ha escrito el diario de esta ficha (las 4 últimas; el resto, en el diario)
  function diarioFicha(kind, item) {
    const ns = diarioDe(kind, item);
    const box = h('div.maq-diario', ns.slice(0, 4).map((n) => h('button.maq-nota-diario', { type: 'button', onclick: () => RV.diario.escribir(num(n.dia), n) },
      h('span.versal', fmtDiario(n.dia)), n.x && n.x.trim() ? h('span.texto', n.x.trim().length > 320 ? `${n.x.trim().slice(0, 318).trimEnd()}…` : n.x.trim()) : h('span.cursiva', 'Una foto.'))));
    if (ns.length > 4) box.append(h('span.versal', `y ${ns.length - 4} más en tu diario`));
    return box;
  }

  // Capítulos (libros que se leen): el capítulo en el que vas y cuántos llevas sin apuntar
  function capitulosFicha(kind, item) {
    if (num(item.status) !== 1) {
      return [RV.vacio(`Márcalo como «${kind.statuses[1]}» para llevar la cuenta.`), h('button.enlace.acento', { type: 'button', onclick: () => F.estado(kind, item, 1) }, `Marcar «${kind.statuses[1]}»`)];
    }
    const cap = num(item.chapter);
    const pend = cap - (item.entries || []).reduce((m, e) => Math.max(m, num(e.chapter)), 0);
    const vence = num(item.every) > 0 && pend >= num(item.every);
    return [
      h('div.cap-gigante', cap > 0 ? String(cap) : '—'), h('span.versal', 'Capítulo actual'),
      h('div.cap-botones', h('button.enlace', { type: 'button', onclick: () => F.capitulo(kind, item, -1) }, '− 1'), h('button.enlace.acento', { type: 'button', onclick: () => F.capitulo(kind, item, 1) }, '+ 1')),
      h(`p.texto${vence ? '.acento' : ''}`, vence ? `Llevas ${RV.plural(pend, 'capítulo', 'capítulos')} sin apuntar.` : `Apuntas cada ${num(item.every) || 3} cap.`),
    ];
  }

  // Progreso (solo lectura, como en el móvil): partes hechas o, si no las hay, las horas frente a lo que dura
  function progresoFicha(kind, item) {
    const parts = item.parts || [];
    const hechas = parts.filter((x) => x && (x.d || x.done)).length;
    const pct = D.porcentaje(item);
    const out = [h('div.versal', 'Progreso'),
      h('div.maq-progreso', h('span.texto', parts.length ? `${hechas} de ${parts.length} completados` : pct !== null ? 'Por horas jugadas' : 'Sin capítulos'),
        pct !== null ? h('span.cifra-prog', `${pct} %`) : null)];
    if (pct !== null) out.push(h('div.maq-barra', h('i', { style: { width: `${pct}%` } })));
    const ul = h('ul.datos');
    if (num(item.ttbMain) > 0) ul.append(RV.dato('Historia', RV.horas(num(item.ttbMain))));
    if (num(item.ttbExtra) > 0) ul.append(RV.dato('+ Extras', RV.horas(num(item.ttbExtra))));
    if (num(item.ttbFull) > 0) ul.append(RV.dato('Completo', RV.horas(num(item.ttbFull))));
    if (ul.children.length) out.push(ul);
    if (num(item.minutes) > 0 && num(item.ttbMain) > 0) {
      const queda = num(item.ttbMain) - num(item.minutes);
      out.push(h('p.texto', `Lleva ${RV.horas(num(item.minutes))} de unas ${RV.horas(num(item.ttbMain))}${queda > 0 ? ` · le quedan ~${RV.horas(queda)}` : ' · ya debería estar acabando'}`));
    }
    return out;
  }

  // Guías (videojuegos): las del juego en la biblioteca de este PC, si está, y buscar guías en el navegador
  function guiasFicha(kind, item, pcg) {
    const links = pcg ? (pcg.links || []) : [];
    const out = links.map((l) => enlaceMaq(l.n || l.u, (() => { try { return new URL(l.u).hostname.replace(/^www\./, ''); } catch { return ''; } })(), () => RV.guia(pcg.key, l.u)));
    if (!out.length) out.push(RV.vacio('Ninguna todavía.'));
    out.push(h('button.enlace.acento', { type: 'button', onclick: () => abreFuera(`https://duckduckgo.com/?q=${encodeURIComponent(`${titLimpio(item.name)} guía`)}`) }, 'Buscar guías →'));
    if (!pcg) out.push(h('p.versal', 'Las guías que guardas en el móvil se ven en el móvil.'));
    return out;
  }


  // Una imagen encuadrada (zoom y posición, como en el móvil): se dibuja entera a su tamaño de «llenar» y la casilla la recorta
  function casillaImagen(c, fija) {
    const url = D.imagen(c.imagen);
    const caja = h('div.maq-img');
    const sec = h('section.casilla.maq', c.titulo ? RV.etiqueta(c.titulo) : null, caja);
    if (!url) {
      // Una foto de la galería del móvil que aún no está aquí: se le puede pedir (llega tal cual, en su calidad original)
      caja.classList.add('sin-foto');
      const nombre = (/([^/\\]+)$/.exec(c.imagen) || [])[1];
      const pedir = D.fotoDe(c.imagen) && nombre && RV.api && RV.api.fotoFetch ? h('button.enlace.acento', {
        type: 'button',
        onclick: async (e) => {
          const b = e.currentTarget;
          try {
            const tel = await RV.api.fotoPhones();
            if (!tel || !tel.length) { RV.aviso('No veo el móvil: ábrelo con tu cuenta y vuelve a probar.', 'error'); return; }
            b.textContent = 'Pidiéndola al móvil…';
            const r = await RV.api.fotoFetch(tel[0].dev, nombre, null);
            if (r && r.names) D.setFotos(r.names);
            RV.refrescar();
          } catch (err) { RV.aviso(RV.limpiaError(err), 'error'); b.textContent = 'Traerla del móvil →'; }
        },
      }, 'Traerla del móvil →') : null;
      caja.append(h('span.versal', 'Foto de otro dispositivo: aún no se ha bajado'), pedir);
      caja.style.aspectRatio = fija ? '' : String(c.asp > 0 ? c.asp : 1.2);
      return sec;
    }
    const img = h('img', { alt: '', draggable: 'false' });
    caja.append(img);
    if (!fija) caja.style.aspectRatio = String(c.asp > 0 ? c.asp : 1.2);
    const colocar = () => {
      const bw = caja.clientWidth; const bh = caja.clientHeight;
      if (!bw || !bh || !img.naturalWidth) return;
      const p = RV.M.lugar(c, bw, bh, img.naturalWidth / img.naturalHeight);
      img.style.width = `${p.dw}px`; img.style.height = `${p.dh}px`;
      img.style.transform = `translate(-50%, -50%) translate(${p.tx}px, ${p.ty}px) scale(${p.z})`;
    };
    img.onload = () => {
      // Una imagen de una versión anterior (sin forma guardada): la forma de la foto
      if (!fija && !(c.asp > 0)) caja.style.aspectRatio = String(img.naturalWidth / img.naturalHeight);
      colocar();
    };
    img.src = url;
    if (typeof ResizeObserver === 'function') new ResizeObserver(colocar).observe(caja);
    return sec;
  }

  function pintaMaqueta(cas, filas, kind, item, t, pcg) {
    const g = t === 'g';
    const b = !g ? D.baremo(item) : null;
    const hayOpinion = !!(item.review && item.review.trim());
    const nAp = (item.entries || []).length;
    const ref = refDe(kind, item);
    const notasDiario = diarioDe(kind, item);
    const B = {
      // (Enlaces) lo de siempre de la ficha: escribir en el diario, su saga, su banda sonora y editar los datos
      enlaces: () => {
        const sg = D.sagas ? D.sagas(kind.id).find((x) => (x.items || []).some((i) => Number(i) === Number(item.id))) : null;
        const vivos = sg ? (sg.items || []).filter((i) => Number(i) === Number(item.id) || D.buscar(kind, 'g', i) || D.buscar(kind, 'p', i)) : [];
        return casilla('Enlaces', null,
          enlaceMaq('Escribir en el diario', null, () => RV.diario.escribir(RV.hoy(), null, [ref])),
          sg ? enlaceMaq(`Saga · ${sg.name}`, `Nº ${vivos.findIndex((i) => Number(i) === Number(item.id)) + 1} de ${vivos.length}`, () => RV.ir({ v: 'saga', id: sg.id })) : null,
          kind.isGame || kind.isMovie || kind.isTv ? enlaceMaq('Banda sonora', 'YouTube Music', () => abreFuera(`https://music.youtube.com/search?q=${encodeURIComponent(`${titLimpio(item.name)} soundtrack`)}`)) : null,
          enlaceMaq('Datos de la ficha', null, () => F.datos(kind, item, t)));
      },
      diario: notasDiario.length ? () => casilla('En tu diario', { texto: 'Escribir', fn: () => RV.diario.escribir(RV.hoy(), null, [ref]) }, diarioFicha(kind, item)) : false,
      capitulos: !g && kind.isBook && (() => casilla('Capítulos', null, capitulosFicha(kind, item))),
      progreso: !g && !kind.isBook && (() => h('section.casilla', progresoFicha(kind, item))),
      partes: !g && (() => casilla('Apuntes rápidos', { texto: '+ Añadir', fn: () => F.rapido(kind, item) }, rapidos(kind, item))),
      guias: kind.isGame && (() => casilla('Guías', null, guiasFicha(kind, item, pcg))),
      nota: g && (() => casilla('Nota', { texto: 'Cambiar', fn: () => F.valorar(kind, item) }, bloqueNota(kind, item))),
      opinion: g && (() => casilla('Opinión', { texto: hayOpinion ? 'Editar' : 'Escribir', fn: () => F.opinion(kind, item) }, hayOpinion ? cuerpo(item.review) : RV.vacio('Aún no has escrito nada.'))),
      estado: !g && (() => casilla('Nota', { texto: 'Apuntar', fn: () => F.apuntar(kind, item) },
        b ? bloqueNota(kind, b, `Media de ${RV.plural(nAp, 'apunte', 'apuntes')}`) : [RV.vacio('Aún sin nota: apunta algo y aquí saldrá la media.')])),
      apuntes: !g && (() => casilla(`Apuntes${nAp ? ` · ${nAp}` : ''}`, { texto: '+ Apuntar', fn: () => F.apuntar(kind, item) }, apuntes(kind, item))),
      datos: () => casilla('Datos', { texto: 'Editar', fn: () => F.datos(kind, item, t) }, datos(kind, item, t)),
      horas: kind.isGame && (() => casilla('Horas', null, horas(kind, item, pcg))),
      jugar: kind.isGame && !!pcg && (() => casilla('Jugar en el PC', null, enElPc(pcg))),
    };
    const faltan = new Set();
    for (const f of filas) {
      const dos = f.celdas.length === 2;
      const nodos = f.celdas.map((c) => {
        if (c.tipo === 'texto') {
          if (!c.titulo && !c.texto) return null;
          return h('section.casilla', c.titulo ? RV.etiqueta(c.titulo) : null, c.texto ? cuerpo(c.texto) : null);
        }
        if (c.tipo === 'imagen') return c.imagen ? casillaImagen(c, f.alto > 0) : null;
        if (typeof B[c.tipo] === 'function') return B[c.tipo]();
        // (una casilla que el móvil tampoco enseña en esa ficha —un diario vacío, «Guías» fuera de los videojuegos…—, se oculta sin más)
        if (!(c.tipo in B) && NO_HAY[c.tipo]) faltan.add(NO_HAY[c.tipo]);
        return null;
      });
      const r = fila(nodos, dos && nodos.filter(Boolean).length === 2 ? [f.split, 1 - f.split] : null, f.alto);
      if (r) cas.append(r);
    }
    if (faltan.size) cas.append(h('p.versal.maq-nota', `Esta página tiene casillas que Atrio en el ordenador todavía no muestra: ${[...faltan].join(', ')}.`));
  }

  // ---------------------------------------------------------------- La página
  RV.vistas.ficha = (ruta) => {
    const kind = D.tipo(ruta.k);
    let t = ruta.t;
    let item = kind && D.buscar(kind, t, ruta.id);
    // Si acaba de terminarse (o de empezarse otra vez), se sigue en su nueva forma
    if (kind && !item) {
      const otro = t === 'p' ? 'g' : 'p';
      const o = D.buscar(kind, otro, ruta.id);
      if (o) { item = o; t = otro; ruta.t = otro; }
    }
    const root = h('div.hoja.ficha');
    const top = h('div.ficha-top', h('button.enlace', { type: 'button', onclick: () => RV.volver() }, '← Volver'), h('span.hueco'));
    root.append(top, h('div.filete'));
    if (!kind || !item) {
      root.append(RV.vacio(D.conCuenta() ? 'Esta ficha ya no existe (quizá se ha borrado en otro dispositivo).' : 'Entra con tu cuenta para ver tus fichas.', true));
      return root;
    }
    const pcg = D.pcDe(kind, item);

    // Acciones de arriba a la derecha
    if (t === 'g') {
      top.append(h('button.enlace', { type: 'button', class: item.fav ? 'enlace acento' : 'enlace', onclick: () => F.favorito(kind, item), 'aria-pressed': String(!!item.fav) }, item.fav ? '★ Favorito' : '☆ Favorito'));
    }
    top.append(
      h('button.enlace', { type: 'button', onclick: () => RV.arteFicha(kind, t, item) }, 'Imágenes'),
      h('button.enlace', { type: 'button', onclick: () => F.datos(kind, item, t) }, 'Editar datos'),
      h('button.enlace.apagado', { type: 'button', onclick: () => F.borrar(kind, item, t) }, 'Borrar'));

    // Antetítulo, titular y entradilla
    root.append(h('div.ante', [D.estado(kind, item, t), kind.title, item.creator].filter(Boolean).join(' · ')));
    root.append(h(`h1.titular.gigante.ajusta${RV.tallaTitular(item.name)}`, { 'data-min': '30' }, item.name));
    const deck = [];
    if (item.platform) deck.push(item.platform);
    if (t === 'g') {
      if (num(item.start) > 0 && num(item.end) > 0 && num(item.start) !== num(item.end)) deck.push(`del ${RV.fmtDia(item.start)} al ${RV.fmtDia(item.end)}`);
      else if (num(item.end) > 0) deck.push(`terminado el ${RV.fmtDia(item.end)}`);
    } else {
      if (num(item.start) > 0) deck.push(`empezado el ${RV.fmtDia(item.start)}`);
      if (kind.isBook && num(item.chapter) > 0) deck.push(`capítulo ${item.chapter}`);
      const pct = D.porcentaje(item);
      if (pct !== null && pct > 0) deck.push(`${pct} % hecho`);
    }
    if (!kind.isBook && num(item.minutes) > 0) deck.push(RV.horas(item.minutes));
    if (kind.isBook && num(item.pages) > 0) deck.push(`${item.pages} páginas`);
    if (deck.length) root.append(h('p.entradilla', deck.join(' · ')));

    // Barra de acciones
    const barra = h('div.ficha-acciones');
    if (t === 'p') {
      const st = num(item.status);
      barra.append(h('div.estados', [[1, kind.statuses[1]], [0, kind.statuses[0]], [2, kind.statuses[2]]].map(([v, n]) => h('button.enlace.apagado', {
        type: 'button', 'aria-pressed': String(st === v), onclick: () => { if (st !== v) F.estado(kind, item, v); },
      }, n))));
      barra.append(h('span.sep'),
        h('button.enlace', { type: 'button', onclick: () => F.apuntar(kind, item) }, 'Apuntar →'),
        h('button.enlace', { type: 'button', onclick: () => F.rapido(kind, item) }, 'Apunte rápido +'));
      barra.append(h('span.hueco'), h('button.enlace.acento', { type: 'button', onclick: () => F.terminar(kind, item) }, 'Terminar y valorar →'));
    } else {
      barra.append(
        h('button.enlace', { type: 'button', onclick: () => F.valorar(kind, item) }, 'Valorar →'),
        h('button.enlace', { type: 'button', onclick: () => F.opinion(kind, item) }, item.review && item.review.trim() ? 'Editar la opinión' : 'Escribir la opinión'),
        h('button.enlace', { type: 'button', onclick: () => F.empezar(kind, item) }, `${palabraEmpezar(kind)} →`),
        h('span.hueco'));
    }
    if (pcg && !D.activoPc(pcg.key)) barra.append(h('button.enlace.acento', { type: 'button', onclick: () => RV.jugar(pcg) }, 'Jugar en el PC →'));
    root.append(barra);

    // Casillas
    const cas = h('div.casillas');
    const maq = RV.M.resolver(D.datos().maquetas, t, kind.id, item.id);
    if (maq) pintaMaqueta(cas, maq, kind, item, t, pcg);
    else if (t === 'g') {
      const opinion = item.review && item.review.trim() ? cuerpo(item.review) : RV.vacio('Aún no has escrito nada.');
      cas.append(fila([
        casilla('Nota', { texto: 'Cambiar', fn: () => F.valorar(kind, item) }, bloqueNota(kind, item)),
        casilla('Opinión', { texto: item.review && item.review.trim() ? 'Editar' : 'Escribir', fn: () => F.opinion(kind, item) }, opinion),
      ], [5, 7]));
      if (kind.isGame) {
        cas.append(fila([
          casilla('Datos', { texto: 'Editar', fn: () => F.datos(kind, item, t) }, datos(kind, item, t)),
          casilla('Horas', null, horas(kind, item, pcg)),
          casilla('Jugar en el PC', null, enElPc(pcg)),
        ]));
      } else {
        cas.append(fila([casilla('Datos', { texto: 'Editar', fn: () => F.datos(kind, item, t) }, datos(kind, item, t))]));
      }
    } else {
      const b = D.baremo(item);
      const nota = b ? bloqueNota(kind, b, `Media de ${RV.plural((item.entries || []).length, 'apunte', 'apuntes')}`)
        : [RV.vacio('Aún sin nota: apunta algo y aquí saldrá la media.')];
      const nAp = (item.entries || []).length;
      cas.append(fila([
        casilla('Nota', { texto: 'Apuntar', fn: () => F.apuntar(kind, item) }, nota),
        casilla(`Apuntes${nAp ? ` · ${nAp}` : ''}`, { texto: '+ Apuntar', fn: () => F.apuntar(kind, item) }, apuntes(kind, item)),
      ], [4, 8]));
      if (kind.isGame) {
        cas.append(fila([
          casilla('Apuntes rápidos', { texto: '+ Añadir', fn: () => F.rapido(kind, item) }, rapidos(kind, item)),
        ]));
        cas.append(fila([
          casilla('Datos', { texto: 'Editar', fn: () => F.datos(kind, item, t) }, datos(kind, item, t)),
          casilla('Horas', null, horas(kind, item, pcg)),
          casilla('Jugar en el PC', null, enElPc(pcg)),
        ]));
      } else {
        cas.append(fila([
          casilla('Apuntes rápidos', { texto: '+ Añadir', fn: () => F.rapido(kind, item) }, rapidos(kind, item)),
          casilla('Datos', { texto: 'Editar', fn: () => F.datos(kind, item, t) }, datos(kind, item, t)),
        ]));
      }
    }
    if (pcg) cas.append(fila([casilla('Capturas', null, bloqueCapturas(pcg))]));
    root.append(cas);
    return root;
  };

  // ---------------------------------------------------------------- Acciones
  // Apuntar: un deslizador por criterio y el texto. opts.entry: editar uno
  F.apuntar = (kind, play0, opts = {}) => {
    const play = fresca(kind, 'p', play0);
    const entry = opts.entry || null;
    const base = entry || (play.entries || []).slice(-1)[0] || D.baremo(play) || {};
    const v = {};
    for (const f of D.CAMPOS) v[f] = num(base[f], 50);
    const sliders = [];
    for (let i = 0; i <= kind.crit; i++) {
      const f = D.CAMPOS[i];
      sliders.push(RV.deslizador(kind.labels[i] || '', v[f], (n) => { v[f] = n; }, i === 0));
    }
    const nota = RV.areaTexto(entry ? entry.note : '', { placeholder: 'Lo que llevas, lo que te está gustando o no…', rows: '5' });
    const capitulo = kind.isBook ? RV.entrada(String(entry ? num(entry.chapter) || '' : num(play.chapter) || ''), { inputmode: 'numeric', placeholder: 'Capítulo' }) : null;
    const guardar = async (d) => {
      const cur = fresca(kind, 'p', play);
      const list = [...(cur.entries || [])];
      const cap = capitulo ? Math.max(0, parseInt(capitulo.value, 10) || 0) : (entry ? num(entry.chapter) : num(cur.chapter));
      const ne = { time: entry ? num(entry.time) : Date.now(), ...v, note: nota.value.trim(), chapter: cap };
      const i = entry ? list.findIndex((x) => num(x.time) === num(entry.time)) : -1;
      if (i >= 0) list[i] = ne; else list.push(ne);
      const next = { ...cur, entries: list };
      if (kind.isBook && cap > num(cur.chapter)) next.chapter = cap;
      if (await RV.editar({ op: 'savePlay', kind: kind.id, item: D.completarFicha(next) }, entry ? 'Apunte guardado' : 'Apuntado')) d.cerrar();
    };
    const borrar = entry ? {
      texto: 'Borrar el apunte', peligro: true,
      fn: (d) => RV.confirmar('¿Borrar este apunte?', 'Se borra en todos tus dispositivos.', 'Borrar', async () => {
        const cur = fresca(kind, 'p', play);
        const list = (cur.entries || []).filter((x) => num(x.time) !== num(entry.time));
        if (await RV.editar({ op: 'savePlay', kind: kind.id, item: D.completarFicha({ ...cur, entries: list }) }, 'Apunte borrado')) d.cerrar();
      }),
    } : null;
    RV.dialogo({
      ante: entry ? `Apunte · ${RV.fmtMomento(num(entry.time))}` : 'Apuntar',
      titulo: play.name, ancho: true,
      cuerpo: [
        h('div.criterios', sliders),
        RV.campo('Nota', nota),
        capitulo ? RV.campo('Capítulo', capitulo, 'Por dónde vas (opcional).') : null,
      ],
      acciones: [borrar, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: guardar }],
    });
  };

  // Apunte rápido: solo texto (memo). memo: editar o borrar uno que ya existe
  F.rapido = (kind, play, memo) => {
    const area = RV.areaTexto(memo ? memo.x : '', { placeholder: 'Una idea, algo que no quieres olvidar…', rows: '5' });
    const guardar = async (d) => {
      const x = area.value.trim();
      if (!x && !memo) { d.cerrar(); return; }
      const t = memo ? num(memo.t) : Date.now();
      if (await RV.editar({ op: 'memo', kind: kind.id, id: play.id, memo: { t, x, sh: memo ? memo.sh || '' : '' } }, 'Apunte guardado')) d.cerrar();
    };
    const borrar = memo ? {
      texto: 'Borrar', peligro: true,
      fn: async (d) => { if (await RV.editar({ op: 'memo', kind: kind.id, id: play.id, memo: { t: num(memo.t) }, remove: true }, 'Apunte borrado')) d.cerrar(); },
    } : null;
    RV.dialogo({
      ante: memo ? `Apunte rápido · ${RV.fmtMomento(num(memo.t))}` : 'Apunte rápido', titulo: play.name,
      cuerpo: [RV.campo('Texto', area), memo && memo.sh ? h('p.versal', 'Lleva una captura: la ves en el móvil y en Imágenes/Atrio.') : null],
      acciones: [borrar, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: guardar }],
    });
  };

  // Terminar: pasa a terminados con estas notas (la media de tus apuntes, para empezar) y tus apuntes como opinión
  F.terminar = (kind, play0) => {
    const play = fresca(kind, 'p', play0);
    const b = D.baremo(play) || {};
    const v = {};
    for (const f of D.CAMPOS) v[f] = num(b[f], 50);
    const sliders = [];
    for (let i = 0; i <= kind.crit; i++) {
      const f = D.CAMPOS[i];
      sliders.push(RV.deslizador(kind.labels[i] || '', v[f], (n) => { v[f] = n; }, i === 0));
    }
    RV.dialogo({
      ante: `Terminar · pasa a ${kind.doneWord}`, titulo: play.name, ancho: true,
      cuerpo: [
        h('p.texto', (play.entries || []).length
          ? 'Para empezar, la media de tus apuntes. Retócala si quieres: tus apuntes con texto pasan a la opinión.'
          : 'Ponle nota. Después podrás escribir la opinión.'),
        h('div.criterios', sliders),
      ],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, {
        texto: 'Terminado', principal: true,
        fn: async (d) => {
          const scores = {};
          for (const f of D.CAMPOS) scores[f] = v[f];
          if (await RV.editar({ op: 'finish', kind: kind.id, id: play.id, scores }, `${play.name} pasa a ${kind.doneWord.toLowerCase()}`)) {
            d.cerrar();
            RV.ir({ v: 'ficha', k: kind.id, t: 'g', id: play.id }, { reemplazar: true });
          }
        },
      }],
    });
  };

  // Valorar (cambiar las notas de algo terminado)
  F.valorar = (kind, game0) => {
    const game = fresca(kind, 'g', game0);
    const v = {};
    for (const f of D.CAMPOS) v[f] = num(game[f], 50);
    const sliders = [];
    for (let i = 0; i <= kind.crit; i++) {
      const f = D.CAMPOS[i];
      sliders.push(RV.deslizador(kind.labels[i] || '', v[f], (n) => { v[f] = n; }, i === 0));
    }
    RV.dialogo({
      ante: 'Valorar', titulo: game.name, ancho: true,
      cuerpo: h('div.criterios', sliders),
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, {
        texto: 'Guardar', principal: true,
        fn: async (d) => { if (await RV.editar({ op: 'saveGame', kind: kind.id, item: D.completarJuego({ ...fresca(kind, 'g', game), ...v }) }, 'Nota guardada')) d.cerrar(); },
      }],
    });
  };

  F.opinion = (kind, game0) => {
    const game = fresca(kind, 'g', game0);
    const area = RV.areaTexto(game.review, { placeholder: '¿Qué te ha parecido?', rows: '14' });
    RV.dialogo({
      ante: 'Tu opinión', titulo: game.name, ancho: true,
      cuerpo: [area, h('p.versal', 'Deja una línea en blanco entre párrafos.')],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, {
        texto: 'Guardar', principal: true,
        fn: async (d) => { if (await RV.editar({ op: 'saveGame', kind: kind.id, item: D.completarJuego({ ...fresca(kind, 'g', game), review: area.value.trim() }) }, 'Opinión guardada')) d.cerrar(); },
      }],
    });
  };

  F.favorito = (kind, game) => {
    const cur = fresca(kind, 'g', game);
    RV.editar({ op: 'saveGame', kind: kind.id, item: D.completarJuego({ ...cur, fav: !cur.fav }) }, cur.fav ? 'Quitado de favoritos' : 'Favorito');
  };

  F.capitulo = (kind, play, d) => {
    const cur = fresca(kind, 'p', play);
    RV.editar({ op: 'savePlay', kind: kind.id, item: D.completarFicha({ ...cur, chapter: Math.max(0, num(cur.chapter) + d) }) });
  };
  F.estado = (kind, play, status) => {
    const cur = fresca(kind, 'p', play);
    const next = { ...cur, status };
    if (status === 1 && !num(cur.start)) next.start = RV.hoy();
    RV.editar({ op: 'savePlay', kind: kind.id, item: D.completarFicha(next) }, `${cur.name}: ${kind.statuses[status].toLowerCase()}`);
  };

  // Rejugar / releer: vuelve a «en curso» (la valoración se queda como está)
  F.empezar = async (kind, game) => {
    const p = D.buscar(kind, 'p', game.id);
    const item = p ? { ...p, status: 1, start: num(p.start) || RV.hoy() } : {
      id: game.id, name: game.name, cover: game.cover || '', hero: game.hero || '', logo: game.logo || '', creator: game.creator || '',
      platform: game.platform || '', pages: num(game.pages), status: 1, start: RV.hoy(), entries: [], memos: [],
      ttbMain: num(game.ttbMain), ttbExtra: num(game.ttbExtra), ttbFull: num(game.ttbFull), ttbSrc: game.ttbSrc || '',
    };
    if (await RV.editar({ op: 'savePlay', kind: kind.id, item: D.completarFicha(item) }, `${game.name}: ${kind.statuses[1].toLowerCase()} otra vez`)) {
      RV.ir({ v: 'ficha', k: kind.id, t: 'p', id: game.id });
    }
  };

  // Datos: creador, plataforma o formato, fechas y horas (o páginas y capítulo en libros)
  F.datos = (kind, item0, t) => {
    const item = fresca(kind, t, item0);
    const lista = `plataformas-${kind.id}`;
    // El nombre también se corrige aquí (la ficha sigue siendo la misma); vacío, se queda el que tenía
    const nombre = RV.entrada(item.name || '', { placeholder: 'Nombre', spellcheck: 'true' });
    const creador = RV.entrada(item.creator || '', { placeholder: kind.creatorWord });
    const plataforma = RV.entrada(item.platform || '', { placeholder: kind.platforms.slice(0, 3).join(', '), list: lista });
    const dl = h('datalist', { id: lista }, kind.platforms.map((p) => h('option', { value: p })));
    const empezado = RV.entrada(RV.isoDia(num(item.start)), { type: 'date' });
    const terminado = t === 'g' ? RV.entrada(RV.isoDia(num(item.end)), { type: 'date' }) : null;
    const horasIn = !kind.isBook ? RV.entrada(num(item.minutes) ? String(Math.round((num(item.minutes) / 60) * 10) / 10).replace('.', ',') : '', { inputmode: 'decimal', placeholder: 'Horas' }) : null;
    const paginas = kind.isBook ? RV.entrada(num(item.pages) ? String(item.pages) : '', { inputmode: 'numeric', placeholder: 'Páginas' }) : null;
    const capitulo = kind.isBook && t === 'p' ? RV.entrada(num(item.chapter) ? String(item.chapter) : '', { inputmode: 'numeric', placeholder: 'Capítulo' }) : null;
    const hoyBtn = (inp) => h('button.enlace', { type: 'button', onclick: () => { inp.value = RV.isoDia(RV.hoy()); } }, 'Hoy');
    const guardar = async (d) => {
      const cur = fresca(kind, t, item);
      const next = { ...cur, name: nombre.value.trim() || cur.name, creator: creador.value.trim(), platform: plataforma.value.trim(), start: RV.deIso(empezado.value) };
      if (terminado) next.end = RV.deIso(terminado.value);
      if (horasIn) { const hv = parseFloat(String(horasIn.value).replace(',', '.')); next.minutes = Number.isFinite(hv) && hv > 0 ? Math.round(hv * 60) : 0; }
      if (paginas) next.pages = Math.max(0, parseInt(paginas.value, 10) || 0);
      if (capitulo) next.chapter = Math.max(0, parseInt(capitulo.value, 10) || 0);
      const op = t === 'g' ? { op: 'saveGame', kind: kind.id, item: D.completarJuego(next) } : { op: 'savePlay', kind: kind.id, item: D.completarFicha(next) };
      if (await RV.editar(op, 'Datos guardados')) d.cerrar();
    };
    RV.dialogo({
      ante: 'Editar datos', titulo: item.name,
      cuerpo: [
        RV.campo('Nombre', nombre),
        h('div.dos-campos', RV.campo(kind.creatorWord, creador), RV.campo(kind.platformWord, h('div', plataforma, dl))),
        h('div.dos-campos',
          RV.campo('Empezado', h('div', { style: { display: 'flex', gap: '12px', alignItems: 'center' } }, empezado, hoyBtn(empezado))),
          terminado ? RV.campo('Terminado', h('div', { style: { display: 'flex', gap: '12px', alignItems: 'center' } }, terminado, hoyBtn(terminado))) : h('span')),
        h('div.dos-campos',
          horasIn ? RV.campo('Horas', horasIn) : paginas ? RV.campo('Páginas', paginas) : h('span'),
          capitulo ? RV.campo('Capítulo', capitulo) : h('span')),
      ],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: guardar }],
    });
  };

  F.borrar = (kind, item, t, o = {}) => {
    RV.confirmar(`¿Borrar ${item.name}?`,
      `Se borra la ficha ${t === 'g' ? 'con su nota y su opinión' : 'con sus apuntes'} en todos tus dispositivos (también en el móvil). No se puede deshacer.`,
      'Borrar', async () => {
        if (await RV.editar({ op: t === 'g' ? 'deleteGame' : 'deletePlay', kind: kind.id, id: item.id }, `${item.name}: borrado`) && !o.quedarse) RV.volver();
      });
  };

  // Y (opciones) sobre cualquier ficha de tu cuenta, esté o no instalada en este PC: favorito, nota, apuntes,
  // estado, datos, borrar… y, si está en el PC, jugar y sus opciones del lanzador
  RV.opcionesFicha = (kind, t, item0) => {
    const item = fresca(kind, t, item0) || item0;
    const pcg = D.pcDe(kind, item);
    const ul = h('ul.j-opciones');
    const op = (texto, fn) => ul.append(h('li', h('button.resultado', { type: 'button', style: { width: '100%', textAlign: 'left' }, onclick: () => { d.cerrar(); fn(); } },
      h('span.tit', texto), h('span'))));
    const abrir = () => { if (RV.modo === 'juegos') RV.cambiarModo('revista'); RV.ir({ v: 'ficha', k: kind.id, t, id: item.id }); };
    if (pcg && !D.activoPc(pcg.key)) op('Jugar en el PC', () => RV.jugar(pcg));
    op('Abrir su ficha', abrir);
    if (t === 'g') {
      op(item.fav ? 'Quitar de favoritos' : 'Añadir a favoritos', () => F.favorito(kind, item));
      op('Cambiar la nota', () => F.valorar(kind, item));
      op(String(item.review || '').trim() ? 'Editar la opinión' : 'Escribir la opinión', () => F.opinion(kind, item));
      op(palabraEmpezar(kind), () => F.empezar(kind, item));
    } else {
      op('Apuntar', () => F.apuntar(kind, item));
      op(`Terminado: valorar`, () => F.terminar(kind, item));
      kind.statuses.forEach((st, i) => { if (i !== num(item.status)) op(`Pasar a «${st}»`, () => F.estado(kind, item, i)); });
    }
    if (RV.arteFicha) op('Imágenes de la ficha: portada, fondo, logo', () => RV.arteFicha(kind, t, item));
    if (pcg && RV.opcionesJuego) op('Opciones del PC (sus imágenes en el lanzador, nombre, guías…)', () => RV.opcionesJuego(pcg));
    op('Editar los datos', () => F.datos(kind, item, t));
    op('Borrar la ficha', () => F.borrar(kind, item, t, { quedarse: true }));
    const d = RV.dialogo({ ante: `Opciones · ${t === 'g' ? kind.doneOne : D.estado(kind, item, t)}`, titulo: item.name, cuerpo: ul });
  };
})(window.RV);
