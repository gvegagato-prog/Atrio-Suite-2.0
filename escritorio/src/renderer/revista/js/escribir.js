'use strict';
/* global window, document */
// Escribir: tus obras, y dentro de cada una el manuscrito (capítulos y escenas), las fichas, el tablón de notas, la
// línea temporal y las cifras. Es lo mismo que «Escribir» del móvil (mismos documentos, misma cuenta): lo que escribes
// aquí está en el móvil y viceversa, y sin conexión se escribe igual y se sube solo. El editor está en escribir-editor.js.
(function (RV) {
  const h = RV.h;
  const api = RV.api;
  const num = RV.num;
  RV.vistas = RV.vistas || {};

  // ---------------------------------------------------------------- Datos (una copia de lo que guarda el proceso principal)
  const E = {};
  RV.E = E;
  let docs = new Map();
  let estado = { error: '', ultima: 0, pendientes: 0, signed: false };
  let cargado = false;
  E.estado = () => estado;
  E.cargado = () => cargado;
  E.set = (lista, st) => { docs = new Map((lista || []).map((o) => [o.id, o])); if (st) estado = st; cargado = true; };
  const vivos = () => [...docs.values()];
  const orden = (a, b) => (num(a.orden) - num(b.orden)) || (num(a.u) - num(b.u));
  E.get = (id) => docs.get(id) || null;
  E.tipo = (t, pid) => vivos().filter((o) => o.t === t && (pid == null || o.pid === pid)).sort(orden);
  E.obras = () => E.tipo('p');
  // Los capítulos en el orden del libro (los sueltos y los de cada parte: escribir-estructura.js)
  E.capitulos = (pid) => RV.EST.estructura(E.tipo('c', pid), E.tipo('a', pid)).libro;
  E.estructura = (pid) => RV.EST.estructura(E.tipo('c', pid), E.tipo('a', pid));
  E.partes = (pid) => E.tipo('a', pid);
  E.ramas = (pid) => E.tipo('r', pid);
  E.relaciones = (pid) => E.tipo('v', pid);
  E.relacionesDe = (fid) => vivos().filter((o) => o.t === 'v' && (o.a === fid || o.b === fid));
  // Las notas y fichas vinculadas a una parte o un capítulo
  E.vinculados = (id, pid) => RV.EST.vinculados(id, [...E.notas(pid), ...E.fichas(pid)]);
  E.numeroCapitulo = (c) => E.capitulos(c.pid).findIndex((x) => x.id === c.id) + 1;
  E.escenas = (cid) => vivos().filter((o) => o.t === 'e' && o.cid === cid).sort(orden);
  E.escenasDe = (pid) => E.capitulos(pid).flatMap((c) => E.escenas(c.id));
  E.fichas = (pid) => E.tipo('f', pid);
  E.notas = (pid) => E.tipo('n', pid);
  E.hitos = (pid) => E.tipo('h', pid);
  // La última escena que se tocó (aquí o en otro dispositivo), de una obra sin archivar: para «seguir escribiendo»
  E.ultimaEscena = () => vivos().filter((o) => o.t === 'e' && E.get(o.cid) && E.get(o.pid) && !E.get(o.pid).arch).sort((a, b) => num(b.u) - num(a.u))[0] || null;
  E.borradores = (eid) => vivos().filter((o) => o.t === 'b' && o.eid === eid).sort((a, b) => a.u - b.u);
  const PAL = /[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu;
  E.palabras = (t) => (String(t || '').match(PAL) || []).length;
  E.palabrasObra = (pid) => E.escenasDe(pid).reduce((s, e) => s + num(e.palabras), 0);
  E.palabrasCapitulo = (cid) => E.escenas(cid).reduce((s, e) => s + num(e.palabras), 0);
  const norma = (s) => String(s || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
  E.fichasEn = (pid, texto) => {
    const t = ` ${norma(texto).replace(/[^\p{L}\p{N}]+/gu, ' ')} `;
    return E.fichas(pid).filter((f) => [f.nombre, ...String(f.alias || '').split(',')].map((x) => norma(x).trim().replace(/[^\p{L}\p{N}]+/gu, ' ')).filter((x) => x.length >= 2).some((x) => t.includes(` ${x} `)));
  };
  E.busca = (pid, q, limite = 200) => {
    const n = norma(q);
    if (!n.trim()) return [];
    const out = [];
    const mira = (doc, donde, txt) => {
      const t = norma(txt); let i = t.indexOf(n); let veces = 0;
      while (i >= 0 && out.length < limite && veces < 8) {
        const a = Math.max(0, i - 40); const b = Math.min(txt.length, i + n.length + 60);
        out.push({ doc, donde, extracto: (a > 0 ? '…' : '') + txt.slice(a, b).replace(/\n/g, ' ') + (b < txt.length ? '…' : '') });
        veces++; i = t.indexOf(n, i + n.length);
      }
    };
    for (const e of E.escenasDe(pid)) { mira(e, `Escena · ${e.titulo || ''}`, e.texto || ''); mira(e, `Escena · ${e.titulo || ''}`, e.resumen || ''); }
    for (const f of E.fichas(pid)) mira(f, `Ficha · ${f.nombre || ''}`, `${f.nombre || ''} ${f.alias || ''} ${f.resumen || ''} ${f.texto || ''}`);
    for (const x of E.notas(pid)) mira(x, `Nota · ${x.titulo || ''}`, `${x.titulo || ''} ${x.texto || ''}`);
    return out;
  };
  // Las palabras escritas, por día (en la obra o en todas), sumando todos tus dispositivos
  E.escritas = (dia, pid) => vivos().reduce((s, o) => (o.t === 'l' && o.dia === dia && (pid == null || o.pid === pid) ? s + Object.values(o.dev || {}).reduce((a, v) => a + num(v), 0) : s), 0);
  E.ultimosDias = (n, pid) => { const hoy = RV.hoy(); return Array.from({ length: n }, (_, i) => { const d = hoy - n + 1 + i; return [d, E.escritas(d, pid)]; }); };
  E.racha = (pid) => { let d = RV.hoy(); if (E.escritas(d, pid) === 0) d--; let n = 0; while (E.escritas(d, pid) > 0) { n++; d--; } return n; };
  // Días distintos en los que has escrito algo en la obra
  E.diasEscritos = (pid) => new Set(vivos().filter((o) => o.t === 'l' && o.pid === pid && Object.values(o.dev || {}).reduce((a, v) => a + num(v), 0) > 0).map((o) => o.dia)).size;
  // Lo que la guía de la novela necesita saber de la obra (escribir-guia.js)
  E.datosGuia = (pid) => {
    const o = E.get(pid) || {};
    const res = E.capitulos(pid).filter((c) => String(c.resumen || '').trim()).length + E.escenasDe(pid).filter((e) => String(e.resumen || '').trim()).length + E.hitos(pid).length
      + E.notas(pid).filter((n) => n.titulo === RV.G.TITULO_ESQUEMA).reduce((a, n) => a + RV.G.seccionesEsquema(n.texto), 0);
    return { obra: o, personajes: E.fichas(pid).filter((f) => f.tipo === 'personaje').length, resumenes: res, dias: E.diasEscritos(pid), palabras: E.palabrasObra(pid), ahora: Date.now() };
  };
  E.ordenEntre = (a, b) => (a == null && b == null ? 1000 : a == null ? b - 1000 : b == null ? a + 1000 : (a + b) / 2);
  // Operaciones (las hace el proceso principal, que guarda y sube)
  const op = (nombre, ...args) => api.escritos(nombre, ...args);
  E.op = op;
  E.err = (e) => RV.aviso(RV.limpiaError(e), 'error');
  const TIPO = { p: 'Obra', c: 'Capítulo', e: 'Escena', f: 'Ficha', n: 'Nota', h: 'Suceso', b: 'Borrador', a: 'Parte', r: 'Rama', v: 'Unión' };
  const tituloDe = (d) => (d.t === 'f' ? d.nombre : d.titulo || d.nombre) || 'Sin título';
  E.tituloDe = tituloDe;
  // A la papelera, con «Deshacer» en el aviso
  E.aPapelera = async (d) => {
    try {
      await op('borra', d.id);
      RV.aviso(`${TIPO[d.t] || 'Escrito'} «${tituloDe(d)}» en la papelera`, null, { texto: 'Deshacer', fn: () => op('restaura', d.id).catch(E.err) });
    } catch (e) { E.err(e); }
  };

  RV.COLORES = ['#B0102F', '#D9822B', '#C9A227', '#5B8C5A', '#3E8E9E', '#4A6FA5', '#7B5EA7', '#A8577E', '#6B6B6B'];
  const ESTADOS = ['Borrador', 'Revisión', 'Final'];
  const COL_ESTADO = ['#B8B2A6', '#D9A441', '#5B8C5A'];
  const TIPOS_FICHA = [['personaje', 'Personajes', 'Personaje'], ['lugar', 'Lugares', 'Lugar'], ['objeto', 'Objetos', 'Objeto'], ['mundo', 'Mundo', 'Mundo'], ['otro', 'Otros', 'Otro']];
  const TIPOS_NOTA = [['nota', 'Notas', 'Nota'], ['idea', 'Ideas', 'Idea'], ['resumen', 'Resúmenes', 'Resumen'], ['esquema', 'Esquemas', 'Esquema'], ['investigacion', 'Investigación', 'Investigación'], ['plantilla', 'Plantillas', 'Plantilla']];
  E.ESTADOS = ESTADOS; E.COL_ESTADO = COL_ESTADO; E.TIPOS_FICHA = TIPOS_FICHA; E.TIPOS_NOTA = TIPOS_NOTA;
  const cifra = (n) => new Intl.NumberFormat('es-ES').format(n);
  E.cifra = cifra;
  const hace = (t) => {
    const d = Date.now() - t;
    if (d < 90000) return 'ahora mismo';
    if (d < 3600000) return `hace ${Math.floor(d / 60000)} min`;
    if (d < 86400000) return `hace ${Math.floor(d / 3600000)} h`;
    if (d < 172800000) return 'ayer';
    if (d < 30 * 86400000) return `hace ${Math.floor(d / 86400000)} días`;
    return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'long' }).format(new Date(t));
  };
  E.hace = hace;

  // replaceChildren que se salta lo que no hay (null, false): si no, saldría escrito «null»
  const reemplaza = (el, ...kids) => el.replaceChildren(...kids.flat(Infinity).filter((x) => x != null && x !== false));

  // ---------------------------------------------------------------- Piezas
  const barra = (frac, color) => h('span.e-barra', h('span', { style: { width: `${Math.round(Math.max(0, Math.min(1, frac)) * 100)}%`, background: color || null } }));
  const barras = (vals, meta) => {
    const max = Math.max(1, ...vals, meta || 0);
    return h('div.e-barras', vals.map((v, i) => h('span', { class: i === vals.length - 1 ? 'hoy' : v > 0 ? 'con' : '', title: String(v), style: { height: `${v > 0 ? Math.max(4, Math.round((v / max) * 100)) : 2}%` } })),
      meta ? h('i.meta', { style: { bottom: `${Math.round((meta / max) * 100)}%` } }) : null);
  };
  const estadoNube = () => {
    const s = estado;
    if (!s.signed) return 'Se guarda en este PC. Entra con tu cuenta (Ajustes) para tenerlo en todos tus dispositivos.';
    if (s.error && s.pendientes) return `Sin conexión: ${RV.plural(s.pendientes, 'cambio', 'cambios')} por subir. Se subirán solos cuando haya red.`;
    if (s.error) return 'Sin conexión con tu cuenta (se reintenta solo).';
    if (s.pendientes) return `Subiendo ${RV.plural(s.pendientes, 'cambio', 'cambios')}…`;
    return s.ultima ? 'Todo sincronizado' : '';
  };
  E.estadoNube = estadoNube;
  const colorDe = (hex, def) => (/^#[0-9a-f]{6}$/i.test(hex || '') ? hex : def || 'var(--acento)');
  E.colorDe = colorDe;

  // Selector de colores
  // (los botones se marcan sin volver a crearlos: dentro de un <label>, quitar el botón pulsado haría que el label
  // pulsara el primero)
  const colores = (valor, alCambiar) => {
    let v = valor;
    const botones = RV.COLORES.map((c) => h('button.e-color', { type: 'button', 'aria-label': c, style: { background: c }, onclick: () => { v = c; alCambiar(c); marca(); } }));
    const marca = () => botones.forEach((b, i) => b.setAttribute('aria-pressed', RV.COLORES[i].toLowerCase() === (v || '').toLowerCase() ? 'true' : 'false'));
    marca();
    return h('div.e-colores', botones);
  };
  const chips = (opciones, actual, alElegir) => {
    const botones = opciones.map(([k, n]) => h('button.e-chip', { type: 'button', onclick: () => { actual = k; alElegir(k); marca(); } }, n));
    const marca = () => botones.forEach((b, i) => b.setAttribute('aria-pressed', opciones[i][0] === actual ? 'true' : 'false'));
    marca();
    return h('div.e-chips', botones);
  };
  const selector = (valor, opciones, alCambiar) => {
    const s = h('select.entrada', { onchange: () => alCambiar(s.value) }, opciones.map(([k, n]) => h('option', { value: k, selected: k === valor ? '' : null }, n)));
    return s;
  };
  // Elegir varios (vincular a partes y capítulos): botones que se encienden y se apagan
  const varios = (opciones, elegidos, alCambiar) => {
    const sel = new Set(elegidos || []);
    const botones = opciones.map(([k, n]) => h('button.e-chip', { type: 'button', onclick: () => { if (sel.has(k)) sel.delete(k); else sel.add(k); alCambiar([...sel]); marca(); } }, n));
    const marca = () => botones.forEach((b, i) => b.setAttribute('aria-pressed', sel.has(opciones[i][0]) ? 'true' : 'false'));
    marca();
    return h('div.e-chips', botones);
  };
  // Lo que se puede vincular en una obra: sus partes y sus capítulos
  const destinosVinculo = (pid) => [...E.partes(pid).map((a) => [a.id, `Parte · ${a.titulo || 'Sin título'}`]), ...E.capitulos(pid).map((c) => [c.id, `Cap. ${E.numeroCapitulo(c)} · ${c.titulo || 'Sin título'}`])];
  E.colores = (v, f) => colores(v, f); E.chips = (o, a, f) => chips(o, a, f); E.selector = (v, o, f) => selector(v, o, f); E.varios = (o, e, f) => varios(o, e, f);
  E.barra = (f, c) => barra(f, c); E.reemplaza = (...a) => reemplaza(...a);

  // ---------------------------------------------------------------- Sprints de escritura
  // Escribir sin parar durante un rato (10, 25, 45 min…), con una meta si quieres. Cuentan las palabras nuevas de la obra
  // (en cualquier escena). Sigue aunque salgas del editor o cierres Atrio; lo hecho se guarda en este PC (Cifras).
  const lee = (k, def) => { try { return JSON.parse(localStorage.getItem(k) || '') ?? def; } catch { return def; } };
  const graba = (k, v) => { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, JSON.stringify(v)); } catch { /* */ } };
  const SP = {
    actual: lee('escribir:sprint', null),
    activo(pid) { const s = SP.actual; return !!(s && s.hasta > Date.now() && (pid == null || s.pid === pid)); },
    llevas() { const s = SP.actual; if (!s) return 0; const hoy = RV.hoy(); return Math.max(0, E.escritas(s.dia, s.pid) + (hoy !== s.dia ? E.escritas(hoy, s.pid) : 0) - s.base); },
    empieza(pid, min, meta) {
      const d = RV.hoy(); const desde = Date.now();
      SP.actual = { pid, min, meta: meta || 0, desde, hasta: desde + min * 60000, dia: d, base: E.escritas(d, pid) };
      graba('escribir:sprint', SP.actual); vigila(); RV.refrescar();
    },
    termina(ya = false) {
      const s = SP.actual; if (!s) return;
      const fin = ya ? Math.min(Date.now(), s.hasta) : s.hasta;
      const min = Math.max(ya ? 0 : s.min, Math.round((fin - s.desde) / 60000));
      const r = { pid: s.pid, desde: s.desde, min, pal: SP.llevas(), meta: s.meta };
      SP.actual = null; graba('escribir:sprint', null);
      if (min >= 1) graba('escribir:sprints', [...SP.historial(), r].slice(-200));
      if (min >= 1 || r.pal > 0) resultado(r);
      if (RV.editorActivo) RV.editorActivo.refresca();
    },
    historial: (pid) => (lee('escribir:sprints', []) || []).filter((x) => pid == null || x.pid === pid),
  };
  E.sprint = SP;
  let timerSprint = null;
  function vigila() {
    clearTimeout(timerSprint);
    if (!SP.actual) return;
    const q = SP.actual.hasta - Date.now();
    if (q <= 0) { if (E.cargado()) SP.termina(); else timerSprint = setTimeout(vigila, 1000); return; }
    timerSprint = setTimeout(vigila, Math.min(q, 60000));
  }
  setTimeout(vigila, 1500);
  const porMinuto = (r) => (r.min ? Math.floor(r.pal / r.min) : 0);
  function resultado(r) {
    const antes = SP.historial(r.pid).filter((x) => x.desde !== r.desde);
    const record = antes.length && porMinuto(r) > Math.max(...antes.map(porMinuto));
    const obra = E.get(r.pid);
    RV.dialogo({
      ante: 'Sprint terminado', titulo: `${cifra(r.pal)} palabras`, cuerpo: [
        h('p.versal', `${r.min ? `En ${r.min} min · ${porMinuto(r)} por minuto` : 'En menos de un minuto'}${obra ? ` · ${obra.titulo || ''}` : ''}`),
        r.meta ? h('p.texto', r.pal >= r.meta ? `Meta de ${cifra(r.meta)} conseguida.` : `Te quedaste a ${cifra(r.meta - r.pal)} de la meta (${cifra(r.meta)}).`) : null,
        record ? h('p.texto.acento', 'Es tu mejor ritmo en esta obra.') : null],
      acciones: ['hueco', { texto: 'Seguir', principal: true, fn: (d) => d.cerrar() }],
    });
  }
  E.dlgSprint = (pid) => {
    let min = 25;
    const meta = RV.entrada('', { type: 'number', min: '0', placeholder: 'Opcional' });
    const mejor = SP.historial(pid).reduce((m, x) => Math.max(m, porMinuto(x)), 0);
    RV.dialogo({
      ante: 'Escribir', titulo: 'Sprint de escritura', cuerpo: [
        h('p.texto', 'Escribe sin parar, sin corregir, hasta que acabe el tiempo. Cuentan las palabras nuevas de toda la obra.'),
        // (no en un <label>: un clic en cualquier botón de dentro pulsaría el primero)
        h('div.campo', h('span', 'Cuánto'), chips([5, 10, 15, 20, 25, 30, 45, 60].map((m) => [m, `${m} min`]), min, (k) => { min = k; })),
        RV.campo('Meta de palabras', meta), mejor ? h('p.apagado', `Tu mejor ritmo en esta obra: ${mejor} palabras por minuto.`) : null],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Empezar', principal: true, fn: (d) => { d.cerrar(); SP.empieza(pid, min, num(meta.value)); } }],
    });
  };
  E.sprintsObra = (pid) => {
    const l = SP.historial(pid).sort((a, b) => b.desde - a.desde);
    if (!l.length) return null;
    const f = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    return h('section', h('div.etiqueta', h('span.t', 'Sprints (en este PC)')),
      h('p.cursiva', `${l.length} · ${cifra(l.reduce((s, x) => s + x.pal, 0))} palabras en ${l.reduce((s, x) => s + x.min, 0)} min · mejor ritmo ${Math.max(...l.map(porMinuto))} por minuto`),
      h('table.e-sprints', h('tbody', l.slice(0, 8).map((x) => h('tr', h('td', f.format(new Date(x.desde)).replace(/\./g, '')), h('td', `${x.min} min`),
        h('td', `${cifra(x.pal)} palabras${x.meta ? (x.pal >= x.meta ? ' ✓' : ` de ${cifra(x.meta)}`) : ''}`), h('td', `${porMinuto(x)}/min`))))));
  };

  // ---------------------------------------------------------------- Diálogos: obra, capítulo, ficha, nota, hito
  E.dlgObra = (obra) => {
    const t = RV.entrada(obra ? obra.titulo : '', { placeholder: 'El título de tu novela', 'data-autofocus': true });
    const au = RV.entrada(obra ? obra.autor : '');
    const ge = RV.entrada(obra ? obra.genero : '', { placeholder: 'Fantasía, thriller, memorias…' });
    const si = RV.areaTexto(obra ? obra.sinopsis : '', { rows: '4' });
    let color = obra ? obra.color || RV.COLORES[0] : RV.COLORES[0];
    const meta = RV.entrada(obra ? (obra.meta || '') : '80000', { type: 'number', min: '0' });
    const dia = RV.entrada(obra ? (obra.diaria || '') : '500', { type: 'number', min: '0' });
    let guiada = obra ? RV.G.activa(obra) : true;
    const gu = h('input', { type: 'checkbox', checked: guiada ? '' : null, onchange: (e) => { guiada = e.target.checked; } });
    const guardar = async (d) => {
      if (!t.value.trim()) { RV.aviso('Ponle un título.', 'error'); return; }
      const c = { titulo: t.value.trim(), autor: au.value.trim(), genero: ge.value.trim(), sinopsis: si.value.trim(), color, meta: num(meta.value), diaria: num(dia.value), gOn: guiada ? 1 : 0 };
      try {
        if (obra) await op('edita', obra.id, c);
        else {
          const o = await op('nuevo', 'p', '', c);
          const cap = await op('nuevo', 'c', o.id, { titulo: 'Capítulo 1' });
          const es = await op('nuevo', 'e', o.id, { cid: cap.id, titulo: 'Escena 1' });
          d.cerrar();
          if (guiada) RV.ir({ v: 'obra', id: o.id, tab: TAB_GUIA }); else RV.ir({ v: 'editor', id: es.id });
          return;
        }
        d.cerrar();
      } catch (e) { E.err(e); }
    };
    const archivar = async (d) => { try { await op('edita', obra.id, { arch: obra.arch ? 0 : 1 }); d.cerrar(); RV.ir({ v: 'escribir' }); } catch (e) { E.err(e); } };
    const borrar = (d) => RV.confirmar(`¿Borrar «${obra.titulo}»?`, 'Irá a la papelera con sus capítulos, escenas, fichas y notas, también en tus otros dispositivos. Podrás recuperarla durante 30 días.', 'A la papelera', async () => { d.cerrar(); await E.aPapelera(obra); RV.ir({ v: 'escribir' }, { reemplazar: true }); });
    RV.dialogo({
      ante: obra ? 'Ajustes de la obra' : 'Nueva obra', titulo: obra ? obra.titulo : 'Empieza una novela', ancho: true,
      cuerpo: [RV.campo('Título', t), h('div.e-dos', RV.campo('Autor o autora', au), RV.campo('Género', ge)), RV.campo('Sinopsis', si),
        RV.campo('Color', colores(color, (c) => { color = c; })),
        h('div.e-dos', RV.campo('Meta de palabras', meta), RV.campo('Palabras al día', dia, 'Las metas son tuyas: sirven para ver cuánto llevas, no para presionarte.')),
        h('label.e-check.e-guiarme', gu, h('span', h('b', ' Guiarme paso a paso'), h('small', ' Una guía de 11 pasos, de la idea al manuscrito final: te dice qué toca ahora. Puedes quitarla cuando quieras.')))],
      acciones: [obra ? { texto: 'Borrar la obra', peligro: true, fn: borrar } : null, obra ? { texto: obra.arch ? 'Sacar del archivo' : 'Archivar', fn: archivar } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: obra ? 'Guardar' : 'Crear', principal: true, fn: guardar }],
    });
  };

  // Capítulo: título, parte (o suelto), resumen y color de su cuadrado. aidNuevo: la parte donde se crea.
  E.dlgCapitulo = (pid, cap, aidNuevo = '', alCrear = null) => {
    const t = RV.entrada(cap ? cap.titulo : '', { 'data-autofocus': true });
    const re = RV.areaTexto(cap ? cap.resumen : '', { rows: '3' });
    let aid = cap ? cap.aid || '' : aidNuevo || '';
    let color = cap ? cap.color || '' : '';
    const pa = selector(aid, [['', 'Ninguna (capítulo suelto)'], ...E.partes(pid).map((x) => [x.id, x.titulo || 'Sin título'])], (v) => { aid = v; });
    const esc = cap ? E.escenas(cap.id).length : 0;
    RV.dialogo({
      ante: cap ? 'Capítulo' : 'Nuevo capítulo', titulo: cap ? cap.titulo || 'Capítulo' : 'Otro capítulo', ancho: true,
      cuerpo: [RV.campo('Título', t), RV.campo('Parte', pa), RV.campo('Resumen', re), RV.campo('Color de su cuadrado', colores(color, (c) => { color = c; }))],
      acciones: [cap ? { texto: 'Borrar el capítulo', peligro: true, fn: (d) => RV.confirmar('¿Borrar el capítulo?', `Irá a la papelera con ${esc === 1 ? 'su escena' : `sus ${esc} escenas`}. Podrás recuperarlo durante 30 días.`, 'A la papelera', async () => { d.cerrar(); await E.aPapelera(cap); }) } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: async (d) => {
          try {
            const c = { titulo: t.value.trim() || `Capítulo ${E.capitulos(pid).length + 1}`, resumen: re.value.trim(), color: color || null };
            if (cap) {
              await op('edita', cap.id, c);
              if ((cap.aid || '') !== aid) await op('mueveCapitulo', cap.id, aid, null, null);
            } else {
              const n = await op('nuevo', 'c', pid, { ...c, ...(aid ? { aid } : {}) });
              await op('nuevo', 'e', pid, { cid: n.id, titulo: 'Escena 1' });
              if (alCrear) alCrear(n);
            }
            d.cerrar();
          } catch (e) { E.err(e); }
        } }],
    });
  };

  // Parte (acto, libro…): título, resumen y color
  E.dlgParte = (pid, parte, alCrear = null) => {
    const t = RV.entrada(parte ? parte.titulo : '', { 'data-autofocus': true, placeholder: `Parte ${['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'][E.partes(pid).length] || E.partes(pid).length + 1}` });
    const re = RV.areaTexto(parte ? parte.resumen : '', { rows: '4', placeholder: 'Qué pasa en esta parte' });
    let color = parte ? parte.color || RV.COLORES[0] : RV.COLORES[0];
    const caps = parte ? E.capitulos(pid).filter((c) => c.aid === parte.id).length : 0;
    RV.dialogo({
      ante: parte ? 'Parte' : 'Nueva parte', titulo: parte ? parte.titulo || 'Parte' : 'Una parte del libro', ancho: true,
      cuerpo: [RV.campo('Título', t, 'Parte I, Primer acto, Libro dos…'), RV.campo('Resumen', re), RV.campo('Color', colores(color, (c) => { color = c; }))],
      acciones: [parte ? { texto: 'Borrar la parte', peligro: true, fn: (d) => RV.confirmar('¿Borrar la parte?', caps ? `Irá a la papelera con ${caps === 1 ? 'su capítulo' : `sus ${caps} capítulos`} (y sus escenas). Podrás recuperarla durante 30 días.` : 'Irá a la papelera. Podrás recuperarla durante 30 días.', 'A la papelera', async () => { d.cerrar(); await E.aPapelera(parte); RV.ir({ v: 'obra', id: pid, tab: 0 }, { reemplazar: true }); }) } : null,
        'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: parte ? 'Guardar' : 'Crear', principal: true, fn: async (d) => {
          try {
            const c = { titulo: t.value.trim() || t.placeholder, resumen: re.value.trim(), color };
            if (parte) await op('edita', parte.id, c);
            else { const n = await op('nuevo', 'a', pid, c); if (alCrear) alCrear(n); }
            d.cerrar();
          } catch (e) { E.err(e); }
        } }],
    });
  };

  E.dlgFicha = (pid, ficha, tipoNuevo = 'personaje', alCerrar, camposNuevos = null) => {
    let tipo = ficha ? ficha.tipo || 'personaje' : tipoNuevo;
    let color = ficha ? ficha.color || RV.COLORES[5] : RV.COLORES[5];
    const nom = RV.entrada(ficha ? ficha.nombre : '', { 'data-autofocus': true });
    const ali = RV.entrada(ficha ? ficha.alias : '', { placeholder: 'la hechicera, Ald…' });
    const res = RV.entrada(ficha ? ficha.resumen : '', { placeholder: 'En una frase' });
    const tex = RV.areaTexto(ficha ? ficha.texto : '', { rows: '8', placeholder: 'Historia, personalidad, detalles…' });
    let campos = (ficha && Array.isArray(ficha.campos) ? ficha.campos : []).map((c) => ({ k: c.k || '', v: c.v || '' }));
    if (!ficha && !campos.length && camposNuevos) campos = camposNuevos.map((k) => ({ k, v: '' }));
    if (!ficha && !campos.length) campos = tipo === 'personaje' ? ['Edad', 'Aspecto', 'Quiere', 'Teme'].map((k) => ({ k, v: '' })) : tipo === 'lugar' ? ['Clima', 'Gente'].map((k) => ({ k, v: '' })) : [];
    const filas = h('div.e-campos');
    const pintaCampos = () => filas.replaceChildren(...campos.map((c, i) => h('div.e-campo-fila',
      RV.entrada(c.k, { placeholder: 'Dato', oninput: (ev) => { c.k = ev.target.value; } }), RV.entrada(c.v, { placeholder: 'Valor', oninput: (ev) => { c.v = ev.target.value; } }),
      h('button.enlace.apagado', { type: 'button', onclick: () => { campos.splice(i, 1); pintaCampos(); } }, '✕'))),
    h('button.enlace', { type: 'button', onclick: () => { campos.push({ k: '', v: '' }); pintaCampos(); } }, '+ Añadir dato'));
    pintaCampos();
    let vin = ficha && Array.isArray(ficha.vin) ? [...ficha.vin] : [];
    let tam = ficha ? num(ficha.tam) : 0;
    const destinos = destinosVinculo(pid);
    RV.dialogo({
      ante: ficha ? 'Editar la ficha' : 'Nueva ficha', titulo: ficha ? ficha.nombre : 'Una ficha del mundo', ancho: true, alCerrar,
      cuerpo: [chips(TIPOS_FICHA.map(([k, , s]) => [k, s]), tipo, (k) => { tipo = k; }), RV.campo('Nombre', nom),
        RV.campo('Otros nombres', ali, 'Separados por comas: así se reconoce en tus escenas (queda subrayado al escribirlo).'), RV.campo('Resumen', res),
        RV.campo('Datos', filas), RV.campo('Notas largas', tex), RV.campo('Color', colores(color, (c) => { color = c; })),
        h('div.campo', h('span', 'Tamaño en el árbol'), chips([[0, 'Según el tipo'], [1, 'Pequeña'], [2, 'Mediana'], [3, 'Grande']], tam, (k) => { tam = k; })),
        destinos.length ? h('div.campo', h('span', 'Vinculada a'), varios(destinos, vin, (l) => { vin = l; }), h('small', 'Sale también en la página de esa parte o ese capítulo.')) : null],
      acciones: [ficha ? { texto: 'Borrar', peligro: true, fn: (d) => { d.cerrar(); E.aPapelera(ficha); } } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: async (d) => {
          if (!nom.value.trim()) { RV.aviso('Ponle un nombre.', 'error'); return; }
          try {
            const c = { tipo, nombre: nom.value.trim(), alias: ali.value.trim(), resumen: res.value.trim(), texto: tex.value, color, campos: campos.filter((x) => x.k.trim() || x.v.trim()), vin, tam: tam || null };
            let f = ficha;
            if (ficha) await op('edita', ficha.id, c); else f = await op('nuevo', 'f', pid, c);
            d.cerrar();
            if (!ficha && E.alCrearFicha) { const fn = E.alCrearFicha; E.alCrearFicha = null; fn(f); }
          } catch (e) { E.err(e); }
        } }],
    });
  };

  E.dlgNota = (pid, nota, tipoNuevo = 'nota', vinNuevo = null) => {
    let tipo = nota ? nota.tipo || 'nota' : tipoNuevo;
    let color = nota ? nota.color || RV.COLORES[2] : RV.COLORES[2];
    let fija = !!(nota && nota.fija);
    const ti = RV.entrada(nota ? nota.titulo : '', { 'data-autofocus': true });
    const tx = RV.areaTexto(nota ? nota.texto : '', { rows: '10' });
    const fj = h('input', { type: 'checkbox', checked: fija ? '' : null, onchange: (e) => { fija = e.target.checked; } });
    let vin = nota && Array.isArray(nota.vin) ? [...nota.vin] : [...(vinNuevo || [])];
    const destinos = destinosVinculo(pid);
    RV.dialogo({
      ante: nota ? 'Editar la nota' : 'Nueva nota', titulo: nota ? nota.titulo || 'Nota' : 'Al tablón', ancho: true,
      cuerpo: [chips(TIPOS_NOTA.map(([k, , s]) => [k, s]), tipo, (k) => { tipo = k; }), RV.campo('Título', ti), RV.campo('Texto', tx, 'Una plantilla es un texto que puedes tener al lado mientras escribes o insertar en una escena.'),
        RV.campo('Color', colores(color, (c) => { color = c; })), h('label.e-check', fj, ' Fijada arriba'),
        destinos.length ? h('div.campo', h('span', 'Vinculada a'), varios(destinos, vin, (l) => { vin = l; }), h('small', 'Sale también en la página de esa parte o ese capítulo (y aquí, en Notas).')) : null],
      acciones: [nota ? { texto: 'Borrar', peligro: true, fn: (d) => { d.cerrar(); E.aPapelera(nota); } } : null, 'hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() },
        { texto: 'Guardar', principal: true, fn: async (d) => {
          if (!ti.value.trim() && !tx.value.trim()) { d.cerrar(); return; }
          try { const c = { tipo, titulo: ti.value.trim(), texto: tx.value, color, fija: fija ? 1 : 0, vin }; if (nota) await op('edita', nota.id, c); else await op('nuevo', 'n', pid, c); d.cerrar(); } catch (e) { E.err(e); }
        } }],
    });
  };

  // ---------------------------------------------------------------- Modo lectura
  // Lo que ya existe se abre para leerlo; para cambiarlo, «Editar». Lo nuevo se abre ya para rellenarlo.
  const parrafos = (t) => String(t || '').split(/\n{2,}|\n/).map((x) => x.trim()).filter(Boolean).map((x) => h('p.texto', x));
  const vinculosDe = (d) => (Array.isArray(d.vin) ? d.vin : []).map((id) => E.get(id)).filter(Boolean);
  const irA = (x) => { RV.cerrarDialogo(); RV.ir(x.t === 'a' ? { v: 'parte', id: x.id } : { v: 'capitulo', id: x.id }); };
  const enlacesVinculos = (d) => {
    const l = vinculosDe(d);
    return l.length ? h('div.e-lee-sec', h('div.versal', 'Vinculada a'), h('div.e-chips', l.map((x) => h('button.e-chip', { type: 'button', onclick: () => irA(x) }, x.t === 'a' ? `Parte · ${x.titulo || ''}` : `Cap. ${E.numeroCapitulo(x)} · ${x.titulo || ''}`)))) : null;
  };
  E.verFicha = (pid, f) => {
    if (!f) { E.dlgFicha(pid, null); return; }
    const tipo = (TIPOS_FICHA.find((t) => t[0] === f.tipo) || [])[2] || 'Ficha';
    const sale = E.escenasDe(pid).filter((s) => E.fichasEn(pid, s.texto).some((x) => x.id === f.id));
    const rels = E.relacionesDe(f.id).map((r) => ({ r, otra: E.get(r.a === f.id ? r.b : r.a) })).filter((x) => x.otra);
    const datos = (Array.isArray(f.campos) ? f.campos : []).filter((c) => String(c.k || '').trim() || String(c.v || '').trim());
    RV.dialogo({
      ante: `${tipo}${f.alias ? ` · también «${f.alias}»` : ''}`, titulo: f.nombre || 'Sin nombre', ancho: true, clase: 'e-lee',
      cuerpo: [
        h('span.e-lee-color', { style: { background: colorDe(f.color) } }),
        f.resumen ? h('p.e-lee-entradilla', f.resumen) : null,
        datos.length ? h('ul.datos', datos.map((c) => RV.dato(c.k || '—', c.v || '—'))) : null,
        f.texto ? h('div.e-lee-sec', ...parrafos(f.texto)) : null,
        enlacesVinculos(f),
        h('div.e-lee-sec', h('div.versal', rels.length ? `Uniones · ${rels.length}` : 'Uniones'),
          rels.length ? h('div.e-uniones', rels.map(({ r, otra }) => h('div.e-union',
            h('button.e-union-tit', { type: 'button', title: 'Ver la unión', onclick: () => { RV.cerrarDialogo(); E.verRelacion(pid, r); } }, `${r.flecha && r.b === f.id ? '← ' : ''}${(r.titulo || 'Unión').toUpperCase()}${r.flecha && r.a === f.id ? ' →' : ''}`),
            h('button.enlace-serif', { type: 'button', onclick: () => { RV.cerrarDialogo(); E.verFicha(pid, otra); } }, otra.nombre || 'Sin nombre'),
            r.texto ? h('span.cursiva', String(r.texto).split('\n')[0].slice(0, 140)) : null)))
            : h('p.apagado', 'Todavía no está unida a nada. Únela en la pestaña Árbol.')),
        sale.length ? h('div.e-lee-sec', h('div.versal', `Sale en ${RV.plural(sale.length, 'escena', 'escenas')}`), h('div.e-chips', sale.slice(0, 40).map((x) => h('button.e-chip', { type: 'button', onclick: () => { RV.cerrarDialogo(); RV.ir({ v: 'editor', id: x.id }); } }, x.titulo || 'Sin título')))) : null],
      acciones: [{ texto: 'Ver en el árbol', fn: (d) => { d.cerrar(); RV.ir({ v: 'obra', id: pid, tab: TAB_ARBOL, foco: f.id }); } }, 'hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, { texto: 'Editar', principal: true, fn: (d) => { d.cerrar(); E.dlgFicha(pid, f); } }],
    });
  };
  E.verNota = (pid, n) => {
    if (!n) { E.dlgNota(pid, null); return; }
    const tipo = (TIPOS_NOTA.find((t) => t[0] === n.tipo) || [])[2] || 'Nota';
    RV.dialogo({
      ante: `${tipo}${n.fija ? ' · fijada' : ''}`, titulo: n.titulo || tipo, ancho: true, clase: 'e-lee',
      cuerpo: [h('span.e-lee-color', { style: { background: colorDe(n.color) } }), n.texto ? h('div.e-lee-sec', ...parrafos(n.texto)) : h('p.apagado', 'Sin texto.'), enlacesVinculos(n)],
      acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, { texto: 'Editar', principal: true, fn: (d) => { d.cerrar(); E.dlgNota(pid, n); } }],
    });
  };
  E.parrafos = parrafos;

  // La papelera: lo borrado de los últimos 30 días, para recuperarlo (vuelve a su sitio) o borrarlo para siempre
  const PLURAL = { c: ['capítulo', 'capítulos'], e: ['escena', 'escenas'], f: ['ficha', 'fichas'], n: ['nota', 'notas'], h: ['hito', 'hitos'], b: ['borrador', 'borradores'] };
  const cuantos = (tipos) => {
    const n = {}; for (const t of tipos) n[t] = (n[t] || 0) + 1;
    const l = Object.keys(PLURAL).filter((t) => n[t]).map((t) => `${n[t]} ${n[t] === 1 ? PLURAL[t][0] : PLURAL[t][1]}`);
    return l.length > 1 ? `${l.slice(0, -1).join(', ')} y ${l[l.length - 1]}` : l[0] || '';
  };
  E.dlgPapelera = () => {
    const seguro = new Set();
    let d = null;
    const fila = ({ d: x, con, obra }) => {
      const cuando = num(x.dt || x.u);
      const quedan = Math.max(0, Math.floor((cuando + 30 * 86400000 - Date.now()) / 86400000));
      const linea = String(x.t === 'f' ? x.resumen || '' : x.t === 'p' ? x.sinopsis || '' : x.t === 'c' ? x.resumen || '' : x.texto || '').split('\n').find((l) => l.trim() && !l.startsWith('#')) || '';
      const meta = [con.length ? `Con ${cuantos(con)}` : '', x.t === 'e' || x.t === 'b' ? `${cifra(E.palabras(x.texto))} palabras` : '', `borrado ${hace(cuando)}`, quedan <= 1 ? 'se borra del todo mañana' : `quedan ${quedan} días`].filter(Boolean).join(' · ');
      return h('div.e-pap',
        h('div.versal.acento', `${TIPO[x.t] || 'Escrito'}${obra ? ` · ${obra}` : ''}`),
        h('div.e-pap-titulo', tituloDe(x)),
        linea ? h('div.e-pap-linea', linea) : null,
        h('div.e-pap-meta', meta),
        h('div.fila-botones',
          h('button.boton-tinta', { type: 'button', onclick: async () => { try { await op('restaura', x.id); RV.aviso(`«${tituloDe(x)}» ha vuelto a su sitio`); pinta(); } catch (e) { E.err(e); } } }, 'Recuperar'),
          h('button.enlace' + (seguro.has(x.id) ? '.acento' : '.apagado'), { type: 'button', onclick: async () => {
            if (!seguro.has(x.id)) { seguro.add(x.id); pinta(); return; }
            try { await op('purga', x.id); pinta(); } catch (e) { E.err(e); }
          } }, seguro.has(x.id) ? 'Sí, para siempre' : 'Borrar para siempre')));
    };
    const pinta = async () => {
      let lista = [];
      try { lista = await op('papeleraDetalle'); } catch (e) { E.err(e); }
      if (!d) return;
      reemplaza(d.cuerpo, h('p.texto', 'Lo que borras se queda aquí 30 días, también en tus otros dispositivos. Al recuperarlo vuelve a su sitio, con lo que se fue con ello.'),
        lista.length ? lista.map(fila) : h('p.e-pap-vacia', 'La papelera está vacía.'));
      const b = d.pie.querySelector('.e-vaciar');
      if (b) b.hidden = !lista.length;
    };
    d = RV.dialogo({
      ante: 'Escribir', titulo: 'Papelera', ancho: true, cuerpo: [h('p.texto', 'Cargando…')],
      acciones: [{ texto: 'Vaciar la papelera', peligro: true, clase: 'e-vaciar', fn: () => RV.confirmar('¿Vaciar la papelera?', 'Se borrará para siempre todo lo que hay en ella, en todos tus dispositivos.', 'Vaciar', async () => { try { await op('vaciaPapelera'); pinta(); } catch (e) { E.err(e); } }) },
        'hueco', { texto: 'Cerrar', principal: true, fn: (x) => x.cerrar() }],
    });
    pinta();
  };

  // Copias de seguridad: Atrio guarda solo, en una carpeta de este equipo (aparte de tu cuenta), una copia completa de todo lo
  // escrito cada vez que algo cambia. De cualquier copia se saca una obra como obra NUEVA: nunca pisa lo que tienes ahora.
  E.dlgCopias = () => {
    let d = null;
    const abiertas = new Set();
    const cuando = (t) => new Date(t).toLocaleString('es-ES', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
    const fila = (c, obras) => h('div.e-pap',
      h('div.versal.acento', cuando(c.t)),
      h('div.e-pap-titulo', `${RV.plural(c.obras, 'obra', 'obras')} · ${cifra(c.palabras)} palabras`),
      h('div.e-pap-meta', `${Math.max(1, Math.round(c.bytes / 1024))} KB · ${c.archivo}`),
      abiertas.has(c.archivo)
        ? h('div.e-copia-obras', (obras || []).map((o) => h('div.e-copia-obra',
          h('span', `${o.titulo} · ${RV.plural(o.escenas, 'escena', 'escenas')} · ${cifra(o.palabras)} palabras`),
          h('button.boton-tinta', { type: 'button', onclick: async () => {
            try {
              const r = await api.escritosCopiaRestaurar(c.archivo, o.id);
              RV.aviso(`Sacada como obra nueva: «${r.titulo}»`);
              if (d) d.cerrar();
              RV.ir({ v: 'obra', id: r.id, tab: 0 });
            } catch (e) { E.err(e); }
          } }, 'Sacar como obra nueva'))))
        : h('div.fila-botones', h('button.enlace', { type: 'button', onclick: () => { abiertas.add(c.archivo); pinta(); } }, 'Ver las obras de esta copia →')));
    const pinta = async () => {
      let r = { carpeta: '', lista: [] };
      try { r = await api.escritosCopias(); } catch (e) { E.err(e); }
      if (!d) return;
      const detalle = {};
      for (const c of r.lista) if (abiertas.has(c.archivo)) { try { detalle[c.archivo] = await api.escritosCopiaObras(c.archivo); } catch (e) { E.err(e); } }
      reemplaza(d.cuerpo,
        h('p.texto', 'Atrio guarda solo una copia completa de todo lo que escribes (también lo de la papelera) en una carpeta de este equipo, aparte de tu cuenta: la primera a los pocos segundos de abrir, luego cada cuarto de hora si algo ha cambiado, y al cerrar. Se conservan las últimas 40. Sacar una obra de una copia la deja como obra nueva: no toca lo que tienes ahora.'),
        r.carpeta ? h('p.versal', r.carpeta) : null,
        r.lista.length ? r.lista.map((c) => fila(c, detalle[c.archivo])) : h('p.e-pap-vacia', 'Todavía no hay ninguna copia: se hará la primera enseguida.'));
    };
    d = RV.dialogo({
      ante: 'Escribir', titulo: 'Copias de seguridad', ancho: true, cuerpo: [h('p.texto', 'Cargando…')],
      acciones: [
        { texto: 'Hacer una copia ahora', fn: async () => { try { const c = await api.escritosCopiaAhora(); RV.aviso(c ? 'Copia hecha.' : 'No hay nada nuevo desde la última copia.'); pinta(); } catch (e) { E.err(e); } } },
        { texto: 'Abrir la carpeta', fn: async () => { try { await api.escritosCopiaCarpeta(); } catch (e) { E.err(e); } } },
        'hueco', { texto: 'Cerrar', principal: true, fn: (x) => x.cerrar() }],
    });
    pinta();
  };

  // Comparar con otro texto (un borrador o una versión): diferencia a diferencia, eliges de cada una
  E.dlgCompara = (actual, nombre, otro, alAplicar) => {
    const ET = RV.ET;
    const partes = ET.compara(actual, otro);
    const cambios = partes.filter((p) => p.aqui);
    const usa = cambios.map(() => false);
    const cuerpo = [h('p.texto', cambios.length ? `${RV.plural(cambios.length, 'diferencia', 'diferencias')}. Toca cada una para elegir qué texto te quedas.` : 'Son iguales.')];
    let k = 0;
    for (const p of partes) {
      if (p.igual) { const t = p.igual.join('\n\n'); cuerpo.push(h('p.e-igual', t.length > 200 ? `${t.slice(0, 100)} … ${t.slice(-80)}` : t)); continue; }
      const i = k++;
      const a = h('button.e-lado', { type: 'button', onclick: () => { usa[i] = false; pinta(); } }, h('span.versal', 'El actual'), h('span.e-texto', p.aqui.join('\n\n') || '(nada)'));
      const b = h('button.e-lado', { type: 'button', onclick: () => { usa[i] = true; pinta(); } }, h('span.versal', 'Este'), h('span.e-texto', p.otro.join('\n\n') || '(nada)'));
      const pinta = () => { a.setAttribute('aria-pressed', usa[i] ? 'false' : 'true'); b.setAttribute('aria-pressed', usa[i] ? 'true' : 'false'); };
      pinta();
      cuerpo.push(h('div.e-cambio', a, b));
    }
    RV.dialogo({
      ante: 'Comparar', titulo: nombre, ancho: true, cuerpo,
      acciones: ['hueco', { texto: 'Cerrar', fn: (d) => d.cerrar() }, cambios.length ? { texto: 'Quedarme con este', fn: (d) => { d.cerrar(); alAplicar(otro); } } : null, cambios.length ? { texto: 'Aplicar la mezcla', principal: true, fn: (d) => { d.cerrar(); alAplicar(ET.mezcla(partes, usa)); } } : null],
    });
  };

  // ---------------------------------------------------------------- La biblioteca de obras
  const resumenHoy = () => {
    const obras = E.obras().filter((o) => !o.arch);
    const hoy = E.escritas(RV.hoy());
    const diaria = obras.reduce((s, o) => s + num(o.diaria), 0);
    const total = obras.reduce((s, o) => s + E.palabrasObra(o.id), 0);
    const dias = E.ultimosDias(14).map((x) => x[1]);
    return h('div.e-hoy',
      h('div.e-cifras', h('div', h('b.grande' + (diaria && hoy >= diaria ? '.hecho' : ''), cifra(hoy)), h('span.versal', diaria ? `Hoy · de ${cifra(diaria)}` : 'Palabras hoy')),
        h('div', h('b', String(E.racha())), h('span.versal', E.racha() === 1 ? 'día seguido' : 'días seguidos')),
        h('div', h('b', cifra(total)), h('span.versal', 'palabras en total'))),
      diaria ? barra(hoy / diaria, hoy >= diaria ? '#5B8C5A' : null) : null, barras(dias));
  };

  const tarjeta = (o) => {
    const pal = E.palabrasObra(o.id); const es = E.escenasDe(o.id); const meta = num(o.meta);
    const ultima = es.reduce((m, e) => Math.max(m, num(e.u)), num(o.u));
    return h('button.e-obra', { type: 'button', style: { '--c': colorDe(o.color) }, onclick: () => RV.ir({ v: 'obra', id: o.id, tab: 0 }) },
      h('span.franja'), h('span.cuerpo',
        h('span.titulo', o.titulo || 'Sin título'),
        h('span.versal', [o.autor, o.genero].filter(Boolean).join(' · ')),
        o.sinopsis ? h('span.sinopsis', o.sinopsis) : null,
        meta ? barra(pal / meta, colorDe(o.color)) : null,
        h('span.pie', `${cifra(pal)}${meta ? ` de ${cifra(meta)} palabras · ${Math.min(999, Math.floor((pal * 100) / meta))} %` : ' palabras'} · ${RV.plural(es.length, 'escena', 'escenas')} · editada ${hace(ultima)}`)));
  };

  // La última escena que tocaste, para seguir donde lo dejaste
  const ultimaLinea = (s) => String(s.texto || '').split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#') && l !== '***').pop() || '';
  function sigue(s) {
    const obra = E.get(s.pid) || {};
    const l = ultimaLinea(s);
    return h('button.e-sigue', { type: 'button', onclick: () => RV.ir({ v: 'editor', id: s.id }) },
      h('span.versal.acento', 'Sigue escribiendo'), h('span.e-sigue-titulo', s.titulo || 'Sin título'),
      l ? h('span.e-sigue-linea', `…${l.length > 160 ? l.slice(-160) : l}`) : null,
      h('span.e-sigue-pie', `${obra.titulo || 'Sin título'} · ${cifra(num(s.palabras))} palabras · editada ${hace(num(s.u))} · Abrir →`));
  }
  // «Escribiendo» en la portada de la revista: la obra, lo de hoy y seguir con la última escena
  E.bloquePortada = () => {
    if (!E.cargado()) return null;
    const s = E.ultimaEscena();
    if (!s) return null;
    const obra = E.get(s.pid) || {};
    const total = E.palabrasObra(obra.id); const meta = num(obra.meta); const hoy = E.escritas(RV.hoy()); const racha = E.racha();
    return h('section.p-escribiendo', RV.seccion('Escribiendo', { texto: 'Escribir →', fn: () => RV.ir({ v: 'escribir' }) }),
      h('button.e-p-obra', { type: 'button', onclick: () => RV.ir({ v: 'obra', id: obra.id, tab: 0 }) }, h('span.titular', obra.titulo || 'Sin título')),
      h('p.versal', [obra.genero, `${cifra(total)}${meta ? ` de ${cifra(meta)}` : ''} palabras`, hoy ? `hoy ${cifra(hoy)}` : null, racha > 1 ? `${racha} días seguidos` : null].filter(Boolean).join(' · ')),
      meta ? barra(total / meta, colorDe(obra.color)) : null,
      h('button.enlace.acento', { type: 'button', onclick: () => RV.ir({ v: 'editor', id: s.id }) }, `Seguir con «${s.titulo || 'Sin título'}» →`));
  };

  let archivoAbierto = false;
  RV.vistas.escribir = () => {
    const root = h('div.hoja.escribir');
    const obras = E.obras();
    const activas = obras.filter((o) => !o.arch).sort((a, b) => E.escenasDe(b.id).reduce((m, e) => Math.max(m, num(e.u)), num(b.u)) - E.escenasDe(a.id).reduce((m, e) => Math.max(m, num(e.u)), num(a.u)));
    const arch = obras.filter((o) => o.arch);
    // (Escribir es una sección de la cabecera, como Diario o Cifras: sin «← Volver» propio; el de la cabecera vuelve)
    root.append(h('header.cabeza', h('div.ante', E.escritas(RV.hoy()) > 0 ? `Hoy · ${cifra(E.escritas(RV.hoy()))} palabras` : 'Tus obras'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Escribir'),
        h('div.herramientas', h('button.enlace', { type: 'button', onclick: importar }, 'Importar un texto'), h('button.boton-tinta', { type: 'button', onclick: () => E.dlgObra(null) }, 'Nueva obra'))),
      h('div.filete')));
    if (!E.cargado()) { root.append(RV.vacio('Cargando…')); return root; }
    root.append(resumenHoy());
    const ult = E.ultimaEscena();
    if (ult) root.append(sigue(ult));
    const st = estadoNube();
    if (st) root.append(h('p.e-nube' + (estado.error && estado.pendientes ? '.mal' : ''), st));
    if (!activas.length) {
      root.append(h('div.e-vacio', h('h2.titular', 'Aquí empieza tu novela.'), h('p', 'Crea una obra y escribe por capítulos y escenas, con fichas de personajes y lugares, notas, borradores e historial. Se guarda sola, funciona sin conexión y aparece en todos tus dispositivos con tu cuenta.'),
        h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: () => E.dlgObra(null) }, 'Nueva obra'), h('button.enlace', { type: 'button', onclick: importar }, 'Importar un texto'))));
    } else root.append(h('div.e-obras', activas.map(tarjeta)));
    if (arch.length) {
      root.append(h('button.enlace.apagado', { type: 'button', style: { marginTop: '24px' }, onclick: () => { archivoAbierto = !archivoAbierto; RV.refrescar(); } }, `Archivadas ${archivoAbierto ? '▴' : '▾'} ${arch.length}`));
      if (archivoAbierto) root.append(h('div.e-obras', arch.map(tarjeta)));
    }
    root.append(h('button.enlace.apagado.e-ir-papelera', { type: 'button', onclick: () => E.dlgPapelera() }, `Papelera${estado.papelera ? ` · ${estado.papelera}` : ''}`));
    root.append(h('button.enlace.apagado.e-ir-papelera', { type: 'button', onclick: () => E.dlgCopias() }, 'Copias de seguridad'));
    return root;
  };
  async function importar() {
    try {
      const id = await api.escritosImportar();
      if (id) { RV.aviso('Importada. Ya la tienes en tus obras.'); RV.ir({ v: 'obra', id, tab: 0 }); }
    } catch (e) { E.err(e); }
  }

  // ---------------------------------------------------------------- Una obra
  const TABS = ['Manuscrito', 'Fichas', 'Árbol', 'Notas', 'Línea', 'Cifras', 'Guía'];
  const TAB_ARBOL = 2;
  const TAB_GUIA = 6;
  E.TAB_ARBOL = TAB_ARBOL; E.TAB_GUIA = TAB_GUIA;
  RV.vistas.obra = (ruta) => {
    const obra = E.get(ruta.id);
    if (!obra) { RV.volver() || RV.ir({ v: 'escribir' }); return h('div.hoja'); }
    const tab = num(ruta.tab);
    const root = h('div.hoja.escribir');
    root.append(h('header.cabeza', h('div.ante', h('button.enlace.e-atras', { type: 'button', onclick: () => RV.ir({ v: 'escribir' }) }, '← Tus obras'), ` · ${obra.genero || 'Obra'}`),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, obra.titulo || 'Sin título'),
        h('div.herramientas', h('button.enlace', { type: 'button', onclick: () => buscarObra(obra) }, 'Buscar'), h('button.enlace', { type: 'button', onclick: () => exportar(obra) }, 'Exportar'), h('button.enlace', { type: 'button', onclick: () => E.dlgObra(obra) }, 'Ajustes'))),
      obra.autor ? h('p.entradilla', obra.autor) : null, h('div.filete')));
    root.append(h('nav.e-tabs', TABS.map((n, i) => h('button', { type: 'button', 'aria-current': i === tab ? 'page' : null, onclick: () => RV.ir({ v: 'obra', id: obra.id, tab: i }, { reemplazar: true }) }, n))));
    const vistas = [(o) => E.manuscrito(o), fichas, (o) => E.arbol(o, ruta), notas, (o) => E.linea(o), cifras, guiaNovela];
    root.append((vistas[tab] || vistas[0])(obra));
    return root;
  };

  let esquema = false;
  let etiqueta = { pid: '', t: '' };
  // La vista de lista del manuscrito: todos los capítulos con sus escenas (los cuadrados están en escribir-partes.js)
  E.manuscritoLista = (obra, raizMadre) => manuscrito(obra, raizMadre);
  E.tarjetaGuia = (obra) => tarjetaGuia(obra);
  E.menuEscena = (s, caps) => menuEscena(s, caps);
  function manuscrito(obra, raizMadre) {
    const pid = obra.id; const caps = E.capitulos(pid); const pal = E.palabrasObra(pid); const meta = num(obra.meta);
    const ests = [0, 0, 0]; for (const e of E.escenasDe(pid)) ests[Math.min(2, num(e.estado))]++;
    const raiz = raizMadre || h('div.e-manu');
    if (!raizMadre) { const sig = tarjetaGuia(obra); if (sig) raiz.append(sig); }
    if (!raizMadre) raiz.append(h('div.e-resumen', h('span', `${cifra(pal)}${meta ? ` de ${cifra(meta)} palabras` : ' palabras'} · ${RV.plural(caps.length, 'capítulo', 'capítulos')}`),
      meta ? barra(pal / meta, colorDe(obra.color)) : null,
      h('div.e-estados', ESTADOS.map((n, i) => h('span', h('i', { style: { background: COL_ESTADO[i] } }), `${ests[i]} ${n.toLowerCase()}`)),
        h('button.e-chip', { type: 'button', 'aria-pressed': esquema ? 'true' : 'false', title: 'Enseña el resumen de cada escena (el del panel de la escena) en vez del principio del texto', onclick: () => { esquema = !esquema; RV.refrescar(); } }, esquema ? 'Ver el texto' : 'Ver resúmenes'))));
    // Filtrar por etiqueta (#flashback, #Aldara…): solo las escenas que la llevan
    const cuenta = new Map();
    for (const e of E.escenasDe(pid)) for (const t of e.etq || []) { const x = String(t).trim(); if (x) cuenta.set(x, (cuenta.get(x) || 0) + 1); }
    const etqs = [...cuenta.entries()].sort((a, b) => b[1] - a[1]).map((x) => x[0]);
    if (etiqueta.pid !== pid || !etqs.includes(etiqueta.t)) etiqueta = { pid, t: '' };
    const pasa = (s) => !etiqueta.t || (s.etq || []).some((x) => String(x).trim() === etiqueta.t);
    if (etqs.length) raiz.append(h('div.e-chips.e-filtro', [['', 'Todas'], ...etqs.map((t) => [t, `#${t}`])].map(([t, n]) => h('button.e-chip', { type: 'button', 'aria-pressed': etiqueta.t === t ? 'true' : 'false', onclick: () => { etiqueta = { pid, t: etiqueta.t === t ? '' : t }; RV.refrescar(); } }, n))));
    let parte = '';
    let arrastrando = null;
    const suelta = async (fn) => { try { await fn(); } catch (e) { E.err(e); } };
    caps.forEach((c, ci) => {
      const esc = E.escenas(c.id).filter(pasa);
      if (etiqueta.t && !esc.length) return;
      const pa = c.aid ? E.get(c.aid) : null;
      const np = pa ? pa.id : String(c.parte || '');
      if (np && np !== parte) { parte = np; raiz.append(h('button.e-parte', { type: 'button', onclick: () => (pa ? RV.ir({ v: 'parte', id: pa.id }) : null) }, pa ? pa.titulo || 'Parte' : c.parte)); }
      const cab = h('div.e-cap', {
        ondragover: (ev) => { if (arrastrando && arrastrando.esc) { ev.preventDefault(); cab.classList.add('sobre'); } }, ondragleave: () => cab.classList.remove('sobre'),
        ondrop: (ev) => { ev.preventDefault(); cab.classList.remove('sobre'); const a = arrastrando; arrastrando = null; if (a && a.esc) suelta(() => op('mueveEscena', a.esc, c.id, null, esc[0] && esc[0].id !== a.esc ? esc[0].id : null)); } },
      h('div', h('span.versal', `Capítulo ${ci + 1}`), h('button.e-cap-titulo', { type: 'button', onclick: () => RV.ir({ v: 'capitulo', id: c.id }) }, c.titulo || 'Sin título'), c.resumen ? h('span.cursiva', c.resumen) : null),
      h('span.versal', cifra(E.palabrasCapitulo(c.id))),
      h('button.enlace.apagado', { type: 'button', title: 'Editar el capítulo', onclick: () => E.dlgCapitulo(pid, c) }, '⋯'));
      raiz.append(cab);
      esc.forEach((s, si) => {
        const prev = esc[si - 1];
        const primera = String(s.texto || '').split('\n').find((l) => l.trim() && !l.startsWith('#')) || '';
        const fila = h('div.e-escena', { draggable: 'true', ondragstart: (ev) => { arrastrando = { esc: s.id }; ev.dataTransfer.effectAllowed = 'move'; ev.stopPropagation(); },
          ondragover: (ev) => { if (arrastrando && arrastrando.esc) { ev.preventDefault(); fila.classList.add('sobre'); } }, ondragleave: () => fila.classList.remove('sobre'),
          ondrop: (ev) => { ev.preventDefault(); ev.stopPropagation(); fila.classList.remove('sobre'); const a = arrastrando; arrastrando = null; if (a && a.esc && a.esc !== s.id) suelta(() => op('mueveEscena', a.esc, c.id, prev && prev.id !== a.esc ? prev.id : null, s.id)); } },
        h('button.e-punto', { type: 'button', title: `${ESTADOS[Math.min(2, num(s.estado))]} (clic para cambiar)`, style: { background: COL_ESTADO[Math.min(2, num(s.estado))] }, onclick: () => suelta(() => op('edita', s.id, { estado: (num(s.estado) + 1) % 3 })) }),
        h('button.e-escena-cuerpo', { type: 'button', 'data-clave': `esc-${s.id}`, onclick: () => RV.ir({ v: 'editor', id: s.id }) },
          h('span.e-escena-titulo', s.titulo || 'Sin título'),
          h('span.e-escena-texto' + (esquema ? '.largo' : ''), esquema ? (s.resumen || primera) : primera),
          s.cuando || (s.etq && s.etq.length) ? h('span.e-escena-meta', [s.cuando, ...(s.etq || []).map((x) => `#${x}`)].filter(Boolean).join(' · ')) : null),
        h('span.versal', cifra(num(s.palabras))),
        h('button.enlace.apagado', { type: 'button', title: 'Más', onclick: () => menuEscena(s, caps) }, '⋯'));
        raiz.append(fila);
      });
      if (!etiqueta.t) raiz.append(h('button.enlace.acento.e-mas', { type: 'button', onclick: async () => { try { const n = await op('nuevo', 'e', pid, { cid: c.id, titulo: `Escena ${esc.length + 1}` }); RV.ir({ v: 'editor', id: n.id }); } catch (e) { E.err(e); } } }, '+ Escena'));
    });
    if (!raizMadre) raiz.append(h('div.fila-botones', h('button.boton-borde', { type: 'button', onclick: () => E.dlgCapitulo(pid, null) }, 'Nuevo capítulo')));
    return raiz;
  }

  function menuEscena(s, caps) {
    const pid = s.pid;
    const hermanas = E.escenas(s.cid); const i = hermanas.findIndex((x) => x.id === s.id);
    const acc = (t, fn) => h('button.enlace-serif', { type: 'button', onclick: async () => { RV.cerrarDialogo(); try { await fn(); } catch (e) { E.err(e); } } }, t);
    const ci = caps.findIndex((c) => c.id === s.cid);
    const d = RV.dialogo({
      ante: 'Escena', titulo: s.titulo || 'Sin título',
      cuerpo: h('div.e-menu',
        acc('Abrir', () => RV.ir({ v: 'editor', id: s.id })),
        acc('Subir', () => (i > 0 ? op('mueveEscena', s.id, s.cid, hermanas[i - 2] ? hermanas[i - 2].id : null, hermanas[i - 1].id) : ci > 0 ? op('mueveEscena', s.id, caps[ci - 1].id, (E.escenas(caps[ci - 1].id).slice(-1)[0] || {}).id || null, null) : null)),
        acc('Bajar', () => (i < hermanas.length - 1 ? op('mueveEscena', s.id, s.cid, hermanas[i + 1].id, hermanas[i + 2] ? hermanas[i + 2].id : null) : ci < caps.length - 1 ? op('mueveEscena', s.id, caps[ci + 1].id, null, (E.escenas(caps[ci + 1].id)[0] || {}).id || null) : null)),
        h('span.versal', 'Mover a otro capítulo'),
        ...caps.filter((c) => c.id !== s.cid).map((c) => acc(c.titulo || 'Sin título', () => op('mueveEscena', s.id, c.id, (E.escenas(c.id).slice(-1)[0] || {}).id || null, null))),
        acc('Duplicar', () => op('duplicaEscena', s.id)),
        h('button.enlace-serif.acento', { type: 'button', onclick: () => { d.cerrar(); E.aPapelera(s); } }, 'Borrar')),
      acciones: [{ texto: 'Cerrar', fn: (x) => x.cerrar() }],
    });
    return pid && d;
  }

  function fichas(obra) {
    const pid = obra.id; let tipo = '';
    const raiz = h('div.e-fichas');
    const lista = h('div.e-lista');
    const fs = E.fichas(pid);
    const sale = new Map();
    for (const s of E.escenasDe(pid)) for (const f of E.fichasEn(pid, s.texto)) sale.set(f.id, (sale.get(f.id) || 0) + 1);
    const pinta = () => {
      reemplaza(raiz,
        h('div.e-chips', h('button.e-chip', { type: 'button', 'aria-pressed': tipo === '' ? 'true' : 'false', onclick: () => { tipo = ''; pinta(); } }, `Todas ${fs.length}`),
          ...TIPOS_FICHA.map(([k, n]) => { const c = fs.filter((f) => f.tipo === k).length; return c || tipo === k ? h('button.e-chip', { type: 'button', 'aria-pressed': tipo === k ? 'true' : 'false', onclick: () => { tipo = k; pinta(); } }, `${n} ${c}`) : null; })),
        !fs.length ? h('div.e-vacio', h('h2.titular', 'El mundo de tu historia'), h('p', 'Personajes, lugares, objetos y reglas del mundo en fichas. Cuando escribas su nombre (o un alias) en una escena, quedará subrayado y lo tendrás en el panel, con sus datos, sin salir del texto.')) : null,
        h('div.e-rejilla', fs.filter((f) => !tipo || f.tipo === tipo).sort((a, b) => String(a.nombre).localeCompare(String(b.nombre), 'es')).map((f) => h('button.e-ficha-tarj', { type: 'button', style: { '--c': colorDe(f.color) }, onclick: () => E.verFicha(pid, f) },
          h('span.punto'), h('span.titulo', f.nombre), h('span.versal', `${(TIPOS_FICHA.find((t) => t[0] === f.tipo) || [])[2] || 'Ficha'} · ${sale.get(f.id) ? `sale en ${RV.plural(sale.get(f.id), 'escena', 'escenas')}` : 'aún no sale'}`), f.resumen ? h('span.e-res', f.resumen) : null))),
        h('div.fila-botones', h('button.boton-borde', { type: 'button', onclick: () => E.dlgFicha(pid, null, tipo || 'personaje') }, 'Nueva ficha')));
    };
    pinta();
    return raiz && lista && raiz;
  }

  function notas(obra) {
    const pid = obra.id; let tipo = ''; let de = '';
    const raiz = h('div.e-notas');
    const ns = E.notas(pid);
    const partes = E.partes(pid);
    const pinta = () => {
      const lista = ns.filter((n) => (!tipo || n.tipo === tipo) && (!de || (Array.isArray(n.vin) && (n.vin.includes(de) || n.vin.some((v) => (E.get(v) || {}).aid === de))))).sort((a, b) => num(b.fija) - num(a.fija) || b.u - a.u);
      reemplaza(raiz,
        h('div.e-chips', h('button.e-chip', { type: 'button', 'aria-pressed': tipo === '' ? 'true' : 'false', onclick: () => { tipo = ''; pinta(); } }, `Todo ${ns.length}`),
          ...TIPOS_NOTA.map(([k, n]) => { const c = ns.filter((x) => x.tipo === k).length; return c || tipo === k ? h('button.e-chip', { type: 'button', 'aria-pressed': tipo === k ? 'true' : 'false', onclick: () => { tipo = k; pinta(); } }, `${n} ${c}`) : null; })),
        partes.length ? h('div.e-chips.e-filtro', h('button.e-chip', { type: 'button', 'aria-pressed': de === '' ? 'true' : 'false', onclick: () => { de = ''; pinta(); } }, 'De toda la obra'),
          ...partes.map((a) => h('button.e-chip', { type: 'button', 'aria-pressed': de === a.id ? 'true' : 'false', onclick: () => { de = de === a.id ? '' : a.id; pinta(); } }, a.titulo || 'Parte'))) : null,
        !ns.length ? h('div.e-vacio', h('h2.titular', 'El tablón'), h('p', 'Ideas sueltas, investigación, cosas por arreglar y plantillas (como una ficha de escena) que puedes tener junto al texto mientras escribes.')) : null,
        h('div.e-tablon', lista.map((n) => h('button.e-nota', { type: 'button', style: { '--c': colorDe(n.color) }, onclick: () => E.verNota(pid, n) },
          h('span.versal', `${(TIPOS_NOTA.find((t) => t[0] === n.tipo) || [])[2] || 'Nota'}${n.fija ? ' · fijada' : ''}${vinculosDe(n).length ? ` · ${vinculosDe(n).map((x) => (x.t === 'a' ? x.titulo : `cap. ${E.numeroCapitulo(x)}`)).join(', ')}` : ''}`), n.titulo ? h('span.titulo', n.titulo) : null, n.texto ? h('span.cuerpo' + (n.tipo === 'idea' ? '.cursiva' : ''), n.texto) : null))),
        h('div.fila-botones', h('button.boton-borde', { type: 'button', onclick: () => E.dlgNota(pid, null, tipo || 'nota') }, 'Nueva nota')));
    };
    pinta();
    return raiz;
  }

  function cifras(obra) {
    const pid = obra.id; const caps = E.capitulos(pid); const esc = E.escenasDe(pid);
    const total = esc.reduce((s, e) => s + num(e.palabras), 0); const meta = num(obra.meta); const diaria = num(obra.diaria);
    const dias = E.ultimosDias(30, pid); const ult14 = dias.slice(-14).map((x) => x[1]);
    const ritmo = ult14.some((x) => x > 0) ? ult14.reduce((a, b) => a + b, 0) / 14 : 0;
    const hoy = E.escritas(RV.hoy(), pid);
    const mejor = dias.reduce((m, x) => (x[1] > m[1] ? x : m), [0, 0]);
    const ests = [0, 0, 0]; for (const e of esc) ests[Math.min(2, num(e.estado))]++;
    const maxCap = Math.max(1, ...caps.map((c) => E.palabrasCapitulo(c.id)));
    const raiz = h('div.e-cifrasp');
    const grande = (n, t) => h('div', h('b', String(n)), h('span.versal', t));
    raiz.append(h('div.e-cifras', grande(cifra(total), 'palabras'), grande(esc.length, esc.length === 1 ? 'escena' : 'escenas'), grande(caps.length, caps.length === 1 ? 'capítulo' : 'capítulos'),
      grande(cifra(esc.length ? Math.round(total / esc.length) : 0), 'por escena'), grande(Math.max(1, Math.ceil(total / 220)), 'min de lectura')));
    if (meta) {
      const falta = Math.max(0, meta - total);
      let fin = '';
      if (falta > 0 && ritmo >= 1) fin = ` · a tu ritmo de los últimos 14 días (${cifra(Math.round(ritmo))} al día), el ${new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(Date.now() + Math.ceil(falta / ritmo) * 86400000))}`;
      raiz.append(h('section', h('div.etiqueta', h('span.t', 'Meta de la obra')), barra(total / meta, colorDe(obra.color)), h('p.cursiva', `${Math.floor((total * 100) / meta)} % · ${falta ? `faltan ${cifra(falta)} palabras` : 'meta alcanzada'}${fin}`)));
    }
    raiz.append(h('section', h('div.etiqueta', h('span.t', 'Últimos 30 días')), barras(dias.map((x) => x[1]), diaria),
      h('p.cursiva', `Hoy ${cifra(hoy)}${diaria ? ` de ${cifra(diaria)}` : ''} · racha ${RV.plural(E.racha(pid), 'día', 'días')}${mejor[1] ? ` · tu mejor día: ${cifra(mejor[1])} (${RV.fmtDiaCorto(mejor[0])})` : ''}`)));
    if (esc.length) raiz.append(h('section', h('div.etiqueta', h('span.t', 'Cómo va el manuscrito')),
      h('div.e-apilada', ests.map((n, i) => (n ? h('span', { style: { flex: String(n), background: COL_ESTADO[i] } }) : null))), h('p.cursiva', ESTADOS.map((n, i) => `${ests[i]} ${n.toLowerCase()}`).join(' · '))));
    if (caps.length) raiz.append(h('section', h('div.etiqueta', h('span.t', 'Palabras por capítulo')),
      ...caps.map((c, i) => h('div.e-cap-barra', h('span', String(i + 1)), barra(E.palabrasCapitulo(c.id) / maxCap), h('span', cifra(E.palabrasCapitulo(c.id)))))));
    const sp = E.sprintsObra(pid);
    if (sp) raiz.append(sp);
    return raiz;
  }

  // ---------------------------------------------------------------- La guía de la novela (escribir-guia.js)
  let abierto = { pid: '', id: 0 };
  const guardaGuia = (obra, c) => op('edita', obra.id, c).catch(E.err);
  // Marcar un paso (1 hecho, 2 saltado) y abrir el siguiente
  const marca = (obra, p, v) => { abierto = { pid: obra.id, id: p.id < RV.G.PASOS.length ? p.id + 1 : 0, sig: abierto.sig }; return guardaGuia(obra, { [`g${p.id}`]: v }); };
  const ACCIONES = {
    1: (obra) => {
      const ta = RV.areaTexto(obra.idea || '', { rows: '3', placeholder: 'Ejemplo: Una aprendiz de hechicera sube a la torre donde nadie vuelve para salvar a su hermana, y descubre que lo que la espera arriba es ella misma.', 'data-autofocus': true });
      RV.dialogo({ ante: 'Paso 1', titulo: 'Tu historia en una frase', ancho: true,
        cuerpo: [h('p.texto', 'Quién quiere qué, y qué se lo impide. Corta y sin nombres propios si puedes; no tiene que ser perfecta.'), RV.campo('La frase', ta)],
        acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: async (d) => { if (!ta.value.trim()) { RV.aviso('Escribe tu frase.', 'error'); return; } await guardaGuia(obra, { idea: ta.value.trim() }); d.cerrar(); } }] });
    },
    2: (obra) => E.dlgFicha(obra.id, null, 'personaje', null, ['Quiere', 'Se lo impide', 'Si fracasa', 'Edad']),
    // El esquema va en una nota (fuera del manuscrito), con un apartado por cada acto
    3: (obra) => {
      const ex = E.notas(obra.id).find((n) => n.titulo === RV.G.TITULO_ESQUEMA);
      if (ex) { E.dlgNota(obra.id, ex); return; }
      op('nuevo', 'n', obra.id, { tipo: 'idea', titulo: RV.G.TITULO_ESQUEMA, color: RV.COLORES[1], texto: RV.G.PLANTILLA_ESQUEMA })
        .then((n) => E.dlgNota(obra.id, n)).catch(E.err);
    },
    4: (obra) => {
      const ex = E.notas(obra.id).find((n) => n.titulo === 'Promesas de la historia');
      if (ex) { E.dlgNota(obra.id, ex); return; }
      op('nuevo', 'n', obra.id, { tipo: 'idea', titulo: 'Promesas de la historia', color: RV.COLORES[2],
        texto: 'Promesa: ¿qué le prometo al lector en las primeras páginas (tono, género, conflicto)?\n\nProgreso: ¿qué cambia en cada tramo para que el medio avance?\n\nRecompensa: ¿cómo cumple el final lo que prometí?\n' })
        .then((n) => E.dlgNota(obra.id, n)).catch(E.err);
    },
    5: (obra) => {
      const dia = RV.entrada(obra.diaria || '300', { type: 'number', min: '0', 'data-autofocus': true });
      RV.dialogo({ ante: 'Paso 5', titulo: 'Tu meta de cada día', ancho: true,
        cuerpo: [h('p.texto', 'Elige una cantidad que te parezca casi demasiado fácil. Mejor cumplirla todos los días que abandonar una grande.'), RV.campo('Palabras al día', dia, 'Para empezar, unas 300 (una sugerencia de Atrio). Siempre puedes subirla.'),
          chips([['150', '150'], ['300', '300'], ['500', '500'], ['1000', '1000']], String(dia.value), (k) => { dia.value = k; })],
        acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Guardar', principal: true, fn: async (d) => { await guardaGuia(obra, { diaria: num(dia.value) }); d.cerrar(); } }] });
    },
    6: (obra) => { const e = E.escenasDe(obra.id).sort((a, b) => num(b.u) - num(a.u))[0]; RV.ir(e ? { v: 'editor', id: e.id } : { v: 'obra', id: obra.id, tab: 0 }); },
    7: (obra) => guardaGuia(obra, { g7t: Date.now() }),
    8: (obra) => RV.ir({ v: 'obra', id: obra.id, tab: 0 }),
    9: (obra) => RV.ir({ v: 'obra', id: obra.id, tab: 0 }),
    10: (obra) => exportar(obra),
    11: (obra) => exportar(obra),
  };
  // La tarjeta del manuscrito con el paso que toca ahora (solo con la guía activada)
  function tarjetaGuia(obra) {
    if (!RV.G.activa(obra)) return null;
    const est = RV.G.estados(E.datosGuia(obra.id)); const id = RV.G.siguiente(est);
    if (!id) return null;
    const p = RV.G.PASOS.find((x) => x.id === id);
    return h('div.e-sigue.e-paso-ahora', h('span.versal.acento', `Guía · paso ${id} de ${RV.G.PASOS.length}`), h('span.titulo', p.titulo), h('span.cursiva', p.que),
      h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: () => ACCIONES[id](obra) }, p.accion), h('button.enlace', { type: 'button', onclick: () => { abierto = { pid: obra.id, id, sig: id }; RV.ir({ v: 'obra', id: obra.id, tab: TAB_GUIA }); } }, 'Ver la guía')));
  }
  function guiaNovela(obra) {
    const pid = obra.id; const d = E.datosGuia(pid); const est = RV.G.estados(d); const sig = RV.G.siguiente(est); const hechos = RV.G.hechos(est);
    const raiz = h('div.e-guia');
    raiz.append(h('div.e-resumen', h('span', `${hechos} de ${RV.G.PASOS.length} pasos`), barra(hechos / RV.G.PASOS.length, colorDe(obra.color))));
    if (String(obra.idea || '').trim()) raiz.append(h('blockquote.e-idea', obra.idea));
    raiz.append(h('div.fila-botones', RV.G.activa(obra)
      ? h('span.apagado', 'La guía está activada: el paso que toca sale arriba del manuscrito.')
      : h('span.apagado', 'La guía está desactivada: no sale nada en el manuscrito.'),
    h('button.enlace', { type: 'button', onclick: () => guardaGuia(obra, { gOn: RV.G.activa(obra) ? 0 : 1 }) }, RV.G.activa(obra) ? 'Desactivar' : 'Activar')));
    // El paso abierto sigue al que toca: al acabar uno, se abre el siguiente
    if (abierto.pid !== pid || abierto.sig !== sig) abierto = { pid, id: sig || 0, sig };
    for (const p of RV.G.PASOS) {
      const e = est[p.id - 1]; const abre = abierto.id === p.id; const g = `g${p.id}`;
      const signo = e.estado === 'hecho' ? '✓' : e.estado === 'saltado' ? '–' : e.estado === 'curso' ? '…' : String(p.id);
      const cab = h('button.e-paso-cab', { type: 'button', 'aria-expanded': abre ? 'true' : 'false', onclick: () => { abierto = { pid, id: abre ? 0 : p.id, sig }; RV.refrescar(); } },
        h('i', signo), h('span.titulo', p.titulo),
        h('span.versal', e.estado === 'hecho' ? 'Hecho' : e.estado === 'saltado' ? 'Saltado' : e.estado === 'curso' ? `Reposo: ${RV.plural(e.dias, 'día', 'días')}` : p.id === sig ? 'Toca ahora' : ''));
      const cuerpo = abre ? h('div.e-paso-cuerpo', h('p.texto', p.que), h('ul', p.como.map((x) => h('li', x))),
        p.id === 6 ? h('div', barra(num(obra.meta) ? d.palabras / num(obra.meta) : 0, colorDe(obra.color)), h('span.apagado', `${cifra(d.palabras)}${num(obra.meta) ? ` de ${cifra(num(obra.meta))}` : ''} palabras`)) : null,
        p.id === 5 ? h('span.apagado', `Has escrito en ${RV.plural(d.dias, 'día', 'días')} distintos (con 3 se da por hecho).`) : null,
        e.estado === 'hecho' ? h('p.cursiva.acento', p.hecho) : null,
        h('div.fila-botones',
          e.estado === 'curso' ? h('span.apagado', `Llevas ${RV.plural(e.dias, 'día', 'días')} sin abrirlo (unas semanas suele ir bien).`) : h('button.boton-tinta', { type: 'button', onclick: () => ACCIONES[p.id](obra) }, p.accion),
          e.estado === 'curso' ? h('button.boton-borde', { type: 'button', onclick: () => guardaGuia(obra, { g7: 1 }) }, 'Ya he descansado') : null,
          e.estado === 'curso' ? h('button.enlace', { type: 'button', onclick: () => guardaGuia(obra, { g7t: Date.now() }) }, 'Empezar de nuevo') : null,
          e.estado === 'pendiente' && !p.auto ? h('button.boton-borde', { type: 'button', onclick: () => marca(obra, p, 1) }, 'Ya lo tengo') : null,
          e.estado === 'pendiente' && p.auto ? h('button.enlace', { type: 'button', onclick: () => marca(obra, p, 1) }, 'Marcar como hecho') : null,
          e.estado === 'pendiente' ? h('button.enlace.apagado', { type: 'button', onclick: () => marca(obra, p, 2) }, 'Saltar este paso') : null,
          e.aMano ? h('button.enlace.apagado', { type: 'button', onclick: () => guardaGuia(obra, { [g]: 0 }) }, 'Quitar la marca') : null)) : null;
      raiz.append(h('section.e-paso' + (e.estado === 'hecho' ? '.hecho' : '') + (p.id === sig ? '.ahora' : ''), cab, cuerpo));
    }
    raiz.append(h('p.apagado.e-fuentes', RV.G.FUENTES));
    return raiz;
  }

  function buscarObra(obra) {
    const q = RV.entrada('', { type: 'search', placeholder: 'Una palabra o una frase', 'data-autofocus': true });
    const ul = h('ul.resultados');
    const pinta = () => {
      ul.replaceChildren();
      if (q.value.trim().length < 2) return;
      const res = E.busca(obra.id, q.value);
      if (!res.length) { ul.append(h('li', RV.vacio('Nada coincide.'))); return; }
      for (const r of res) {
        ul.append(h('li', h('button.resultado', { type: 'button', style: { width: '100%', textAlign: 'left' }, onclick: () => { d.cerrar(); if (r.doc.t === 'e') RV.ir({ v: 'editor', id: r.doc.id, buscar: q.value }); else if (r.doc.t === 'f') E.dlgFicha(obra.id, r.doc); else E.dlgNota(obra.id, r.doc); } },
          h('span', h('span.versal.acento', r.donde, { style: { display: 'block' } }), h('span.tit', r.extracto)))));
      }
    };
    q.addEventListener('input', RV.debounce(pinta, 120));
    const d = RV.dialogo({ ante: 'Buscar', titulo: `En «${obra.titulo}»`, ancho: true, cuerpo: [q, ul] });
  }

  function exportar(obra) {
    let formato = 'docx'; let caps = true; let escenas = false; let minimo = 0; let estilo = 'libro'; let interlineado = '1.5';
    const msg = h('p.mensaje');
    const ESTILOS = { libro: 'Como un libro: tus letras, justificado, capítulos en página nueva.', manuscrito: 'Para enviar a editoriales y concursos: Times New Roman 12, márgenes de 2,5 cm, páginas numeradas y una portada con el recuento de palabras. Si una editorial pide otra cosa, sus normas mandan.' };
    const ayudaEstilo = h('small', ESTILOS.libro);
    const filaInter = h('div.campo', h('span', 'Interlineado'), chips([['1.5', '1,5'], ['2', 'Doble']], interlineado, (k) => { interlineado = k; }));
    const filaEstilo = h('div.campo', h('span', 'Estilo'), chips([['libro', 'Libro'], ['manuscrito', 'Manuscrito']], estilo, (k) => { estilo = k; pintaOpc(); }), ayudaEstilo);
    const pintaOpc = () => {
      const conEstilo = formato === 'docx' || formato === 'pdf';
      filaEstilo.hidden = !conEstilo; filaInter.hidden = !conEstilo || estilo !== 'manuscrito';
      ayudaEstilo.textContent = ESTILOS[estilo];
    };
    RV.dialogo({
      ante: 'Exportar', titulo: obra.titulo, ancho: true,
      cuerpo: [h('p.texto', 'Se hace en este PC: el texto no se envía a ningún sitio. Después eliges dónde guardarlo.'),
        RV.campo('Formato', chips([['docx', 'Word (.docx)'], ['pdf', 'PDF'], ['epub', 'Libro (.epub)'], ['md', 'Markdown (.md)'], ['txt', 'Texto (.txt)']], formato, (k) => { formato = k; pintaOpc(); })),
        filaEstilo, filaInter,
        RV.campo('Qué incluir', h('div', h('label.e-check', h('input', { type: 'checkbox', checked: '', onchange: (e) => { caps = e.target.checked; } }), ' Títulos de capítulo'), h('label.e-check', h('input', { type: 'checkbox', onchange: (e) => { escenas = e.target.checked; } }), ' Títulos de escena'))),
        RV.campo('Escenas', chips([['0', 'Todas'], ['1', 'Revisión y finales'], ['2', 'Solo finales']], '0', (k) => { minimo = Number(k); })), msg],
      acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: 'Exportar', principal: true, fn: async (d) => {
        msg.textContent = formato === 'pdf' ? 'Preparando el PDF…' : '';
        try { const r = await api.escritosExportar(obra.id, formato, { titulosDeCapitulo: caps, titulosDeEscena: escenas, soloEstadoMinimo: minimo, estilo, interlineado }); if (r) { d.cerrar(); RV.aviso(`Guardado en ${r}`); } else msg.textContent = ''; } catch (e) { msg.textContent = RV.limpiaError(e); }
      } }],
    });
    pintaOpc();
  }

  // ---------------------------------------------------------------- Datos en directo
  // (los escritos pueden llegar antes de que cargue app.js, que es quien sabe en qué página estamos)
  const vista = () => (typeof RV.ruta === 'function' && typeof RV.refrescar === 'function' ? RV.ruta().v || 'portada' : null);
  // (la portada solo se repinta si cambia lo que enseña de Escribir)
  let firmaPortada = '';
  const firma = () => { const s = E.ultimaEscena(); return s ? `${s.id}|${s.titulo}|${s.palabras}|${E.escritas(RV.hoy())}|${E.obras().length}` : ''; };
  const cambia = RV.debounce(() => {
    const r = vista();
    if (r === 'editor') { if (RV.escribirEditorCambio) RV.escribirEditorCambio(); return; }
    if (['escribir', 'obra', 'parte', 'capitulo'].includes(r)) RV.refrescar();
    if (r === 'portada') { const f = firma(); if (f !== firmaPortada) { firmaPortada = f; RV.refrescar(); } }
  }, 60);
  api.on('escritos', (x) => { E.set(x.docs, x.estado); cambia(); });
  api.escritosTodo().then((x) => { E.set(x.docs, x.estado); if (['escribir', 'obra', 'editor', 'parte', 'capitulo'].includes(vista())) RV.refrescar(); }).catch(() => { cargado = true; });
})(window.RV);
