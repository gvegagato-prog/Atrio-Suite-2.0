'use strict';
/* global window, document, getSelection, NodeFilter, requestAnimationFrame */
// El editor de una escena: una página de libro donde escribir con el formato a la vista (el Markdown ligero se pinta
// mientras escribes), con concentración, máquina de escribir, deshacer, buscar y reemplazar, y un panel con la escena, las
// fichas, las notas, los borradores y el historial. Se guarda solo (y sube con tu cuenta en cuanto hay red).
(function (RV) {
  const h = RV.h;
  const E = RV.E;
  const ET = RV.ET;
  const num = RV.num;
  const api = RV.api;
  RV.vistas = RV.vistas || {};
  RV.editorActivo = null;

  const prefs = () => { try { return JSON.parse(localStorage.getItem('escribir:prefs') || '{}'); } catch { return {}; } };
  const guardaPrefs = (p) => { try { localStorage.setItem('escribir:prefs', JSON.stringify(p)); } catch { /* */ } };
  const esc = (s) => String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const fechaHora = (t) => new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(t)).replace(/\./g, '');

  RV.vistas.editor = (ruta) => {
    if (RV.editorActivo) RV.editorActivo.cerrar();
    const escena = E.get(ruta.id);
    if (!escena || escena.t !== 'e') { RV.ir({ v: 'escribir' }, { reemplazar: true }); return h('div.hoja'); }
    return crear(escena, ruta).nodo;
  };

  function crear(escena0, ruta) {
    const id = escena0.id; const pid = escena0.pid;
    const p = { tam: 20, fuente: 0, maquina: false, ...prefs() };
    let texto = escena0.texto || '';
    let guardado = texto;
    const inicio = E.palabras(texto);
    const hist = new ET.Historial({ text: texto, a: texto.length, b: texto.length });
    let timerGuarda = null; let timerPinta = null; let timerSnap = null;
    let componiendo = false; let foco = false; let panelAbierto = false; let busqueda = ''; let pestana = 0; let busquedaMayus = false; let actual = -1;
    let regFichas = null;

    const pagina = h('div.ed-texto', { contenteditable: 'plaintext-only', spellcheck: 'true', role: 'textbox', 'aria-multiline': 'true', 'aria-label': 'Texto de la escena', 'data-clave': 'ed-texto' });
    const scroll = h('div.ed-scroll', h('div.ed-hoja', pagina));
    const titulo = h('input.ed-titulo', { type: 'text', value: escena0.titulo || '', placeholder: 'Sin título', 'aria-label': 'Título de la escena', spellcheck: 'true' });
    const cuentas = h('div.ed-cuentas');
    const panel = h('aside.ed-panel', { hidden: '' });
    const buscaFila = h('div.ed-busca', { hidden: '' });

    // ---- Texto y selección
    const leer = () => pagina.textContent.replace(/​/g, '');
    const offsets = () => {
      const s = getSelection();
      if (!s.rangeCount || !pagina.contains(s.anchorNode)) return null;
      const r = s.getRangeAt(0); const pre = document.createRange();
      pre.selectNodeContents(pagina); pre.setEnd(r.startContainer, r.startOffset);
      const a = pre.toString().replace(/​/g, '').length;
      pre.setEnd(r.endContainer, r.endOffset);
      return { a, b: pre.toString().replace(/​/g, '').length };
    };
    const ponSel = (a, b = a) => {
      const w = document.createTreeWalker(pagina, NodeFilter.SHOW_TEXT);
      let n = 0; let ini = null; let fin = null; let nodo;
      const lim = (x) => Math.max(0, Math.min(x, texto.length));
      a = lim(a); b = lim(b);
      while ((nodo = w.nextNode())) {
        const len = nodo.data.replace(/​/g, '').length;
        if (!ini && a <= n + len) ini = [nodo, Math.min(a - n, nodo.data.length)];
        if (!fin && b <= n + len) { fin = [nodo, Math.min(b - n, nodo.data.length)]; break; }
        n += len;
      }
      const s = getSelection(); const r = document.createRange();
      if (!ini) { r.selectNodeContents(pagina); r.collapse(false); } else { r.setStart(...ini); if (fin) r.setEnd(...fin); else r.collapse(true); }
      s.removeAllRanges(); s.addRange(r);
    };
    const busquedaRx = () => (busqueda ? new RegExp(esc(busqueda), busquedaMayus ? 'g' : 'gi') : null);
    const pinta = () => {
      const segs = ET.segmentos(texto, { fichas: regFichas, busca: busquedaRx() });
      const nodos = segs.map((s) => { const sp = document.createElement('span'); if (s.cls.length) sp.className = s.cls.map((c) => `e-${c}`).join(' '); sp.textContent = texto.slice(s.desde, s.hasta); return sp; });
      // Un salto de línea al final no se ve si no hay nada detrás: se pone un carácter invisible
      if (texto.endsWith('\n')) nodos.push(document.createTextNode('​'));
      pagina.replaceChildren(...nodos);
    };
    const reconstruyeFichas = () => {
      const ns = E.fichas(pid).flatMap((f) => [f.nombre, ...String(f.alias || '').split(',')]).map((x) => String(x).trim()).filter((x) => x.length >= 2);
      const unicos = [...new Map(ns.map((x) => [x.toLowerCase(), x])).values()].sort((a, b) => b.length - a.length);
      regFichas = unicos.length ? new RegExp(`(?<![\\p{L}\\p{N}])(${unicos.map(esc).join('|')})(?![\\p{L}\\p{N}])`, 'giu') : null;
    };
    const repinta = () => { const s = offsets(); pinta(); if (s && document.activeElement === pagina) ponSel(s.a, s.b); };

    // ---- Guardar
    const guarda = async () => {
      clearTimeout(timerGuarda); timerGuarda = null;
      if (texto === guardado) return;
      const t = texto; guardado = t;
      try { await api.escritos('escribe', id, t); } catch (e) { E.err(e); guardado = ''; }
    };
    const aviso = () => { clearTimeout(timerGuarda); timerGuarda = setTimeout(guarda, 600); };
    const snap = () => api.escritos('instantanea', id, texto, {}).catch(() => {});
    timerSnap = setInterval(snap, 300000);
    api.escritos('instantanea', id, texto, { hueco: 3600000 }).catch(() => {});

    // ---- Contadores
    const pie = () => {
      const pal = E.palabras(texto); const o = offsets(); const selPal = o && o.a !== o.b ? E.palabras(texto.slice(Math.min(o.a, o.b), Math.max(o.a, o.b))) : 0;
      const obra = E.get(pid) || {}; const hoy = E.escritas(RV.hoy(), pid) + 0; const dia = num(obra.diaria); const ses = pal - inicio; const meta = num((E.get(id) || {}).meta);
      cuentas.replaceChildren(...[
        h('span', selPal ? `${selPal} de ${pal} palabras` : `${E.cifra(pal)} palabras`), h('span', `${Math.max(1, Math.ceil(pal / 220))} min`),
        ses ? h('span' + (ses > 0 ? '.acento' : ''), `${ses > 0 ? '+' : ''}${ses} en esta sesión`) : null,
        meta ? h('span' + (pal >= meta ? '.hecho' : ''), `meta ${meta}`) : null, h('span.hueco'), dia ? h('span' + (hoy >= dia ? '.hecho' : ''), `hoy ${hoy}/${dia}`) : null].filter(Boolean));
    };

    // ---- Máquina de escribir: la línea donde escribes se queda a un 40 % de la altura
    const centra = () => {
      if (!p.maquina) return;
      const s = getSelection(); if (!s.rangeCount || !pagina.contains(s.anchorNode)) return;
      const r = s.getRangeAt(0).cloneRange(); r.collapse(false);
      let rect = r.getClientRects()[0];
      if (!rect) { const n = s.anchorNode.nodeType === 1 ? s.anchorNode : s.anchorNode.parentElement; rect = n.getBoundingClientRect(); }
      const c = scroll.getBoundingClientRect();
      scroll.scrollBy({ top: rect.top - (c.top + c.height * 0.4), behavior: 'auto' });
    };

    // ---- Cambios del texto
    const cambio = (txt, a, b) => { texto = txt; hist.apunta({ text: txt, a, b }); pinta(); ponSel(a, b); pie(); aviso(); centra(); };
    const aplica = (fn) => { const o = offsets() || { a: texto.length, b: texto.length }; const r = fn({ text: texto, a: o.a, b: o.b }); cambio(r.text, r.a, r.b); pagina.focus({ preventScroll: true }); };
    pagina.addEventListener('compositionstart', () => { componiendo = true; });
    pagina.addEventListener('compositionend', () => { componiendo = false; pagina.dispatchEvent(new Event('input')); });
    pagina.addEventListener('input', () => {
      if (componiendo) return;
      texto = leer();
      const o = offsets() || { a: texto.length, b: texto.length };
      hist.apunta({ text: texto, a: o.a, b: o.b });
      pie(); aviso(); centra();
      clearTimeout(timerPinta); timerPinta = setTimeout(() => { if (!componiendo) repinta(); }, 140);
    });
    pagina.addEventListener('beforeinput', (e) => {
      if (e.inputType === 'historyUndo') { e.preventDefault(); deshacer(); } else if (e.inputType === 'historyRedo') { e.preventDefault(); rehacer(); }
    });
    // Al pegar, siempre texto sin formato
    pagina.addEventListener('paste', (e) => { e.preventDefault(); const t = (e.clipboardData || window.clipboardData).getData('text/plain'); if (t) aplica((v) => ET.inserta(v, t.replace(/\r\n/g, '\n'))); });
    document.addEventListener('selectionchange', selCambia);
    function selCambia() { if (document.activeElement === pagina) { pie(); } }
    const deshacer = () => { const v = hist.deshace(); if (v) { texto = v.text; pinta(); ponSel(v.a, v.b); pie(); aviso(); } };
    const rehacer = () => { const v = hist.rehace(); if (v) { texto = v.text; pinta(); ponSel(v.a, v.b); pie(); aviso(); } };
    pagina.addEventListener('keydown', (e) => {
      const ctrl = e.ctrlKey || e.metaKey; const k = e.key.toLowerCase();
      // (deshacer y rehacer los pide el propio sistema —el teclado o el menú Edición de macOS— y llegan por «beforeinput»)
      if (ctrl && k === 'b') { e.preventDefault(); aplica((v) => ET.envuelve(v, '**')); } else if (ctrl && k === 'i') { e.preventDefault(); aplica((v) => ET.envuelve(v, '*')); }
      else if (ctrl && k === 'f') { e.preventDefault(); abreBusca(); }
    });
    // «Fichas» y «Escena» del panel pueden cambiar mientras escribes
    titulo.addEventListener('input', RV.debounce(() => { api.escritos('edita', id, { titulo: titulo.value }).catch(E.err); }, 500));

    // ---- Barra de formato
    const tecla = (t, ayuda, f, extra) => h('button.ed-tecla' + (extra || ''), { type: 'button', title: ayuda, 'aria-label': ayuda, onmousedown: (e) => e.preventDefault(), onclick: f }, t);
    const barra = h('div.ed-barra', tecla('↶', 'Deshacer (Ctrl+Z)', deshacer), tecla('↷', 'Rehacer (Ctrl+Mayús+Z)', rehacer), h('i'),
      tecla('B', 'Negrita (Ctrl+B)', () => aplica((v) => ET.envuelve(v, '**')), '.neg'), tecla('I', 'Cursiva (Ctrl+I)', () => aplica((v) => ET.envuelve(v, '*')), '.cur'),
      tecla('T', 'Título', () => aplica((v) => ET.prefijoLinea(v, '## '))), tecla('❝', 'Cita', () => aplica((v) => ET.prefijoLinea(v, '> '))), tecla('* * *', 'Salto de escena', () => aplica((v) => ET.salto(v))),
      tecla('«»', 'Comillas', () => aplica((v) => ET.comillas(v))), tecla('—', 'Raya', () => aplica((v) => ET.inserta(v, '—'))), h('i'),
      tecla('Buscar', 'Buscar y reemplazar (Ctrl+F)', () => abreBusca()), h('span.hueco'),
      tecla('Letra −', 'Letra más pequeña', () => { p.tam = Math.max(14, p.tam - 1); aplicaPrefs(); }), tecla('Letra +', 'Letra más grande', () => { p.tam = Math.min(34, p.tam + 1); aplicaPrefs(); }),
      tecla(['Serif', 'Sans', 'Mono'][p.fuente], 'Tipo de letra', () => { p.fuente = (p.fuente + 1) % 3; aplicaPrefs(); }, '.fuente'),
      tecla('Máquina', 'Máquina de escribir: la línea queda a media altura', () => { p.maquina = !p.maquina; aplicaPrefs(); centra(); }, '.maq'),
      tecla('Concentración', 'Solo el texto (Esc para salir)', () => ponFoco(true)), tecla('Panel', 'Panel de la escena', () => { panelAbierto = !panelAbierto; pintaPanel(); }, '.pan'));
    const aplicaPrefs = () => {
      guardaPrefs(p);
      nodo.style.setProperty('--tam', `${p.tam}px`);
      nodo.dataset.fuente = ['serif', 'sans', 'mono'][p.fuente];
      const f = barra.querySelector('.fuente'); if (f) f.textContent = ['Serif', 'Sans', 'Mono'][p.fuente];
      const m = barra.querySelector('.maq'); if (m) m.setAttribute('aria-pressed', p.maquina ? 'true' : 'false');
    };

    // ---- Concentración
    function ponFoco(v) { foco = v; document.body.classList.toggle('ed-foco', v); pagina.focus({ preventScroll: true }); }

    // ---- Buscar y reemplazar
    const qIn = RV.entrada('', { type: 'search', placeholder: 'Buscar', 'aria-label': 'Buscar' });
    const rIn = RV.entrada('', { placeholder: 'Cambiar por', 'aria-label': 'Cambiar por' });
    const nBusca = h('span.versal');
    const hallazgos = () => { const rx = busquedaRx(); return rx ? [...texto.matchAll(rx)].map((m) => [m.index, m.index + m[0].length]) : []; };
    const actualiza = () => { busqueda = qIn.value; const n = hallazgos(); actual = -1; nBusca.textContent = busqueda ? (n.length ? `${n.length}` : '0') : ''; repinta(); };
    const salta = (d) => {
      const n = hallazgos(); if (!n.length) return;
      actual = (((actual < 0 ? (d > 0 ? -1 : 0) : actual) + d) % n.length + n.length) % n.length;
      nBusca.textContent = `${actual + 1}/${n.length}`;
      pagina.focus({ preventScroll: true }); ponSel(n[actual][0], n[actual][1]);
      const s = getSelection(); if (s.rangeCount) { const nodoSel = s.anchorNode.nodeType === 1 ? s.anchorNode : s.anchorNode.parentElement; nodoSel.scrollIntoView({ block: 'center' }); }
    };
    qIn.addEventListener('input', RV.debounce(actualiza, 100));
    qIn.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); salta(e.shiftKey ? -1 : 1); } });
    buscaFila.append(qIn, h('button.ed-tecla', { type: 'button', title: 'Anterior', onclick: () => salta(-1) }, '▲'), h('button.ed-tecla', { type: 'button', title: 'Siguiente', onclick: () => salta(1) }, '▼'), nBusca, rIn,
      h('button.e-chip', { type: 'button', title: 'Distinguir mayúsculas', 'aria-pressed': 'false', onclick: (e) => { busquedaMayus = !busquedaMayus; e.target.setAttribute('aria-pressed', busquedaMayus ? 'true' : 'false'); actualiza(); } }, 'Aa'),
      h('button.enlace', { type: 'button', onclick: () => { const n = hallazgos(); if (actual >= 0 && n[actual]) { const [a, b] = n[actual]; cambio(texto.slice(0, a) + rIn.value + texto.slice(b), a + rIn.value.length, a + rIn.value.length); actualiza(); } } }, 'Este'),
      h('button.enlace', { type: 'button', onclick: () => { const rx = busquedaRx(); if (rx) { const t = texto.replace(rx, () => rIn.value); cambio(t, 0, 0); actualiza(); } } }, 'Todos aquí'),
      h('button.enlace', { type: 'button', onclick: async () => { if (!busqueda) return; await guarda(); try { const n = await api.escritos('reemplaza', pid, busqueda, rIn.value, busquedaMayus); RV.aviso(`${RV.plural(n, 'cambio', 'cambios')} en toda la obra`); } catch (e) { E.err(e); } } }, 'Todos en la obra'),
      h('button.enlace.apagado', { type: 'button', onclick: () => cierraBusca() }, '✕'));
    function abreBusca() { buscaFila.hidden = false; const s = offsets(); if (s && s.a !== s.b) qIn.value = texto.slice(Math.min(s.a, s.b), Math.max(s.a, s.b)); qIn.focus(); qIn.select(); actualiza(); }
    function cierraBusca() { buscaFila.hidden = true; busqueda = ''; qIn.value = ''; repinta(); pagina.focus({ preventScroll: true }); }

    // ---- Panel
    const campoPanel = (et, ctl) => h('label.campo', h('span', et), ctl);
    const pintaPanel = () => {
      panel.hidden = !panelAbierto;
      nodo.classList.toggle('con-panel', panelAbierto);
      const pan = barra.querySelector('.pan'); if (pan) pan.setAttribute('aria-pressed', panelAbierto ? 'true' : 'false');
      if (!panelAbierto) return;
      const e = E.get(id) || escena0;
      const cuerpo = h('div.ed-panel-cuerpo');
      if (pestana === 0) {
        const re = RV.areaTexto(e.resumen || '', { rows: '4' }); const cu = RV.entrada(e.cuando || ''); const et = RV.entrada((e.etq || []).join(', ')); const me = RV.entrada(e.meta || '', { type: 'number', min: '0' });
        const fs = E.fichas(pid);
        const sel = (campo, tipo) => { const s = h('select.entrada', { onchange: () => api.escritos('edita', id, { [campo]: s.value || null }).catch(E.err) }, h('option', { value: '' }, '—'), ...fs.filter((f) => f.tipo === tipo).map((f) => h('option', { value: f.id, selected: e[campo] === f.id ? '' : null }, f.nombre))); return s; };
        const guardaCampos = RV.debounce(() => api.escritos('edita', id, { resumen: re.value, cuando: cu.value, etq: et.value.split(',').map((x) => x.trim()).filter(Boolean), meta: num(me.value) }).catch(E.err), 500);
        for (const c of [re, cu, et, me]) c.addEventListener('input', guardaCampos);
        cuerpo.append(h('div.versal', 'Estado'), h('div.e-chips', E.ESTADOS.map((n, i) => h('button.e-chip', { type: 'button', 'aria-pressed': num(e.estado) === i ? 'true' : 'false', onclick: () => api.escritos('edita', id, { estado: i }).then(pintaPanel).catch(E.err) }, n))),
          campoPanel('Resumen de la escena', re), h('div.e-dos', campoPanel('Punto de vista', sel('pov', 'personaje')), campoPanel('Lugar', sel('lugar', 'lugar'))),
          campoPanel('Cuándo pasa (línea temporal)', cu), campoPanel('Etiquetas (separadas por comas)', et), campoPanel('Meta de palabras de la escena', me));
      } else if (pestana === 1) {
        const q = RV.entrada('', { type: 'search', placeholder: 'Buscar una ficha' });
        const lista = h('div.ed-lista');
        const aqui = new Set(E.fichasEn(pid, texto).map((f) => f.id));
        const dibuja = () => {
          const t = q.value.trim().toLowerCase();
          const fs = E.fichas(pid).filter((f) => !t || `${f.nombre} ${f.alias || ''}`.toLowerCase().includes(t)).sort((a, b) => Number(aqui.has(b.id)) - Number(aqui.has(a.id)));
          lista.replaceChildren(...(fs.length ? fs.map((f) => h('div.ed-item', { style: { '--c': E.colorDe(f.color) } }, h('div.ed-item-cab', h('span.punto'), h('b', f.nombre), h('span.versal', `${(E.TIPOS_FICHA.find((x) => x[0] === f.tipo) || [])[2] || 'Ficha'}${aqui.has(f.id) ? ' · sale aquí' : ''}`)),
            f.resumen ? h('p', f.resumen) : null, ...(Array.isArray(f.campos) ? f.campos.filter((c) => c.v).map((c) => h('p.apagado', `${c.k}: ${c.v}`)) : []),
            h('div.ed-item-acc', h('button.enlace', { type: 'button', onclick: () => aplica((v) => ET.inserta(v, f.nombre)) }, 'Poner el nombre'), h('button.enlace', { type: 'button', onclick: () => E.dlgFicha(pid, f) }, 'Editar'))))
            : [RV.vacio(E.fichas(pid).length ? `Nada con «${q.value}».` : 'Aún no hay fichas. Crea la de tu protagonista: quedará subrayada en el texto cada vez que la nombres.')]));
        };
        q.addEventListener('input', dibuja); dibuja();
        cuerpo.append(q, lista, h('button.boton-borde', { type: 'button', onclick: () => E.dlgFicha(pid, null) }, 'Nueva ficha'));
      } else if (pestana === 2) {
        const ns = E.notas(pid).sort((a, b) => num(b.fija) - num(a.fija) || (b.tipo === 'plantilla') - (a.tipo === 'plantilla') || b.u - a.u);
        cuerpo.append(...(ns.length ? ns.map((n) => h('div.ed-item', { style: { '--c': E.colorDe(n.color) } }, h('div.ed-item-cab', h('span.punto'), h('b', n.titulo || 'Sin título'), h('span.versal', `${(E.TIPOS_NOTA.find((x) => x[0] === n.tipo) || [])[2] || 'Nota'}${n.fija ? ' · fijada' : ''}`)),
          n.texto ? h('p.pre', n.texto) : null, h('div.ed-item-acc', n.texto ? h('button.enlace', { type: 'button', onclick: () => aplica((v) => ET.inserta(v, n.texto)) }, n.tipo === 'plantilla' ? 'Insertar la plantilla' : 'Insertar el texto') : null, h('button.enlace', { type: 'button', onclick: () => E.dlgNota(pid, n) }, 'Editar'))))
          : [RV.vacio('Notas, ideas, investigación y plantillas de la obra: aquí las tienes al lado mientras escribes.')]),
        h('button.boton-borde', { type: 'button', onclick: () => E.dlgNota(pid, null) }, 'Nueva nota'));
      } else if (pestana === 3) {
        const bs = E.borradores(id).reverse();
        cuerpo.append(h('p.apagado', 'Un borrador es otra versión de esta escena: pruébala sin perder la buena, compárala y mezcla lo mejor de cada una.'),
          h('button.boton-borde', { type: 'button', onclick: () => api.escritos('nuevo', 'b', pid, { eid: id, nombre: `Borrador del ${fechaHora(Date.now())}`, texto }).catch(E.err) }, 'Guardar el texto actual como borrador'),
          ...bs.map((b) => h('div.ed-item' + (b.conflicto ? '.conflicto' : ''), h('div.ed-item-cab', h('b', b.nombre || 'Borrador')), h('span.versal', `${E.palabras(b.texto)} palabras${b.conflicto ? ' · se guardó al juntar dos cambios a la vez' : ''}`), h('p.apagado', String(b.texto || '').slice(0, 200)),
            h('div.ed-item-acc', h('button.enlace', { type: 'button', onclick: () => E.dlgCompara(texto, b.nombre || 'Borrador', b.texto || '', (t) => reemplazaTexto(t, true)) }, 'Comparar y mezclar'),
              h('button.enlace', { type: 'button', onclick: async () => { await api.escritos('nuevo', 'b', pid, { eid: id, nombre: `Antes de usar «${b.nombre}»`, texto }); reemplazaTexto(b.texto || '', false); } }, 'Usar este'),
              h('button.enlace.apagado', { type: 'button', onclick: () => api.escritos('borra', b.id).catch(E.err) }, 'Borrar')))));
      } else {
        cuerpo.append(h('p.apagado', 'Cada cierto tiempo (y al salir) se guarda una versión de la escena en este PC: las últimas 40.'));
        api.escritos('versiones', id).then((vs) => {
          const lista = (vs || []).slice().reverse();
          cuerpo.append(...(lista.length ? lista.map((v) => h('div.ed-item', h('div.ed-item-cab', h('b', `${fechaHora(v.u)} · ${v.p} palabras`)), h('p.apagado', String(v.t || '').slice(0, 160)),
            h('div.ed-item-acc', h('button.enlace', { type: 'button', onclick: () => E.dlgCompara(texto, fechaHora(v.u), v.t, (t) => reemplazaTexto(t, true)) }, 'Comparar'), h('button.enlace', { type: 'button', onclick: () => reemplazaTexto(v.t, true) }, 'Restaurar')))) : [RV.vacio('Todavía no hay versiones: se guardan solas mientras escribes.')]));
        }).catch(() => {});
      }
      panel.replaceChildren(h('div.ed-panel-tabs', ['Escena', 'Fichas', 'Notas', 'Borradores', 'Historial'].map((n, i) => h('button', { type: 'button', 'aria-current': i === pestana ? 'page' : null, onclick: () => { pestana = i; pintaPanel(); } }, n))), cuerpo);
    };
    async function reemplazaTexto(t, conInstantanea) {
      if (conInstantanea) await api.escritos('instantanea', id, texto, { fuerza: true });
      cambio(t, t.length, t.length); await guarda();
    }

    // ---- La página
    const volver = h('button.enlace', { type: 'button', onclick: () => { RV.ir({ v: 'obra', id: pid, tab: 0 }); } }, `← ${(E.get(pid) || {}).titulo || 'Obra'}`);
    const salirFoco = h('button.ed-salir', { type: 'button', onclick: () => ponFoco(false) }, 'Salir');
    const nodo = h('div.ed.hoja', { 'data-fuente': ['serif', 'sans', 'mono'][p.fuente], style: { '--tam': `${p.tam}px` } },
      h('div.ed-cab', volver, titulo, h('span.ed-obra', (E.get(pid) || {}).titulo || '')),
      barra, buscaFila,
      h('div.ed-cuerpo', scroll, panel), cuentas, salirFoco);
    reconstruyeFichas(); pinta(); pie(); aplicaPrefs();
    if (ruta.buscar) { requestAnimationFrame(() => { buscaFila.hidden = false; qIn.value = ruta.buscar; actualiza(); salta(1); }); }
    requestAnimationFrame(() => { if (!ruta.buscar) { pagina.focus({ preventScroll: true }); ponSel(texto.length); } });

    const inst = {
      id, nodo,
      cerrar() {
        clearTimeout(timerGuarda); clearTimeout(timerPinta); clearInterval(timerSnap);
        document.removeEventListener('selectionchange', selCambia);
        if (texto !== guardado) { api.escritos('escribe', id, texto).catch(() => {}); guardado = texto; }
        api.escritos('instantanea', id, texto, {}).catch(() => {});
        document.body.classList.remove('ed-foco');
        if (RV.editorActivo === inst) RV.editorActivo = null;
      },
      // Algo ha cambiado en los datos (otro dispositivo, o lo mío ya guardado): sin repintar la página
      refresca() {
        const e = E.get(id);
        if (!e) { RV.ir({ v: 'obra', id: pid, tab: 0 }, { reemplazar: true }); return; }
        if ((e.texto || '') !== guardado && texto === guardado) {
          const s = offsets(); texto = e.texto || ''; guardado = texto; hist.apunta({ text: texto, a: texto.length, b: texto.length }); pinta(); if (s) ponSel(s.a, s.b); pie();
          RV.aviso('Esta escena ha cambiado en otro dispositivo: ya la ves al día.');
        }
        if (document.activeElement !== titulo && titulo.value !== (e.titulo || '')) titulo.value = e.titulo || '';
        reconstruyeFichas();
        if (panelAbierto && !panel.contains(document.activeElement)) pintaPanel();
        pie();
      },
      escape() {
        if (!buscaFila.hidden) { cierraBusca(); return true; }
        if (foco) { ponFoco(false); return true; }
        if (panelAbierto) { panelAbierto = false; pintaPanel(); return true; }
        if (document.activeElement === pagina) { pagina.blur(); return true; }
        return false;
      },
    };
    RV.editorActivo = inst;
    return inst;
  }

  RV.escribirEditorCambio = () => { if (RV.editorActivo) RV.editorActivo.refresca(); };
  RV.escEditor = () => !!(RV.editorActivo && RV.editorActivo.escape());
  window.addEventListener('beforeunload', () => { if (RV.editorActivo) RV.editorActivo.cerrar(); });
})(window.RV);
