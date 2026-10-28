'use strict';
/* global window, document, localStorage, getComputedStyle, requestAnimationFrame */
// La revista de Atrio: utilidades comunes. Nodos (el texto siempre entra como texto, nunca como HTML),
// fechas y horas como en el móvil, titulares que no se parten, avisos y diálogos.
window.RV = window.RV || {};
(function (RV) {
  const api = window.atrio;
  RV.api = api;

  // ---------------------------------------------------------------- Nodos
  // h('div.clase#id', { atributos }, hijos…). Los hijos de texto se crean con createTextNode: así lo que
  // escribe el usuario (títulos, opiniones, apuntes) nunca se interpreta como HTML.
  RV.h = function h(sel, attrs, ...kids) {
    const m = /^([a-z0-9]+)?((?:[.#][\w-]+)*)$/i.exec(sel);
    const el = document.createElement((m && m[1]) || 'div');
    if (m && m[2]) {
      for (const part of m[2].match(/[.#][\w-]+/g)) {
        if (part[0] === '.') el.classList.add(part.slice(1)); else el.id = part.slice(1);
      }
    }
    if (attrs && (typeof attrs !== 'object' || attrs instanceof window.Node || Array.isArray(attrs))) { kids.unshift(attrs); attrs = null; }
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        if (v == null || v === false) continue;
        if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
        else if (k === 'style' && typeof v === 'object') {
          for (const [sk, sv] of Object.entries(v)) {
            if (sv == null) continue;
            if (sk.startsWith('--')) el.style.setProperty(sk, sv); else el.style[sk] = sv;
          }
        } else if (k === 'text') el.textContent = String(v);
        else if (k === 'value') el.value = String(v);
        else el.setAttribute(k, v === true ? '' : String(v));
      }
    }
    for (const k of kids.flat(Infinity)) {
      if (k == null || k === false) continue;
      el.append(k instanceof window.Node ? k : document.createTextNode(String(k)));
    }
    return el;
  };
  RV.$ = (s, r = document) => r.querySelector(s);
  RV.$$ = (s, r = document) => [...r.querySelectorAll(s)];
  // vaciable: si algo está esperando para guardarse (Escribir), RV.vaciarPendientes() lo hace ya (al cerrar la ventana o salir)
  const pendientes = new Set();
  RV.debounce = (fn, ms, vaciable) => {
    let t; let args = null;
    const ejecuta = () => { t = null; pendientes.delete(ejecuta); const a = args; args = null; return fn(...a); };
    return (...a) => { clearTimeout(t); args = a; t = setTimeout(ejecuta, ms); if (vaciable) pendientes.add(ejecuta); };
  };
  RV.vaciarPendientes = async () => {
    const todos = [...pendientes];
    for (const f of todos) { try { await f(); } catch { /* */ } }
  };
  RV.num = (v, d = 0) => (Number.isFinite(Number(v)) ? Number(v) : d);
  RV.plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;
  RV.limpiaError = (e) => String((e && e.message) || e || 'Algo ha fallado').replace(/^Error invoking remote method '[^']+': (Error: )?/, '');

  // ---------------------------------------------------------------- Nombres
  // Igual que normName() del móvil: sin tildes, en minúsculas y solo letras y números
  RV.normName = (s) => String(s || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^\p{L}\p{Nd}]+/gu, '');

  // Id de una ficha creada a mano: igual que customItemId() del móvil (FNV-1a de 64 bits sobre el nombre
  // normalizado), así la misma ficha tiene el mismo id en el móvil, en el PC y en el de tu pareja
  RV.idPropio = (name) => {
    const M = (1n << 64n) - 1n;
    let x = 0xcbf29ce484222325n;
    const s = RV.normName(name);
    for (let i = 0; i < s.length; i++) {
      x ^= BigInt(s.charCodeAt(i));
      x = (x * 0x100000001b3n) & M;
    }
    return Number((x & 0x7fffffffffffffffn) % 9000000000000n + 100000000000n);
  };

  // ---------------------------------------------------------------- Fechas (días desde 1970, como LocalDate.toEpochDay)
  const DIA = 86400000;
  RV.DIA = DIA;
  RV.diaDe = (date) => Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DIA);
  RV.hoy = () => RV.diaDe(new Date());
  const fDia = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const fDiaCorto = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', timeZone: 'UTC' });
  const fMomento = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  const fLargo = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const sinPunto = (s) => s.replace(/\./g, '');
  RV.fmtDia = (d) => (d > 0 ? sinPunto(fDia.format(new Date(d * DIA))) : '');
  RV.fmtDiaCorto = (d) => (d > 0 ? sinPunto(fDiaCorto.format(new Date(d * DIA))) : '');
  RV.fmtMomento = (t) => (t > 0 ? sinPunto(fMomento.format(new Date(t))) : '');
  RV.fmtLargo = (date) => fLargo.format(date);
  RV.isoDia = (d) => (d > 0 ? new Date(d * DIA).toISOString().slice(0, 10) : '');
  RV.deIso = (s) => { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || ''); return m ? Math.floor(Date.UTC(+m[1], +m[2] - 1, +m[3]) / DIA) : 0; };
  RV.diaDelAno = (date) => Math.floor((Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) - Date.UTC(date.getFullYear(), 0, 0)) / DIA);
  const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
  RV.hace = (ts) => {
    if (!ts) return '';
    const s = (ts - Date.now()) / 1000;
    const a = Math.abs(s);
    if (a < 60) return 'ahora mismo';
    if (a < 3600) return rtf.format(Math.round(s / 60), 'minute');
    if (a < 86400) return rtf.format(Math.round(s / 3600), 'hour');
    if (a < 86400 * 30) return rtf.format(Math.round(s / 86400), 'day');
    if (a < 86400 * 365) return rtf.format(Math.round(s / (86400 * 30)), 'month');
    return rtf.format(Math.round(s / (86400 * 365)), 'year');
  };

  // ---------------------------------------------------------------- Horas
  // «12 h 30 min», «45 min» o «0 h» (hoursText del móvil)
  RV.horas = (min) => {
    const m0 = Math.max(0, Math.round(RV.num(min)));
    const h = Math.floor(m0 / 60);
    const m = m0 % 60;
    if (h > 0 && m > 0) return `${h} h ${m} min`;
    if (h > 0) return `${h} h`;
    if (m > 0) return `${m} min`;
    return '0 h';
  };
  // Para cifras grandes: [«12 h», «30 min»]
  RV.horasPartes = (min) => {
    const m0 = Math.max(0, Math.round(RV.num(min)));
    const h = Math.floor(m0 / 60);
    const m = m0 % 60;
    if (!h) return [`${m} min`, ''];
    return [`${h} h`, m ? `${m} min` : ''];
  };

  // ---------------------------------------------------------------- Tema y acento
  RV.ACENTOS = [
    ['rojo', 'Rojo', '#C8372D', '#E0533F'],
    ['azul', 'Azul', '#2F5D8A', '#7FA7C4'],
    ['verde', 'Verde', '#3F7A4A', '#8FB873'],
    ['mostaza', 'Mostaza', '#B8861B', '#E0B456'],
    ['morado', 'Morado', '#7A4E8C', '#B48BC4'],
    ['naranja', 'Naranja', '#C8642D', '#E8894F'],
    ['rosa', 'Rosa', '#B83A6B', '#E07AA1'],
    ['tinta', 'Tinta', '#15120F', '#F1EBE0'],
  ];
  RV.TEMAS = [['claro', 'Claro'], ['oscuro', 'Oscuro'], ['auto', 'Automático']];
  // Fondos de color (los mismos que el móvil): id, nombre, papel, tinta y si es oscuro. Con uno elegido manda sobre el tema.
  RV.FONDOS = [
    ['papelreal', 'Papel de verdad', '#F8F2DF', '#2B2418', false],
    ['granate', 'Granate', '#5A1424', '#F8EDE8', true],
    ['bosque', 'Verde bosque', '#12382B', '#EDF3E8', true],
    ['marino', 'Azul marino', '#10264A', '#E9EEF7', true],
    ['petroleo', 'Petróleo', '#0F3D44', '#E7F2F1', true],
    ['berenjena', 'Berenjena', '#3C1A4A', '#F6EAF4', true],
    ['chocolate', 'Chocolate', '#3A2218', '#F4E8DB', true],
    ['pizarra', 'Pizarra', '#232D38', '#E8EDF2', true],
    ['mostaza', 'Mostaza', '#D9A53A', '#2A1E08', false],
  ];
  RV.aplicarTema = (rev) => {
    const tema = RV.TEMAS.some(([id]) => id === (rev && rev.tema)) ? rev.tema : 'auto';
    const acento = RV.ACENTOS.some(([id]) => id === (rev && rev.acento)) ? rev.acento : 'rojo';
    const fondo = RV.FONDOS.find(([id]) => id === (rev && rev.fondo));
    const raiz = document.documentElement;
    // Con un fondo de color: su modo claro u oscuro manda, y el acento «rojo» pasa a ser el del propio fondo
    if (fondo) { raiz.dataset.fondo = fondo[0]; raiz.dataset.tema = fondo[4] ? 'oscuro' : 'claro'; raiz.dataset.acento = acento === 'rojo' ? 'fondo' : acento; }
    else { delete raiz.dataset.fondo; raiz.dataset.tema = tema; raiz.dataset.acento = acento; }
    // Sin animaciones si lo pides (Ajustes → Comportamiento) o si el sistema pide menos movimiento
    let menos = false;
    try { menos = window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch { /* */ }
    if (document.body) document.body.classList.toggle('quieto', !!(rev && rev.quieto) || menos);
    // Copia local solo para el arranque (tema-inicial.js); la de verdad está en la configuración de Atrio
    try { localStorage.setItem('revista:tema', JSON.stringify({ tema, acento, fondo: fondo ? fondo[0] : '' })); } catch { /* */ }
  };
  RV.oscuro = () => {
    const t = document.documentElement.dataset.tema;
    return t === 'oscuro' || (t !== 'claro' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  };

  // ---------------------------------------------------------------- Titulares que no se parten
  // Como FitTitle del móvil: si la palabra más larga no cabe a lo ancho, se baja el tamaño de la letra hasta que
  // quepa. Se mide con un lienzo (sin recolocar la página por cada titular): primero se leen todas las medidas
  // y después se escriben todos los tamaños.
  let lienzo = null;
  RV.ajustar = (root = document) => {
    const els = RV.$$('.ajusta', root).filter((el) => el.isConnected);
    if (!els.length) return;
    for (const el of els) el.style.fontSize = '';
    const medidas = els.map((el) => {
      const cs = getComputedStyle(el);
      return {
        el,
        ancho: el.clientWidth - (parseFloat(cs.paddingLeft) || 0) - (parseFloat(cs.paddingRight) || 0),
        size: parseFloat(cs.fontSize) || 16,
        ls: parseFloat(cs.letterSpacing) || 0,
        font: `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`,
        min: parseFloat(el.dataset.min) || 14,
        mayus: cs.textTransform === 'uppercase',
      };
    });
    if (!lienzo) lienzo = document.createElement('canvas');
    const ctx = lienzo.getContext('2d');
    for (const m of medidas) {
      if (m.ancho <= 0) continue;
      ctx.font = m.font;
      let ancha = 0;
      // (En mayúsculas por CSS se mide el texto ya en mayúsculas: ocupa más)
      for (const w of (m.mayus ? (m.el.textContent || '').toLocaleUpperCase('es') : (m.el.textContent || '')).split(/\s+/)) {
        if (!w) continue;
        ancha = Math.max(ancha, ctx.measureText(w).width + m.ls * Math.max(0, w.length - 1));
      }
      // Un poco de margen, por si la letra de verdad es algo más ancha que la medida
      const cabe = m.ancho * 0.97;
      if (ancha > cabe) m.el.style.fontSize = `${Math.max(m.min, Math.floor((m.size * cabe) / ancha))}px`;
    }
  };
  // Talla del titular según lo largo que sea (headlineSize del móvil): sin portada, el título es lo que manda.
  // Devuelve una clase que escala el tamaño de la letra en el CSS (--k).
  RV.tallaTitular = (t) => {
    const s = String(t || '').trim();
    const larga = Math.max(0, ...s.split(/\s+/).map((w) => w.length));
    if (s.length <= 8 && larga <= 8) return '';
    if (s.length <= 16 && larga <= 10) return '.t-medio';
    if (s.length <= 30 && larga <= 13) return '.t-largo';
    return '.t-muylargo';
  };
  // Pantallas muy grandes sin escalar (una tele 4K o un monitor de 1440p al 100 %): todo se amplía para que se
  // vea como en una de 1920 px. El modo Juegos, desde 2300 px (se mira desde el sofá); la revista, desde 3000.
  RV.escala = () => {
    const w = window.innerWidth;
    const juegos = document.body && document.body.dataset.modo === 'juegos';
    const z = juegos && w >= 2300 ? w / 1920 : w >= 3000 ? w / 2560 : 1;
    const v = z === 1 ? '' : String(Math.round(z * 1000) / 1000);
    if (document.documentElement.style.zoom !== v) document.documentElement.style.zoom = v;
  };
  window.addEventListener('resize', RV.debounce(RV.escala, 60));
  // Al cambiar el tamaño de la ventana y cuando terminan de cargar las letras, se vuelve a ajustar
  const reajustar = RV.debounce(() => RV.ajustar(document), 80);
  window.addEventListener('resize', reajustar);
  if (document.fonts) {
    document.fonts.addEventListener('loadingdone', reajustar);
    document.fonts.ready.then(reajustar);
  }

  // ---------------------------------------------------------------- Avisos
  let avisoT = null;
  // Con «accion» ({ texto, fn }) lleva un botón (p. ej. «Deshacer») y dura más
  RV.aviso = (msg, tipo, accion) => {
    const el = document.getElementById('aviso');
    if (!el || !msg) return;
    el.textContent = String(msg);
    if (accion) {
      el.append(RV.h('button.aviso-accion', { type: 'button', onclick: () => { el.classList.remove('ver', 'con-accion'); clearTimeout(avisoT); accion.fn(); } }, accion.texto));
    }
    el.classList.toggle('error', tipo === 'error');
    el.classList.toggle('con-accion', !!accion);
    el.classList.add('ver');
    clearTimeout(avisoT);
    avisoT = setTimeout(() => el.classList.remove('ver', 'con-accion'), accion ? 8000 : tipo === 'error' ? 6500 : 3200);
  };

  // ---------------------------------------------------------------- Diálogos
  // Un papel con borde de tinta encima de la página. Esc cierra el de arriba (lo gestiona app.js).
  const pila = [];
  RV.hayDialogo = () => pila.length > 0;
  RV.cerrarDialogo = () => { const d = pila[pila.length - 1]; if (d) d.cerrar(); return !!d; };
  RV.dialogo = (o) => {
    const h = RV.h;
    const antes = document.activeElement;
    const capa = document.getElementById('capa');
    const cuerpo = h('div.cuerpo-dialogo');
    if (o.cuerpo) cuerpo.append(...[].concat(o.cuerpo).filter(Boolean));
    const pie = h('div.pie-dialogo');
    const caja = h(`div.dialogo${o.ancho ? '.ancho' : ''}`, { role: 'dialog', 'aria-modal': 'true', 'aria-label': o.titulo || '' },
      o.ante ? h('div.ante', o.ante) : null,
      o.titulo ? h('h2.ajusta', { 'data-min': '20' }, o.titulo) : null,
      h('div.filete'),
      cuerpo,
      pie);
    const velo = h('div.velo', caja);
    const d = {
      caja, cuerpo, pie,
      cerrar: () => {
        const i = pila.indexOf(d);
        if (i < 0) return;
        pila.splice(i, 1);
        velo.remove();
        if (o.alCerrar) o.alCerrar();
        // Lo que estaba enfocado (si la página se ha redibujado mientras, lo mismo en la página nueva)
        let vuelta = antes;
        if (vuelta && !vuelta.isConnected && vuelta.dataset && vuelta.dataset.clave) vuelta = document.querySelector(`[data-clave="${window.CSS.escape(vuelta.dataset.clave)}"]`);
        if (vuelta && vuelta.isConnected && typeof vuelta.focus === 'function' && !(pila.length && !pila[pila.length - 1].caja.contains(vuelta))) vuelta.focus({ preventScroll: true });
      },
    };
    const acciones = o.acciones || [{ texto: 'Cerrar', fn: () => d.cerrar() }];
    for (const a of acciones) {
      if (a === 'hueco') { pie.append(h('span.hueco')); continue; }
      if (!a) continue;
      const cls = a.principal ? 'button.boton-tinta' : a.peligro ? 'button.enlace.acento' : 'button.enlace';
      pie.append(h(cls + (a.clase ? `.${a.clase}` : ''), { type: 'button', onclick: () => a.fn(d) }, a.texto));
    }
    if (o.fueraCierra) velo.addEventListener('mousedown', (e) => { if (e.target === velo) d.cerrar(); });
    capa.append(velo);
    pila.push(d);
    // El foco, al primer campo (o a lo que se pida)
    requestAnimationFrame(() => {
      RV.ajustar(caja);
      const f = caja.querySelector('[data-autofocus]') || caja.querySelector('input:not([type="hidden"]), textarea, select') || caja.querySelector('button');
      if (f) f.focus({ preventScroll: true });
    });
    return d;
  };
  // Una capa a pantalla completa (el visor de capturas): Esc la cierra como a un diálogo. Devuelve cerrar()
  RV.superponer = (nodo, alCerrar) => {
    const antes = document.activeElement;
    const d = {
      cerrar: () => {
        const i = pila.indexOf(d);
        if (i < 0) return;
        pila.splice(i, 1);
        nodo.remove();
        if (alCerrar) alCerrar();
        // Lo que estaba enfocado (si la página se ha redibujado mientras, lo mismo en la página nueva)
        let vuelta = antes;
        if (vuelta && !vuelta.isConnected && vuelta.dataset && vuelta.dataset.clave) vuelta = document.querySelector(`[data-clave="${window.CSS.escape(vuelta.dataset.clave)}"]`);
        if (vuelta && vuelta.isConnected && typeof vuelta.focus === 'function' && !(pila.length && !pila[pila.length - 1].caja.contains(vuelta))) vuelta.focus({ preventScroll: true });
      },
    };
    document.getElementById('capa').append(nodo);
    pila.push(d);
    const b = nodo.querySelector('button');
    if (b) b.focus({ preventScroll: true });
    return d.cerrar;
  };
  RV.confirmar = (titulo, texto, boton, fn) => RV.dialogo({
    ante: 'Confirmar', titulo,
    cuerpo: RV.h('p.texto', texto),
    acciones: ['hueco', { texto: 'Cancelar', fn: (d) => d.cerrar() }, { texto: boton, principal: true, fn: async (d) => { d.cerrar(); await fn(); } }],
  });

  // ---------------------------------------------------------------- Piezas de maqueta
  const h = RV.h;
  // Título de sección con cuadradito del acento (y una acción a la derecha, opcional)
  RV.seccion = (texto, accion) => h('div.seccion', h('span', texto), accion ? h('button.enlace.acento.mas', { type: 'button', onclick: accion.fn }, accion.texto) : null);
  // Antetítulo de casilla
  RV.etiqueta = (texto, accion) => h('div.etiqueta', h('span.t', texto),
    accion ? h('button.enlace', { type: 'button', onclick: accion.fn }, accion.texto) : null);
  // Línea dato · valor con puntos guía
  RV.dato = (k, v, serif) => h('li.dato', h('span.k', k), h('span.guia'), h(`span.v${serif ? '.serif' : ''}`, v));
  RV.vacio = (texto, grande) => h(`p.vacio${grande ? '.grande' : ''}`, texto);
  // Campo con su etiqueta en versalitas
  // Un <label> solo si el control es un campo (pulsar el texto lo enfoca). Con un grupo de botones, no: si un botón se
  // vuelve a pintar al pulsarlo, el label pulsaría el primero (y se elegiría otra cosa)
  RV.campo = (etiqueta, control, ayuda) => h(control && /^(INPUT|SELECT|TEXTAREA)$/.test(control.tagName) ? 'label.campo' : 'div.campo', h('span', etiqueta), control, ayuda ? h('small', ayuda) : null);
  RV.entrada = (valor, attrs = {}) => h('input.entrada', { type: 'text', spellcheck: 'false', ...attrs, value: valor == null ? '' : valor });
  RV.areaTexto = (valor, attrs = {}) => { const t = h('textarea.entrada', { rows: '6', spellcheck: 'true', ...attrs }); t.value = valor || ''; return t; };

  // Deslizador de nota (0–100): un filete fino, la marca de tinta y la cifra en serif
  RV.deslizador = (etiqueta, valor, alCambiar, principal) => {
    let v = Math.max(0, Math.min(100, Math.round(RV.num(valor, 50))));
    const relleno = h('span.relleno');
    const cifra = h('span.v', String(v));
    const input = h('input', { type: 'range', min: '0', max: '100', step: '1', 'aria-label': etiqueta, value: String(v) });
    const pintar = () => { relleno.style.width = `${v}%`; cifra.textContent = String(v); };
    input.addEventListener('input', () => { v = Number(input.value); pintar(); alCambiar(v); });
    pintar();
    return h(`div.criterio${principal ? '.principal' : ''}`, h('span.k', etiqueta), h('div.desliz', h('span.pista'), relleno, input), cifra);
  };
})(window.RV);
