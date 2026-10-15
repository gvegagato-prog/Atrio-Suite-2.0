'use strict';
/* global window */
// Ajustes de la revista: tema y acento, la cuenta, la clave de SteamGridDB y el paso al lanzador clásico.
// Lo demás (bibliotecas, mando, móvil, streaming…) sigue en los Ajustes del lanzador clásico.
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

  RV.vistas.ajustes = () => {
    const rev = { tema: 'auto', acento: 'rojo', ...(D.config().revista || {}) };
    const root = h('div.hoja.ajustes');
    root.append(h('header.cabeza',
      h('div.ante', 'La revista, a tu gusto'),
      h('div.cabeza-fila', h('h1.titular.ajusta', { 'data-min': '36' }, 'Ajustes'))));

    root.append(bloque('Tema', 'Papel claro, papel oscuro o lo que diga el sistema.',
      h('div.opciones', RV.TEMAS.map(([id, n]) => h('button.opcion', {
        type: 'button', 'aria-pressed': String(rev.tema === id), onclick: () => guardarRevista({ tema: id }),
      }, n)))));

    root.append(bloque('Acento', 'El color de imprenta: antetítulos, capitulares, enlaces y lo que está elegido.',
      h('div.muestras', RV.ACENTOS.map(([id, n, claro, oscuro]) => h('button.muestra', {
        type: 'button', 'aria-pressed': String(rev.acento === id), onclick: () => guardarRevista({ acento: id }), 'aria-label': `Acento ${n}`,
      }, h('span.color', { style: { background: RV.oscuro() ? oscuro : claro } }), h('span.nombre', n))))));

    root.append(bloque('Cuenta', 'Tu cuenta de Atrio: las mismas fichas que en el móvil. Atrio es un dispositivo más: lo que cambies aquí llega al móvil en unos segundos, y al revés.', cuenta()));

    root.append(bloque('SteamGridDB', 'Para buscar videojuegos al añadirlos (el mismo buscador que el móvil) y para la portada, el fondo y el logo de cada ficha.', steamgriddb()));

    root.append(bloque('Lanzador clásico', 'La interfaz de pantalla grande y mando: bibliotecas, emuladores, juegos propios, el mando, el móvil y el streaming se ajustan allí. Atrio recordará cuál usaste la última vez.',
      h('button.boton-tinta', { type: 'button', onclick: () => RV.lanzador() }, 'Abrir el lanzador clásico (pantalla grande y mando) →')));
    return root;
  };
})(window.RV);
