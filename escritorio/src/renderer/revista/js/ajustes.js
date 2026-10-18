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
    const actual = { tema: 'auto', acento: 'rojo', ...(D.config().revista || {}) };
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
  let seccion = 'revista';
  RV.seccionAjustes = (paso) => {
    let i = SECCIONES.findIndex(([id]) => id === seccion);
    i = (i + paso + SECCIONES.length) % SECCIONES.length;
    seccion = SECCIONES[i][0];
    RV.ir({ v: 'ajustes', sec: seccion }, { reemplazar: true });
    requestAnimationFrame(() => { const b = document.querySelector(`.ajustes .pestana[data-sec="${seccion}"]`); if (b) b.focus({ preventScroll: true }); });
  };

  function laRevista() {
    const rev = { tema: 'auto', acento: 'rojo', ...(D.config().revista || {}) };
    return [
      bloque('Tema', 'Papel claro, papel oscuro o lo que diga el sistema.',
        h('div.opciones', RV.TEMAS.map(([id, n]) => h('button.opcion', {
          type: 'button', 'aria-pressed': String(rev.tema === id), onclick: () => guardarRevista({ tema: id }),
        }, n)))),
      bloque('Acento', 'El color de imprenta: antetítulos, capitulares, enlaces y lo que está elegido.',
        h('div.muestras', RV.ACENTOS.map(([id, n, claro, oscuro]) => h('button.muestra', {
          type: 'button', 'aria-pressed': String(rev.acento === id), onclick: () => guardarRevista({ acento: id }), 'aria-label': `Acento ${n}`,
        }, h('span.color', { style: { background: RV.oscuro() ? oscuro : claro } }), h('span.nombre', n))))),
    ];
  }

  RV.vistas.ajustes = (ruta) => {
    if (ruta && ruta.sec && SECCIONES.some(([id]) => id === ruta.sec)) seccion = ruta.sec;
    const root = h('div.hoja.ajustes');
    root.append(h('header.cabeza',
      h('div.ante', 'Atrio, a tu gusto · LB y RB cambian de sección'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Ajustes')),
      h('div.pestanas', { role: 'tablist' }, SECCIONES.map(([id, n]) => h('button.pestana', {
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
