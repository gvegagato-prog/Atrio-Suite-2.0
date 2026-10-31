'use strict';
/* global window, document, requestAnimationFrame */
// Ajustes: la revista (tema y acento) y la cuenta; las secciones del PC (bibliotecas, mando, móvil…) están en sistema.js.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  RV.vistas = RV.vistas || {};
  // Atrio no ha encontrado la clave web de Firebase: se pide (y se sigue pidiendo aunque se redibuje la página)
  let pedirClave = false;

  const bloque = (titulo, nota, ...contenido) => h('section.ajuste',
    h('div', h('div.ante', 'Ajustes'), h('h2', titulo), nota ? h('p.nota', nota) : null),
    h('div', contenido));

  async function guardarRevista(cambio) {
    const actual = { tema: 'auto', acento: 'rojo', fondo: '', ...(D.config().revista || {}) };
    const nuevo = { ...actual, ...cambio };
    RV.aplicarTema(nuevo);
    try { D.setPc(await RV.api.patchConfig({ revista: nuevo })); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    RV.refrescar();
  }

  function cuenta() {
    const s = D.cuenta().status || {};
    if (s.signed) {
      const linea = s.err ? s.err : `${RV.plural(s.count || 0, 'ficha', 'fichas')} · sincronizado ${s.lastSync ? RV.hace(s.lastSync) : 'nunca'}`;
      const msg = h('p.mensaje.bien');
      return [
        h('p.estado-cuenta', `Has entrado como ${s.user}`),
        h(`p.versal${s.err ? '.peligro' : ''}`, { style: { marginTop: '6px' } }, linea),
        h('div.fila-botones',
          h('button.boton-tinta', {
            type: 'button',
            onclick: async (e) => {
              const b = e.currentTarget;
              b.disabled = true;
              msg.textContent = 'Sincronizando…';
              try { D.setCuenta(await RV.api.ronronSync()); RV.aviso('Al día'); } catch (err) { RV.aviso(RV.limpiaError(err), 'error'); }
              RV.refrescar();
            },
          }, 'Sincronizar ahora'),
          h('button.enlace', {
            type: 'button',
            onclick: () => RV.confirmar('¿Salir de tu cuenta?', 'Atrio deja de ver tus fichas en este PC (en el móvil no cambia nada). Puedes volver a entrar cuando quieras.', 'Salir', async () => {
              try { D.setCuenta(await RV.api.ronronLogout()); RV.aviso('Has salido de tu cuenta'); } catch (err) { RV.aviso(RV.limpiaError(err), 'error'); }
              RV.refrescar();
            }),
          }, 'Salir de la cuenta')),
        msg,
        h('p.nota', 'La contraseña no se guarda: Atrio guarda un pase de sesión en tu carpeta de configuración, solo legible por tu usuario.'),
      ];
    }
    // Entrar: usuario y contraseña (la clave web de Firebase solo si Atrio no la encuentra)
    const user = RV.entrada(s.user || '', { autocomplete: 'username', placeholder: 'Usuario', 'data-foco': 'login-user' });
    const pass = RV.entrada('', { type: 'password', autocomplete: 'current-password', placeholder: 'Contraseña', 'data-foco': 'login-pass' });
    const clave = RV.entrada('', { placeholder: 'Empieza por AIza', class: 'entrada mono', 'data-foco': 'login-clave' });
    const campoClave = RV.campo('Clave web de Firebase', clave, 'Solo la primera vez, si el móvil todavía no la ha dejado guardada. Está en la consola de Firebase → Configuración del proyecto → General → Clave de API web.');
    campoClave.style.display = pedirClave ? '' : 'none';
    const msg = h('p.mensaje');
    const entrar = async () => {
      msg.classList.add('bien');
      msg.textContent = 'Entrando…';
      try {
        const st = await RV.api.ronronLogin(user.value.trim(), pass.value, clave.value.trim());
        pass.value = '';
        D.setCuenta(st);
        RV.aviso(`Hola, ${st.status.user}`);
        RV.refrescar();
      } catch (e) {
        msg.classList.remove('bien');
        const m = RV.limpiaError(e);
        if (m === 'FALTA_CLAVE') {
          pedirClave = true;
          campoClave.style.display = '';
          msg.textContent = 'Falta la clave web de Firebase. Pégala abajo y vuelve a pulsar Entrar.';
          clave.focus();
        } else msg.textContent = m;
      }
    };
    for (const i of [user, pass, clave]) i.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); if (i === user) pass.focus(); else entrar(); } });
    return [
      h('div.formulario', RV.campo('Usuario de Atrio', user), RV.campo('Contraseña', pass), campoClave),
      h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: entrar }, 'Entrar')),
      msg,
    ];
  }

  function steamgriddb() {
    const cfg = D.config();
    const clave = RV.entrada(cfg.sgdbKey || '', { placeholder: 'Tu clave de la API de SteamGridDB', class: 'entrada mono', 'data-foco': 'sgdb' });
    const msg = h('p.mensaje.bien', cfg.sgdbKey ? 'Clave guardada.' : 'Sin clave todavía.');
    const guardar = async () => {
      const k = clave.value.trim();
      msg.classList.add('bien');
      if (k) {
        msg.textContent = 'Probando la clave…';
        try { await RV.api.sgdbTest(k); } catch (e) { msg.classList.remove('bien'); msg.textContent = `SteamGridDB no acepta esa clave: ${RV.limpiaError(e)}`; return; }
      }
      try {
        D.setPc(await RV.api.patchConfig({ sgdbKey: k }));
        msg.textContent = k ? 'Clave correcta y guardada.' : 'Clave quitada.';
      } catch (e) { msg.classList.remove('bien'); msg.textContent = RV.limpiaError(e); }
    };
    clave.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); guardar(); } });
    return [
      h('div.formulario', RV.campo('Clave de la API', clave)),
      h('div.fila-botones',
        h('button.boton-tinta', { type: 'button', onclick: guardar }, 'Guardar'),
        h('button.enlace', { type: 'button', onclick: () => RV.api.openExternal('https://www.steamgriddb.com/profile/preferences/api') }, 'Conseguir una clave →')),
      msg,
    ];
  }

  // Tu nube (kDrive): el token y el número de tu kDrive se quedan en este PC
  function nube() {
    const n = D.config().nube || {};
    const token = RV.entrada('', { placeholder: n.token ? 'Token guardado (escribe otro para cambiarlo)' : 'Tu token de API de kDrive', class: 'entrada mono', 'data-foco': 'nube' });
    const drive = RV.entrada(n.drive || '', { placeholder: 'Número de tu kDrive (o su dirección)', class: 'entrada mono' });
    const msg = h('p.mensaje.bien', n.token && n.drive ? `Conectada a tu kDrive ${n.drive}.` : 'Sin conectar.');
    // Subir las capturas: Sí / No (como los interruptores de Sistema)
    const subir = h('div.j-grupo', { role: 'group' });
    const pintarSubir = (v) => subir.replaceChildren(...[[true, 'Sí'], [false, 'No']].map(([x, t]) => h('button.j-opt', {
      type: 'button', 'aria-pressed': String(x === v),
      onclick: async () => { D.setPc(await RV.api.patchConfig({ nube: { shots: x } })); pintarSubir(x); },
    }, t)));
    pintarSubir(n.shots !== false);
    const conectar = async (t) => {
      msg.classList.add('bien');
      msg.textContent = t ? 'Probando…' : 'Desconectando…';
      try {
        const r = await RV.api.nubeTest(t, drive.value.trim());
        D.setPc({ config: r.config });
        msg.textContent = r.msg;
        token.value = '';
      } catch (e) { msg.classList.remove('bien'); msg.textContent = RV.limpiaError(e); }
    };
    return [
      h('div.formulario', RV.campo('Token de API', token), RV.campo('Tu kDrive', drive)),
      h('div.interruptor', h('div', h('span.t', 'Subir las capturas'), h('small', 'Todas las que hagas en este PC, a Atrio/Capturas/<juego>')), subir),
      h('div.fila-botones',
        h('button.boton-tinta', { type: 'button', onclick: () => conectar(token.value.trim() || n.token || '') }, 'Conectar'),
        n.token ? h('button.enlace', { type: 'button', onclick: () => conectar('') }, 'Desconectar') : null,
        h('button.enlace', { type: 'button', onclick: () => RV.api.openExternal('https://manager.infomaniak.com/v3/ng/profile/user/token/list') }, 'Crear un token →')),
      msg,
    ];
  }

  RV.guardarRevista = guardarRevista;
  if (RV.ajustesPc) RV.ajustesPc.guardarRevista = guardarRevista;

  // Las secciones de Ajustes (LB / RB pasan de una a otra)
  const SECCIONES = [
    ['revista', 'La revista'], ['cuenta', 'Cuenta'], ['bibliotecas', 'Bibliotecas'], ['propios', 'Juegos añadidos'],
    ['emuladores', 'Emuladores'], ['ocultos', 'Ocultos'], ['mando', 'Mando'], ['comportamiento', 'Comportamiento'],
    ['movil', 'Móvil'], ['streaming', 'Streaming'],
  ];
  // (en la edición «solo revista» no hay nada del lanzador: solo la revista, la cuenta y la lista negra)
  const secciones = () => (RV.soloRevista ? [['revista', 'La revista'], ['cuenta', 'Cuenta'], ['ocultos', 'Lista negra']] : SECCIONES);
  let seccion = 'revista';
  RV.seccionAjustes = (paso) => {
    const SECC = secciones();
    let i = SECC.findIndex(([id]) => id === seccion);
    i = (i + paso + SECC.length) % SECC.length;
    seccion = SECC[i][0];
    RV.ir({ v: 'ajustes', sec: seccion }, { reemplazar: true });
    requestAnimationFrame(() => { const b = document.querySelector(`.ajustes .pestana[data-sec="${seccion}"]`); if (b) b.focus({ preventScroll: true }); });
  };

  // La lista negra de juegos (la de tu cuenta, igual que en el móvil): no salen en ningún sitio ni se rastrean
  RV.listaNegra = (nombre) => RV.confirmar('¿A la lista negra?',
    `«${nombre}» dejará de salir en ningún sitio (biblioteca, portada, diario, calendario), en todos tus dispositivos, y su ficha de la cuenta se borrará. Puedes quitarlo después en Ajustes → Ocultos.`,
    'Ocultar y borrar', async () => {
      if (await RV.editar({ op: 'negraPut', item: { id: RV.idPropio(nombre), n: nombre } }, `${nombre}: a la lista negra`)) {
        try { RV.api.rescan && await RV.api.rescan(); } catch { /* */ }
        RV.refrescar();
      }
    });
  function laRevista() {
    const rev = { tema: 'auto', acento: 'rojo', ...(D.config().revista || {}) };
    return [
      // (solo revista, el Mac) Pantalla completa: al abrir, y ahora mismo
      RV.soloRevista ? bloque('Pantalla completa', 'Abrir Atrio a pantalla completa, también para escribir. F11 o ⌃⌘F la ponen y la quitan al momento, en cualquier página.',
        h('div.opciones', [[true, 'Abrir a pantalla completa'], [false, 'Abrir en ventana']].map(([v, n]) => h('button.opcion', {
          type: 'button', 'aria-pressed': String((D.config().behavior || {}).fullscreen !== false) === String(v),
          onclick: async () => { try { D.setPc(await RV.api.patchConfig({ behavior: { fullscreen: v } })); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); } RV.refrescar(); },
        }, n)))) : null,
      bloque('Tema', 'Papel claro, papel oscuro o lo que diga el sistema.',
        h('div.opciones', RV.TEMAS.map(([id, n]) => h('button.opcion', {
          type: 'button', 'aria-pressed': String(rev.tema === id), onclick: () => guardarRevista({ tema: id }),
        }, n)))),
      bloque('Fondo', 'Colores hondos y sobrios para toda la revista, los mismos que en el móvil. «Papel» vuelve al tema de arriba.',
        h('div.muestras', [['', 'Papel', RV.oscuro() ? '#141210' : '#F3EEE4', RV.oscuro() ? '#F1EBE0' : '#15120F'], ...RV.FONDOS.map(([id, n, papel, tinta]) => [id, n, papel, tinta])].map(([id, n, papel, tinta]) => h('button.muestra', {
          type: 'button', 'aria-pressed': String(!RV.hexValido(rev.fondoPropio) && (rev.fondo || '') === id), onclick: () => guardarRevista({ fondo: id, fondoPropio: '' }), 'aria-label': `Fondo ${n}`,
        }, h('span.color', { style: { background: papel, color: tinta, border: '1px solid var(--linea)', display: 'grid', placeItems: 'center', fontFamily: 'var(--serif)', fontWeight: '700' } }, 'Aa'), h('span.nombre', n))))),
      bloque('Acento', 'El color de imprenta: antetítulos, capitulares, enlaces y lo que está elegido.',
        h('div.muestras', RV.ACENTOS.map(([id, n, claro, oscuro]) => h('button.muestra', {
          type: 'button', 'aria-pressed': String(!RV.hexValido(rev.acentoPropio) && rev.acento === id), onclick: () => guardarRevista({ acento: id, acentoPropio: '' }), 'aria-label': `Acento ${n}`,
        }, h('span.color', { style: { background: RV.oscuro() ? oscuro : claro } }), h('span.nombre', n))))),
      bloque('A tu manera', 'El color que quieras para el fondo, el texto y el acento, y las letras de los títulos y del texto. Las letras tienen que estar instaladas en este equipo; si no, siguen las de Atrio.',
        h('div.a-tu-manera',
          colorPropio('Fondo', 'fondoPropio', rev, '--papel'),
          colorPropio('Texto', 'tintaPropia', rev, '--tinta'),
          colorPropio('Acento', 'acentoPropio', rev, '--acento'),
          letraPropia('Letra de los títulos', 'letraTitulos', rev, 'var(--serif)'),
          letraPropia('Letra del texto', 'letraTexto', rev, 'var(--sans)'))),
    ];
  }

  // El color que se ve ahora (el del tema si aún no hay uno elegido), en #RRGGBB para el selector
  function hexActual(variable) {
    const c = document.createElement('canvas').getContext('2d');
    c.fillStyle = '#000'; c.fillStyle = getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || '#000';
    c.fillRect(0, 0, 1, 1);
    const [r, g, b] = c.getImageData(0, 0, 1, 1).data;
    return '#' + [r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('').toUpperCase();
  }

  function colorPropio(nombre, clave, rev, variable) {
    const valor = RV.hexValido(rev[clave]);
    const inicial = valor || hexActual(variable);
    // Mientras se arrastra, se ve al momento; al soltar (o al escribir un código entero), se guarda
    const prueba = (v) => RV.aplicarTema({ ...rev, [clave]: v });
    const codigo = h('input.codigo', { type: 'text', value: inicial, maxlength: '7', spellcheck: 'false', 'aria-label': `Código del ${nombre.toLowerCase()}`,
      onchange: () => { const v = RV.hexValido(codigo.value.startsWith('#') ? codigo.value : '#' + codigo.value); if (v) guardarRevista({ [clave]: v }); else codigo.value = inicial; } });
    const selector = h('input.selector-color', { type: 'color', value: inicial.toLowerCase(), 'aria-label': nombre,
      oninput: () => { codigo.value = selector.value.toUpperCase(); prueba(selector.value.toUpperCase()); },
      onchange: () => guardarRevista({ [clave]: selector.value.toUpperCase() }) });
    return h('div.propio', h('span.versal', nombre), selector, codigo,
      h('span.estado', valor ? '' : 'Automático'),
      valor ? h('button.enlace', { type: 'button', onclick: () => guardarRevista({ [clave]: '' }) }, 'Quitar') : null);
  }

  // Letras comunes en Windows, macOS y Linux (y las de la app del móvil, por si las tienes instaladas)
  const LETRAS = ['Georgia', 'Times New Roman', 'Palatino Linotype', 'Book Antiqua', 'Garamond', 'Baskerville', 'Didot', 'Hoefler Text', 'Iowan Old Style',
    'Charter', 'Cambria', 'Constantia', 'Liberation Serif', 'DejaVu Serif', 'Noto Serif', 'Arial', 'Helvetica', 'Helvetica Neue', 'Verdana', 'Tahoma',
    'Trebuchet MS', 'Segoe UI', 'Calibri', 'Candara', 'Avenir', 'Avenir Next', 'Futura', 'Gill Sans', 'Optima', 'SF Pro Text', 'Ubuntu', 'Cantarell',
    'Liberation Sans', 'DejaVu Sans', 'Noto Sans', 'Courier New', 'Menlo', 'Consolas', 'Fraunces', 'Inter', 'Playfair Display', 'Lora', 'Merriweather',
    'EB Garamond', 'Libre Baskerville', 'Cormorant Garamond', 'DM Serif Display', 'Roboto', 'Open Sans', 'Source Sans 3', 'Work Sans', 'IBM Plex Sans'];
  let letrasEquipo = null;
  async function letrasInstaladas() {
    if (letrasEquipo) return letrasEquipo;
    let nombres = [];
    // (las que de verdad hay en el equipo, si el sistema deja leerlas; si no, la lista de arriba)
    try { if (window.queryLocalFonts) nombres = [...new Set((await window.queryLocalFonts()).map((f) => f.family))]; } catch { /* sin permiso */ }
    letrasEquipo = (nombres.length ? nombres : LETRAS).sort((a, b) => a.localeCompare(b, 'es'));
    return letrasEquipo;
  }

  function letraPropia(nombre, clave, rev, actual) {
    const valor = typeof rev[clave] === 'string' ? rev[clave] : '';
    const id = `letras-${clave}`;
    const lista = h('datalist', { id }, LETRAS.map((n) => h('option', { value: n })));
    const campo = h('input.letra', { type: 'text', value: valor, list: id, placeholder: 'La de Atrio', spellcheck: 'false', 'aria-label': nombre,
      style: { fontFamily: valor ? `"${valor}", ${actual}` : actual },
      onfocus: async () => { const n = await letrasInstaladas(); lista.replaceChildren(...n.map((x) => h('option', { value: x }))); },
      onchange: () => guardarRevista({ [clave]: campo.value.trim() }) });
    return h('div.propio', h('span.versal', nombre), campo, lista,
      valor ? h('button.enlace', { type: 'button', onclick: () => guardarRevista({ [clave]: '' }) }, 'Quitar') : null);
  }

  RV.vistas.ajustes = (ruta) => {
    if (ruta && ruta.sec && secciones().some(([id]) => id === ruta.sec)) seccion = ruta.sec;
    if (!secciones().some(([id]) => id === seccion)) seccion = 'revista';
    const root = h('div.hoja.ajustes');
    root.append(h('header.cabeza',
      h('div.ante', 'Atrio, a tu gusto · LB y RB cambian de sección'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Ajustes')),
      h('div.pestanas', { role: 'tablist' }, secciones().map(([id, n]) => h('button.pestana', {
        type: 'button', role: 'tab', 'data-sec': id, 'aria-selected': String(seccion === id), 'data-autofocus': seccion === id ? true : null,
        onclick: () => { seccion = id; RV.ir({ v: 'ajustes', sec: id }, { reemplazar: true }); },
      }, n)))));
    const P = RV.ajustesPc || {};
    let partes;
    if (seccion === 'revista') partes = laRevista();
    else if (seccion === 'cuenta') {
      partes = [
        bloque('Cuenta', 'Tu cuenta de Atrio: las mismas fichas que en el móvil. Lo que cambies aquí llega al móvil en unos segundos, y al revés.', cuenta()),
        bloque('SteamGridDB', 'Para buscar videojuegos al añadirlos y para la portada, el fondo y el logo de cada juego. Si no la pones aquí, se usa la de tu cuenta.', steamgriddb()),
        bloque('Tu nube (kDrive)', 'Las capturas de este PC van a la carpeta «Atrio» de tu kDrive, y las fotos de portada que elegiste en el móvil se bajan aquí. En el Manager de Infomaniak: Tokens de API → Crear un token, con la aplicación kDrive.', nube()),
      ];
    } else partes = P[seccion] ? P[seccion]() : [RV.vacio('Esta sección no está disponible.')];
    root.append(h('div.ajustes-cuerpo', partes));
    return root;
  };
})(window.RV);
