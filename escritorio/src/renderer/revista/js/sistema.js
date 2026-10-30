'use strict';
/* global window, navigator, setInterval, clearInterval, document */
// Ajustes del PC con la estética de la revista (lo que antes estaba en el lanzador clásico): bibliotecas,
// juegos añadidos, emuladores, juegos ocultos, mando, comportamiento, móvil y streaming. Todo se maneja
// con mando, teclado o ratón.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const api = RV.api;
  const S = {};
  RV.ajustesPc = S;
  const WIN = api.platform === 'win32';
  const MAC = api.platform === 'darwin';
  const cfg = () => D.config();

  // ---------------------------------------------------------------- Piezas
  const bloque = (titulo, nota, ...contenido) => h('section.ajuste',
    h('div', h('div.ante', 'Ajustes'), h('h2', titulo), nota ? h('p.nota', nota) : null),
    h('div', contenido));
  const opciones = (lista, actual, fn) => h('div.opciones', lista.map(([v, t]) => h('button.opcion', {
    type: 'button', 'aria-pressed': String(String(actual) === String(v)), onclick: () => fn(v),
  }, t)));
  // Sí / No con su explicación
  const interruptor = (titulo, on, fn, sub) => h('div.interruptor',
    h('div', h('span.t', titulo), sub ? h('small', sub) : null),
    opciones([[true, 'Sí'], [false, 'No']], !!on, (v) => fn(v === true || v === 'true')));
  const sub = (t) => h('h3.sub', t);
  const nota = (t) => h('p.nota', t);
  async function guardar(patch, msg) {
    try {
      const st = await api.patchConfig(patch);
      if (st) D.setPc(st);
      if (st && st.config) RV.mando.config(st.config.behavior);
      if (msg) RV.aviso(msg);
    } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    RV.refrescar();
  }
  const comportamiento = (p, msg) => guardar({ behavior: p }, msg);
  const fila = (titulo, detalle, ...lado) => h('div.fila-ajuste', h('div', h('span.t', titulo), detalle ? h('small', detalle) : null), h('div.lado', lado));
  // Un campo con la ruta a un archivo o carpeta y su botón «Elegir»
  function ruta(valor, o = {}) {
    const input = RV.entrada(valor || '', { placeholder: o.placeholder || '', class: 'entrada mono' });
    const el = h('div.ruta', input, h('button.enlace', {
      type: 'button',
      onclick: async () => {
        try {
          const p = await api.pickFile({ title: o.title || 'Elegir', directory: !!o.directory });
          if (p) { input.value = p; if (o.alElegir) o.alElegir(p); }
        } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
      },
    }, o.directory ? 'Elegir carpeta' : 'Elegir archivo'));
    return { el, input };
  }
  const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
  const shq = (p) => (/\s/.test(p) ? `"${p}"` : p);

  // ---------------------------------------------------------------- Bibliotecas
  S.bibliotecas = () => {
    const c = cfg();
    const info = D.pc().scanInfo || {};
    const juegos = D.pc().games || [];
    const cuenta = (src) => juegos.filter((g) => g.source === src).length;
    const lib = (id, nombre, estado) => fila(nombre, `${estado} · ${c.sources && c.sources[id] ? RV.plural(cuenta(id), 'juego', 'juegos') : 'desactivada'}`,
      opciones([[true, 'Activada'], [false, 'No']], !!(c.sources || {})[id], (v) => guardar({ sources: { [id]: v === true || v === 'true' } }, 'Bibliotecas leídas de nuevo')));
    const steam = info.steam && info.steam.roots && info.steam.roots.length ? `Leyendo ${info.steam.roots.join(', ')}` : 'No he encontrado una instalación de Steam';
    const lutris = info.lutris && info.lutris.available ? 'Leído con el comando lutris' : 'No he encontrado Lutris';
    const extra = ruta('', { title: 'Carpeta de Steam', directory: true, placeholder: 'Carpeta de Steam adicional' });
    return [
      bloque('Bibliotecas', 'Atrio solo lee tus bibliotecas: los juegos se siguen instalando y actualizando desde cada tienda.',
        lib('steam', 'Steam', steam),
        lib('heroic', 'Heroic (Epic, GOG y Amazon)', 'Juegos instalados con Heroic'),
        WIN ? null : lib('lutris', 'Lutris', lutris),
        lib('emulators', 'Emuladores', (c.emulators || []).length ? RV.plural(c.emulators.length, 'sistema', 'sistemas') : 'Ningún sistema configurado'),
        lib('custom', 'Juegos añadidos por ti', (c.customGames || []).length ? RV.plural(c.customGames.length, 'juego', 'juegos') : 'Ninguno todavía'),
        h('div.fila-botones', h('button.boton-tinta', {
          type: 'button',
          onclick: async (e) => {
            const b = e.currentTarget; b.disabled = true; b.textContent = 'Buscando…';
            try { D.setPc(await api.rescan()); RV.aviso('Bibliotecas leídas de nuevo'); } catch (err) { RV.aviso(RV.limpiaError(err), 'error'); }
            RV.refrescar();
          },
        }, 'Volver a buscar juegos'))),
      bloque('Otra instalación de Steam', WIN ? 'Normalmente no hace falta: Atrio encuentra Steam y todas sus carpetas de biblioteca.' : 'Normalmente no hace falta: Atrio encuentra Steam nativo y flatpak, y todas sus carpetas de biblioteca.',
        h('div.formulario', extra.el),
        h('div.fila-botones', h('button.enlace.acento', { type: 'button', onclick: () => { const v = extra.input.value.trim(); if (v) guardar({ extraSteamPaths: [...(c.extraSteamPaths || []), v] }, 'Carpeta añadida'); } }, 'Añadir carpeta')),
        (c.extraSteamPaths || []).map((p) => fila(p, 'Carpeta de Steam adicional', h('button.enlace', { type: 'button', onclick: () => guardar({ extraSteamPaths: c.extraSteamPaths.filter((x) => x !== p) }, 'Carpeta quitada') }, 'Quitar')))),
      bloque('Arte', 'Con la clave de SteamGridDB (en Cuenta) Atrio descarga portadas, fondos y logos de todos tus juegos, también de las ROMs y de los tuyos.',
        h('div.fila-botones', h('button.enlace.acento', { type: 'button', onclick: () => { api.refetchArt(null); RV.aviso('Buscando el arte que falta…'); } }, 'Buscar el arte que falta'))),
    ];
  };

  // ---------------------------------------------------------------- Juegos añadidos
  const RUNNERS = WIN ? [['native', 'Ejecutable (.exe)'], ['command', 'Comando propio']]
    : MAC ? [['native', 'Aplicación de Mac'], ['command', 'Comando propio']]
    : [['native', 'Nativo de Linux'], ['umu', 'Windows con Proton'], ['wine', 'Windows con Wine'], ['command', 'Comando propio']];
  S.propios = () => {
    const c = cfg();
    const runner = Object.fromEntries(RUNNERS);
    const lista = (c.customGames || []);
    return [bloque('Juegos añadidos', WIN ? 'Cualquier juego que no venga de una tienda: su .exe o un comando propio.' : MAC ? 'Cualquier juego que no venga de una tienda: su aplicación (Juego.app) o un comando propio.' : 'Cualquier juego que no venga de una tienda: ejecutables de Linux, AppImage, scripts o juegos de Windows con Proton.',
      lista.length ? lista.map((g) => fila(g.name, `${runner[g.runner] || ''}: ${g.runner === 'command' ? g.command : g.exec}`,
        h('button.enlace', { type: 'button', onclick: () => editarPropio(g.id) }, 'Editar'),
        h('button.enlace.apagado', { type: 'button', onclick: () => RV.confirmar(`¿Quitar ${g.name}?`, 'Se quita de Atrio. Los archivos del juego no se tocan.', 'Quitar', () => guardar({ customGames: lista.filter((x) => x.id !== g.id) }, `${g.name} quitado`)) }, 'Quitar')))
        : nota('Todavía no has añadido ningún juego.'),
      h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: () => editarPropio(null) }, '+ Añadir un juego')))];
  };
  function editarPropio(id) {
    const c = cfg();
    const ya = id ? (c.customGames || []).find((x) => x.id === id) : null;
    const dato = ya ? { ...ya } : { id: uid(), name: '', exec: '', runner: 'native', args: '', prefix: '', protonPath: '', command: '' };
    const nombre = RV.entrada(dato.name, { placeholder: 'Por ejemplo: Hollow Knight', 'data-autofocus': !ya ? true : null });
    const exe = ruta(dato.exec, {
      title: MAC ? 'La aplicación del juego' : 'El ejecutable del juego', placeholder: MAC ? 'Ruta a la aplicación (Juego.app)' : 'Ruta al ejecutable, script o .exe',
      alElegir: (p) => {
        if (!nombre.value.trim()) nombre.value = p.split(/[\\/]/).pop().replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ');
        if (!WIN && !MAC && /\.(exe|msi|bat)$/i.test(p) && dato.runner === 'native') { dato.runner = 'umu'; pintar(); }
      },
    });
    const args = RV.entrada(dato.args, { placeholder: 'Opcional, por ejemplo: -windowed' });
    const prefijo = ruta(dato.prefix, { title: 'Carpeta del prefijo de Wine', directory: true, placeholder: 'Vacío: ~/Games/atrio/<nombre del juego>' });
    const proton = RV.entrada(dato.protonPath, { placeholder: 'Carpeta de Proton, o vacío para la de umu', class: 'entrada mono' });
    const protones = h('div.opciones', h('span.versal', 'Buscando versiones de Proton…'));
    api.protonList().then((l) => {
      const elegir = (p) => { proton.value = p; for (const b of protones.children) b.setAttribute('aria-pressed', String(b.dataset.p === p)); };
      protones.replaceChildren(...[['', 'UMU-Proton'], ...l.map((p) => [p.path, p.name])].map(([p, n]) => h('button.opcion', { type: 'button', 'data-p': p, 'aria-pressed': String((proton.value || '') === p), onclick: () => elegir(p), title: p || 'La que usa umu-launcher por defecto' }, n)));
      if (!ya && !dato.protonPath && l.length) elegir(l[0].path);
    }).catch(() => protones.replaceChildren());
    const comando = RV.entrada(dato.command, { placeholder: 'Cualquier comando de shell', class: 'entrada mono' });
    const msg = h('p.mensaje');
    const dyn = h('div.formulario');
    const runnerSel = h('div');
    function pintar() {
      if ((WIN || MAC) && dato.runner !== 'command') dato.runner = 'native';
      const r = dato.runner;
      runnerSel.replaceChildren(opciones(RUNNERS, r, (v) => { dato.runner = v; pintar(); }));
      dyn.replaceChildren(...(r === 'command'
        ? [RV.campo('Comando', comando, WIN ? 'Se ejecuta con cmd.exe.' : 'Se ejecuta con /bin/sh. Útil para scripts o lanzadores de otros programas.')]
        : [RV.campo(MAC ? 'Aplicación' : 'Ejecutable', exe.el, r === 'native' ? (WIN ? 'El archivo .exe del juego.' : MAC ? 'La aplicación del juego, normalmente en la carpeta Aplicaciones. Atrio cuenta las horas mientras está abierta.' : 'Juegos nativos de Linux, AppImage o scripts .sh.') : 'Archivo .exe del juego de Windows.'),
          RV.campo('Argumentos', args),
          r !== 'native' ? RV.campo('Prefijo de Wine', prefijo.el, 'Cada juego tiene su propio prefijo, donde se guardan su configuración y partidas.') : null,
          r === 'umu' ? RV.campo('Versión de Proton', h('div', protones, proton), 'Hace falta umu-launcher (sudo pacman -S umu-launcher).') : null].filter(Boolean)));
    }
    pintar();
    const guardarJuego = async (x) => {
      Object.assign(dato, { name: nombre.value.trim(), exec: exe.input.value.trim(), args: args.value.trim(), prefix: prefijo.input.value.trim(), protonPath: proton.value.trim(), command: comando.value.trim() });
      if (!dato.name) { msg.textContent = 'Ponle un nombre al juego.'; nombre.focus(); return; }
      if (dato.runner === 'command' ? !dato.command : !dato.exec) { msg.textContent = dato.runner === 'command' ? 'Escribe el comando.' : 'Elige el ejecutable.'; return; }
      const l = cfg().customGames || [];
      x.cerrar();
      await guardar({ customGames: ya ? l.map((g) => (g.id === dato.id ? dato : g)) : [...l, dato] }, ya ? 'Juego actualizado' : `${dato.name} añadido`);
    };
    RV.dialogo({
      ante: 'Juegos añadidos', titulo: ya ? 'Editar el juego' : 'Añadir un juego', ancho: true,
      cuerpo: [h('div.formulario', RV.campo('Nombre', nombre), RV.campo('Cómo se ejecuta', runnerSel)), dyn, msg],
      acciones: ['hueco', { texto: 'Cancelar', fn: (x) => x.cerrar() }, { texto: ya ? 'Guardar' : 'Añadir', principal: true, fn: guardarJuego }],
    });
  }

  // ---------------------------------------------------------------- Emuladores
  S.emuladores = () => {
    const c = cfg();
    const juegos = D.pc().games || [];
    const l = c.emulators || [];
    return [bloque('Emuladores', 'Un sistema por cada consola: la carpeta donde están sus juegos y el comando del emulador que los abre.',
      l.length ? l.map((e) => fila(e.name, `${RV.plural(juegos.filter((g) => String(g.key).startsWith(`rom:${e.id}:`)).length, 'juego', 'juegos')} en ${e.romDir}`,
        h('button.enlace', { type: 'button', onclick: () => editarEmulador(e.id) }, 'Editar'))) : nota('Todavía no hay sistemas.'),
      h('div.fila-botones', h('button.boton-tinta', { type: 'button', onclick: () => editarEmulador(null) }, '+ Añadir un sistema')))];
  };
  function editarEmulador(id) {
    const c = cfg();
    const ya = id ? (c.emulators || []).find((e) => e.id === id) : null;
    const dato = ya ? { ...ya } : { id: uid(), name: '', short: '', romDir: '', extensions: '', command: '', enabled: true };
    const nombre = RV.entrada(dato.name, { placeholder: 'Por ejemplo: Game Boy Advance', 'data-autofocus': true });
    const corto = RV.entrada(dato.short, { placeholder: 'Por ejemplo: GBA' });
    const dir = ruta(dato.romDir, { title: 'Carpeta con las ROMs', directory: true, placeholder: 'Carpeta con los juegos' });
    const ext = RV.entrada(dato.extensions, { placeholder: 'Por ejemplo: gba zip' });
    const cmd = RV.entrada(dato.command, { placeholder: 'ruta/al/emulador {rom}', class: 'entrada mono' });
    const elegir = h('button.enlace', { type: 'button', onclick: async () => { const p = await api.pickFile({ title: 'El ejecutable del emulador' }); if (p) cmd.value = `${shq(p)} {rom}`; } }, 'Elegir el emulador');
    const msg = h('p.mensaje');
    const acciones = ['hueco', { texto: 'Cancelar', fn: (x) => x.cerrar() }, {
      texto: ya ? 'Guardar' : 'Añadir', principal: true,
      fn: async (x) => {
        Object.assign(dato, { name: nombre.value.trim(), short: corto.value.trim(), romDir: dir.input.value.trim(), extensions: ext.value.trim(), command: cmd.value.trim() });
        if (!dato.name || !dato.romDir || !dato.command) { msg.textContent = 'Hacen falta el nombre del sistema, la carpeta y el comando.'; return; }
        const l = cfg().emulators || [];
        x.cerrar();
        await guardar({ emulators: ya ? l.map((e) => (e.id === dato.id ? dato : e)) : [...l, dato] }, `${dato.name} guardado`);
      },
    }];
    if (ya) acciones.unshift({ texto: 'Quitar el sistema', peligro: true, fn: (x) => RV.confirmar(`¿Quitar ${dato.name}?`, 'Se quita el sistema de Atrio. Las ROMs no se tocan.', 'Quitar', () => { x.cerrar(); guardar({ emulators: (cfg().emulators || []).filter((e) => e.id !== dato.id) }, 'Sistema quitado'); }) });
    RV.dialogo({
      ante: 'Emuladores', titulo: ya ? 'Editar el sistema' : 'Añadir un sistema', ancho: true,
      cuerpo: [h('div.formulario',
        RV.campo('Nombre del sistema', nombre), RV.campo('Nombre corto', corto, 'Se muestra junto a cada juego.'),
        RV.campo('Carpeta de ROMs', dir.el, 'Se buscan juegos en esta carpeta y en sus subcarpetas.'),
        RV.campo('Extensiones', ext, 'Separadas por espacios. Vacío: todos los archivos de la carpeta.'),
        RV.campo('Comando para abrir un juego', h('div.ruta', cmd, elegir), WIN ? '{rom} se sustituye por la ruta del juego. Pon la ruta del emulador entre comillas.' : '{rom} se sustituye por la ruta del juego. Para flatpak: flatpak run <id de la app> {rom}.')), msg],
      acciones,
    });
  }

  // ---------------------------------------------------------------- Juegos ocultos
  S.ocultos = () => {
    if (RV.soloRevista) return [listaNegra()];
    const l = (D.pc().games || []).filter((g) => g.hidden).sort((a, b) => String(a.sortName || a.name).localeCompare(String(b.sortName || b.name), 'es'));
    return [bloque('Juegos ocultos', 'No salen en la biblioteca ni en la búsqueda. Desde aquí los abres o los devuelves a la biblioteca.',
      l.length ? l.map((g) => fila(g.name, g.sourceLabel || g.source,
        h('button.enlace.acento', { type: 'button', onclick: () => RV.jugar(g) }, 'Jugar →'),
        h('button.enlace', {
          type: 'button',
          onclick: async () => { try { const ng = await api.override(g.key, { hidden: false }); if (ng) D.setJuegoPc(ng); RV.aviso(`${g.name} vuelve a la biblioteca`); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); } RV.refrescar(); },
        }, 'Mostrar'))) : nota('No tienes juegos ocultos. Para ocultar uno: en el modo Juegos, Y (opciones) → Ocultar.')),
      listaNegra()];
  };

  // La lista negra (de tu cuenta, como en el móvil): esos juegos no salen en ningún sitio y Atrio no los sigue
  function listaNegra() {
    const l = (Array.isArray(D.datos().negra) ? D.datos().negra : []).filter((o) => o && o.n).sort((a, b) => RV.normName(a.n).localeCompare(RV.normName(b.n)));
    const nuevo = RV.entrada('', { placeholder: 'Nombre del juego (p. ej. Wallpaper Engine)' });
    return bloque('Lista negra', 'No salen en ningún sitio (biblioteca, portada, diario, calendario) y Atrio no los sigue, ni sus horas. Vale para todos tus dispositivos.',
      l.length ? l.map((o) => fila(o.n, 'En la lista negra', h('button.enlace', {
        type: 'button',
        onclick: async () => { if (await RV.editar({ op: 'negraDel', id: Number(o.id) }, `${o.n}: ya puede volver a salir`)) { try { await api.rescan(); } catch { /* */ } } },
      }, 'Quitar'))) : nota('Aún no hay ninguno.'),
      h('div', { style: { display: 'flex', gap: '12px', marginTop: '14px', alignItems: 'center' } }, nuevo,
        h('button.boton-tinta', { type: 'button', onclick: () => { const n = nuevo.value.trim(); if (n) RV.listaNegra(n); } }, 'Añadir')));
  }

  // ---------------------------------------------------------------- Mando
  const ETIQUETAS = { up: 'Arriba', down: 'Abajo', left: 'Izquierda', right: 'Derecha', a: 'A', b: 'B', x: 'X', y: 'Y', prev: 'LB', next: 'RB', start: 'Menú', select: 'Vista' };
  const nombreMando = (id) => String(id).replace(/\s*\((STANDARD GAMEPAD\s*)?Vendor: ([0-9a-f]{4}) Product: ([0-9a-f]{4})\)/i, '').trim() || 'Mando';
  let reloj = null;
  S.mando = () => {
    const b = cfg().behavior || {};
    const mapas = b.padMaps || {};
    const lista = h('div');
    const vivo = h('div.pad-vivo', RV.mando.ACCIONES.map((a) => h('span.pad-tecla', { 'data-act': a }, ETIQUETAS[a])));
    const ultima = h('p.versal', 'Pulsa algo en el mando.');
    const steam = h('p.nota', 'Comprobando si Atrio está en Steam…');
    api.autostart().then((st) => { steam.textContent = st.steam ? `Atrio está añadido a Steam${st.viaSteam ? ' y se abre a través de Steam al iniciar sesión' : ''}.` : 'Atrio todavía no está añadido a Steam.'; }).catch(() => { steam.textContent = ''; });
    const pintarMandos = () => {
      const pads = RV.mando.mandos();
      const firma = pads.map((p) => p.id + p.mapping + (mapas[p.id] ? 1 : 0)).join('|');
      if (lista.dataset.firma === firma) return;
      lista.dataset.firma = firma;
      lista.replaceChildren(...(pads.length ? pads.map((p) => fila(nombreMando(p.id),
        mapas[p.id] ? 'Botones configurados a mano' : p.mapping === 'standard' ? 'Reconocido como mando estándar' : 'Sin mapeo estándar: conviene configurar los botones',
        mapas[p.id] ? h('button.enlace.apagado', { type: 'button', onclick: () => guardarMapa(p.id, null) }, 'Quitar configuración') : null,
        h('button.enlace', { type: 'button', onclick: () => calibrar(p.id) }, 'Configurar botones'))) : [nota('Ahora mismo no llega ningún mando como tal. Pulsa cualquier botón para que aparezca.')]));
    };
    clearInterval(reloj);
    reloj = setInterval(() => {
      if (!document.body.contains(lista)) { clearInterval(reloj); return; }
      pintarMandos();
      const ahora = new Set();
      for (const p of RV.mando.mandos()) RV.mando.leer(p, ahora);
      for (const k of vivo.children) k.classList.toggle('on', ahora.has(k.dataset.act));
      const u = RV.mando.ultima();
      if (u) {
        const s = Math.round((Date.now() - u.t) / 1000);
        const tipo = { tecla: 'una tecla', 'ratón': 'el ratón', mando: 'el mando' }[u.tipo] || u.tipo;
        ultima.textContent = `Lo último que ha llegado: ${tipo} (${u.detalle}), ${s < 2 ? 'ahora mismo' : `hace ${s} s`}.`;
      }
    }, 90);
    pintarMandos();
    return [
      bloque('Mando', 'Atrio se maneja con mando, con teclado y con ratón a la vez. Pulsa botones para ver qué entiende.',
        sub('Mandos detectados'), lista, sub('Prueba'), vivo, ultima,
        sub('Opciones'),
        interruptor('Intercambiar A y B', b.swapAB, (v) => comportamiento({ swapAB: v }), 'Para mandos con la distribución de Nintendo')),
      bloque('Steam Controller', 'Con Steam abierto, Steam se queda el mando: fuera de los juegos lo usa como ratón (modo escritorio). Para que llegue a Atrio como mando:',
        h('ol.pasos',
          h('li', h('b', 'Al momento: '), 'mantén pulsado un instante el botón de menú (☰): Steam cambia entre modo escritorio y modo mando.'),
          h('li', h('b', 'Para siempre: '), 'añade Atrio a Steam (Añadir un juego que no es de Steam → Atrio) y, en sus propiedades, Mando → activa Steam Input. Al iniciar sesión, Atrio se abrirá a través de Steam con el mando funcionando.')),
        steam,
        nota('Sin Steam Input también va: en la configuración de escritorio del mando (Steam → Ajustes → Mando) asigna A a Intro, B a Escape, la cruceta a las flechas, Y a Espacio, X a /, LB a RePág, RB a AvPág, Vista a C y Menú a S. El trackpad derecho sigue siendo el ratón.')),
    ];
  };
  async function guardarMapa(id, mapa) {
    const maps = { ...((cfg().behavior || {}).padMaps || {}) };
    maps[id] = mapa ? JSON.stringify(mapa) : '';
    await comportamiento({ padMaps: maps }, mapa ? 'Botones guardados' : 'Configuración quitada');
  }
  const DESCRIBE = {
    up: 'Arriba en la cruceta', down: 'Abajo en la cruceta', left: 'Izquierda en la cruceta', right: 'Derecha en la cruceta',
    a: 'El botón para aceptar y jugar', b: 'El botón para volver', x: 'El botón para buscar', y: 'El botón de opciones',
    prev: 'El gatillo superior izquierdo', next: 'El gatillo superior derecho', start: 'El botón de menú', select: 'El botón de vista (cambia de modo)',
  };
  // Asistente: pide cada botón y guarda lo que se pulse (botón o eje)
  function calibrar(id) {
    const pasos = RV.mando.ACCIONES;
    const mapa = {};
    let i = 0, base = null, soltar = false;
    const tecla = h('div.cal-tecla');
    const texto = h('p.texto');
    const progreso = h('p.versal');
    let d = null;
    const terminar = async () => { RV.mando.soltar(); if (d) d.cerrar(); await guardarMapa(id, mapa); };
    const pintar = () => {
      if (i >= pasos.length) { terminar(); return; }
      tecla.textContent = ETIQUETAS[pasos[i]];
      texto.textContent = DESCRIBE[pasos[i]];
      progreso.textContent = `${i + 1} de ${pasos.length} · mando: ${nombreMando(id)}`;
    };
    const sig = () => { i++; base = null; soltar = true; pintar(); };
    RV.mando.capturar((pads) => {
      const p = pads.find((x) => x.id === id);
      if (!p) return;
      const btns = p.buttons.map((x) => (x.pressed || x.value > 0.5));
      const ejes = [...p.axes];
      if (soltar) {
        if (btns.some(Boolean) || (base && ejes.some((v, k) => Math.abs(v - base.ejes[k]) > 0.35))) return;
        soltar = false;
      }
      if (!base) { base = { btns, ejes }; return; }
      const bi = btns.findIndex((v, k) => v && !base.btns[k]);
      if (bi >= 0) { mapa[pasos[i]] = { t: 'b', i: bi }; sig(); return; }
      const ai = ejes.findIndex((v, k) => Math.abs(v - base.ejes[k]) > 0.6);
      if (ai >= 0) { mapa[pasos[i]] = { t: 'a', i: ai, d: ejes[ai] > base.ejes[ai] ? 1 : -1 }; sig(); }
    });
    d = RV.dialogo({
      ante: 'Mando', titulo: 'Configurar botones', cuerpo: [progreso, tecla, texto, nota('Pulsa en el mando lo que se pide. Con el teclado o el ratón puedes saltar un paso o cancelar.')],
      acciones: [{ texto: 'Cancelar', fn: (x) => x.cerrar() }, 'hueco', { texto: 'Saltar este', fn: () => sig() }],
      alCerrar: () => RV.mando.soltar(),
    });
    pintar();
  }

  // ---------------------------------------------------------------- Comportamiento
  S.comportamiento = () => {
    const b = cfg().behavior || {};
    const arranque = h('div', nota('Comprobando…'));
    api.autostart().then((st) => {
      if (!st.available) { arranque.replaceChildren(nota(WIN ? 'Para abrir Atrio al iniciar sesión, instálalo con el instalador.' : MAC ? 'Para abrir Atrio al iniciar sesión, usa Atrio.app (en la carpeta Aplicaciones).' : 'Para abrir Atrio al iniciar sesión, usa la versión AppImage.')); return; }
      arranque.replaceChildren(
        st.aprobar ? h('div', nota('macOS pide que lo apruebes: en Ajustes del Sistema → General → Elementos de inicio, activa Atrio.'),
          h('button.enlace.acento', { type: 'button', onclick: () => api.abrirInicioSesion() }, 'Abrir Ajustes del Sistema')) : null,
        st.fueraDeAplicaciones ? nota('Atrio no está en la carpeta Aplicaciones: arrástralo allí (desde el DMG) para que se abra bien al iniciar sesión y se actualice.') : null,
        opciones([['visible', 'Abrir Atrio'], ['hidden', 'En segundo plano'], ['off', 'No abrir solo']], st.mode, async (v) => {
          try { await api.setAutostart(v); RV.aviso(v === 'off' ? 'Atrio ya no se abrirá al iniciar sesión' : 'Atrio se abrirá al iniciar sesión'); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
          RV.refrescar();
        }),
        nota(MAC ? 'Al iniciar sesión, Atrio se abre solo y cuenta lo que juegues. En segundo plano no se ve hasta que lo abras desde el Dock; cerrar la ventana solo la oculta.' : 'Al iniciar sesión, Atrio se abre solo y cuenta también lo que juegues desde Steam. En segundo plano no se ve hasta que lo abras; cerrar la ventana solo la oculta.'),
        st.installed && st.path ? nota(`Atrio está instalado en ${st.path}.`) : null);
    }).catch(() => arranque.replaceChildren());
    return [
      bloque('Al abrir un juego', 'Qué hace Atrio mientras juegas.',
        opciones([['minimize', 'Minimizar Atrio'], ['none', 'Dejarlo abierto'], ['quit', 'Cerrar Atrio']], b.onLaunch || 'minimize', (v) => comportamiento({ onLaunch: v }, 'Guardado')),
        interruptor('Volver a Atrio al cerrar el juego', b.returnOnExit !== false, (v) => comportamiento({ returnOnExit: v }, 'Guardado'), 'Atrio reaparece en cuanto el juego termina'),
        WIN ? null : interruptor('Esconder la ventana de Steam al abrir un juego', b.hideSteamLaunch !== false, (v) => comportamiento({ hideSteamLaunch: v }, 'Guardado'), 'Solo en KDE Plasma. Si Steam necesita algo (una actualización), vuelve a salir a los 90 segundos'),
        interruptor('Pasar a «Jugando» lo que juegas', b.ronronAuto !== false, (v) => comportamiento({ ronronAuto: v }, 'Guardado'), 'A los 2 minutos de juego, si no lo tenías en curso (lo valorado no se toca)')),
      bloque('Al iniciar sesión', 'Atrio siempre se abre en el modo Juegos, a pantalla completa.', arranque),
      bloque('Movimiento', 'Las transiciones de la revista. Sin ellas, todo cambia al instante.',
        interruptor('Animaciones', (D.config().revista || {}).quieto !== true, (v) => RV.guardarRevista({ quieto: !v }), 'Ligeras: el foco, las portadas y el cambio de modo')),
    ];
  };

  // ---------------------------------------------------------------- Móvil
  S.movil = () => {
    const c = cfg().suite || {};
    const set = (p) => guardar({ suite: p });
    const si = (t, k, s) => interruptor(t, c[k] !== false, (v) => set({ [k]: v }), s);
    const metodos = nota('Buscando herramientas de captura…');
    api.suiteMethods().then((m) => { metodos.textContent = `Herramientas de captura en este equipo, por orden: ${m.join(', ')}.`; }).catch(() => { metodos.textContent = ''; });
    const firmado = !!(D.cuenta().status || {}).signed;
    return [
      bloque('Móvil', 'Atrio en el PC y en el móvil comparten tu cuenta y se hablan en tiempo real: el móvil ve lo que juegas, pide capturas, lanza juegos y valora cada partida.',
        !firmado ? h('p.mensaje', 'Para conectar el móvil, entra con tu cuenta (en Cuenta).') : null,
        sub('Desde el móvil'),
        si('Hacer capturas', 'remoteShot', 'A la resolución real de la pantalla'),
        si('Lanzar juegos', 'remoteLaunch'),
        si('Suspender, reiniciar o apagar', 'remotePower', 'El móvil siempre pide confirmación')),
      bloque('Capturas', null,
        sub('Formato'), opciones([['png', 'PNG (calidad original)'], ['jpg', 'JPG (más ligeras)']], c.shotFormat || 'png', (v) => set({ shotFormat: v })),
        sub('Qué se captura'), opciones([['juego', 'La pantalla donde juegas'], ['todas', 'Todas las pantallas']], c.shotScope || 'juego', (v) => set({ shotScope: v })),
        si('Enviarlas al móvil', 'shotUpload', 'Si no, solo se guardan en este PC (Imágenes/Atrio)'),
        metodos),
    ];
  };

  // ---------------------------------------------------------------- Streaming
  let info = null;
  S.streaming = () => {
    const c = cfg().stream || {};
    const estado = h('div', nota('Mirando Steam…'));
    const pintarEstado = (i) => {
      if (!i) return;
      const punto = (on, t) => h('p.estado-punto', { 'data-on': String(!!on) }, t);
      estado.replaceChildren(
        punto(i.installed, i.installed ? 'Steam instalado' : 'Steam no está instalado en este PC'),
        punto(i.running, i.running ? `Steam abierto en «${i.name}»` : 'Steam cerrado (Atrio lo abre al jugar desde el móvil)'),
        i.ips && i.ips.length ? punto(true, `En casa: ${i.ips.join(', ')}`) : null);
    };
    if (info) pintarEstado(info);
    api.streamInfo().then((i) => { info = i; pintarEstado(i); }).catch(() => {});
    return [
      bloque('Streaming', 'Juega desde el móvil a los juegos de este PC con Steam Link, la app gratuita de Valve. En el móvil, cada juego tiene «Streaming»: Atrio abre Steam y el juego en este PC, y el móvil abre Steam Link.',
        estado,
        h('div.fila-botones',
          h('button.boton-tinta', { type: 'button', onclick: async () => { try { RV.aviso('Abriendo Steam…'); info = await api.streamStart(); pintarEstado(info); RV.aviso('Steam abierto'); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); } } }, 'Abrir Steam'),
          h('button.enlace.apagado', { type: 'button', onclick: () => { api.streamInfo().then((i) => { info = i; pintarEstado(i); }); } }, 'Volver a mirar')),
        interruptor('El móvil puede preparar el streaming', c.remote !== false, (v) => guardar({ stream: { remote: v } }), 'Abrir Steam y lanzar el juego')),
      bloque('En el móvil', null,
        fila('1 · Instala Steam Link', 'Está en Google Play: la app gratuita de Valve.'),
        fila('2 · Empareja (una vez)', 'Ábrelo y elige este PC: te enseña un PIN. Escríbelo en Steam de este PC, en la ventana que sale (o en Ajustes → Remote Play → Emparejar Steam Link).'),
        fila('3 · A jugar', '«Streaming» en cualquier juego. Fuera de casa, Steam Link también puede conectarse por internet.')),
    ];
  };
})(window.RV);
