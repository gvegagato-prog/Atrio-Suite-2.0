'use strict';
/* global window, requestAnimationFrame */
// Las imágenes de un juego del PC: portada, horizontal, fondo y logo. Se eligen entre las de SteamGridDB
// (con la clave de Ajustes → Cuenta) o desde un archivo propio. También: cambiar el nombre y corregir el
// juego en SteamGridDB si el arte que ha salido es de otro. Todo manejable con mando: LB y RB cambian de tipo.
(function (RV) {
  const h = RV.h;
  const D = RV.D;
  const api = RV.api;

  const TIPOS = [['cover', 'Portada'], ['wide', 'Horizontal'], ['hero', 'Fondo'], ['logo', 'Logo']];
  const juego = (key) => RV.juegoPc(key);
  const actualiza = (ng) => { if (ng) D.setJuegoPc(ng); RV.refrescar(); };
  let abierto = null; // { paso(n) } mientras el selector está abierto (LB / RB)

  function sinClave(titulo) {
    RV.dialogo({
      ante: 'SteamGridDB', titulo,
      cuerpo: h('p.texto', 'Para elegir imágenes de SteamGridDB hace falta una clave gratuita de su web. Se pone en Ajustes → Cuenta. Sin clave puedes poner una imagen desde un archivo.'),
      acciones: [
        { texto: 'Ir a Ajustes →', principal: true, fn: (d) => { d.cerrar(); RV.ir({ v: 'ajustes', sec: 'cuenta' }); } },
        'hueco',
        { texto: 'Cerrar', fn: (d) => d.cerrar() }],
    });
  }

  // ---------------------------------------------------------------- Imágenes
  RV.arteJuego = (key, tipoInicial) => {
    const g0 = juego(key);
    if (!g0) return;
    const conClave = !!D.config().sgdbKey;
    let tipo = tipoInicial || 'cover';
    let pagina = 0;
    let turno = 0;
    const pestanas = h('div.j-grupo.arte-tipos', { role: 'tablist' });
    const actual = h('div.arte-actual');
    const rejilla = h('div.arte-rejilla');
    const estado = h('p.texto.arte-estado');
    const mas = h('button.enlace', { type: 'button', hidden: true, onclick: () => { pagina++; cargar(true); } }, 'Cargar más');

    function pintarPestanas() {
      pestanas.replaceChildren(...TIPOS.map(([t, txt]) => h('button.j-opt', {
        type: 'button', role: 'tab', 'aria-pressed': String(t === tipo), 'data-autofocus': t === tipo ? true : null, onclick: () => cambiar(t),
      }, txt)));
    }
    function pintarActual() {
      const g = juego(key);
      const src = g && (g.art || {})[tipo];
      actual.replaceChildren(h('span.versal', 'Ahora'), src
        ? h(`figure.arte-mini.${tipo}`, h('img', { alt: '', src, decoding: 'sync' }))
        : h(`span.arte-mini.${tipo}.vacia`, 'Sin imagen'));
    }
    function cambiar(t, enfocar) {
      if (t === tipo) return;
      tipo = t; pagina = 0;
      pintarPestanas(); pintarActual();
      if (enfocar) requestAnimationFrame(() => { const b = pestanas.querySelector('[aria-pressed="true"]'); if (b) b.focus({ preventScroll: true }); });
      cargar(false);
    }
    async function elegir(item, el) {
      el.classList.add('cargando');
      try {
        actualiza(await api.setArt(key, tipo, item.url));
        for (const p of rejilla.querySelectorAll('.elegida')) p.classList.remove('elegida');
        el.classList.add('elegida');
        pintarActual();
        RV.aviso(`${TIPOS.find(([t]) => t === tipo)[1]} cambiada`);
      } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
      el.classList.remove('cargando');
    }
    async function cargar(seguir) {
      const mio = ++turno;
      if (!seguir) { rejilla.className = `arte-rejilla ${tipo}`; rejilla.replaceChildren(); }
      mas.hidden = true;
      if (!conClave) { estado.textContent = 'Sin clave de SteamGridDB: puedes poner una imagen desde un archivo (o añadir la clave en Ajustes → Cuenta).'; return; }
      estado.textContent = 'Buscando en SteamGridDB…';
      try {
        const res = await api.sgdbAssets(key, tipo, null, pagina);
        if (mio !== turno) return;
        const items = (tipo === 'hero' || tipo === 'wide') ? [...res.items].sort((a, b) => (b.width || 0) - (a.width || 0)) : res.items;
        if (!items.length && !seguir) {
          estado.textContent = res.sgdbId
            ? 'SteamGridDB no tiene imágenes de este tipo para este juego. Prueba otro tipo o pon una desde un archivo.'
            : 'No he encontrado este juego en SteamGridDB. Usa «Juego equivocado» para buscarlo por otro nombre.';
          return;
        }
        estado.textContent = '';
        const primero = rejilla.children.length;
        for (const it of items) {
          const b = h('button.arte-opcion', { type: 'button', title: it.author ? `De ${it.author}` : '', onclick: () => elegir(it, b) },
            h('img', { alt: '', loading: 'lazy', decoding: 'async', src: it.thumb }),
            h('span.versal', [it.width && it.height ? `${it.width}×${it.height}` : '', it.author || ''].filter(Boolean).join(' · ')));
          rejilla.append(b);
        }
        mas.hidden = res.items.length < 50;
        if (seguir && rejilla.children[primero]) rejilla.children[primero].focus({ preventScroll: true });
      } catch (e) {
        if (mio !== turno) return;
        estado.textContent = RV.limpiaError(e);
      }
    }
    const desdeArchivo = async () => {
      const p = await api.pickFile({ title: 'Elegir una imagen', filters: [{ name: 'Imágenes', extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif'] }] });
      if (!p) return;
      try { actualiza(await api.setArt(key, tipo, p)); pintarActual(); RV.aviso('Imagen puesta'); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    };
    const quitar = async () => {
      try { actualiza(await api.setArt(key, tipo, null)); pintarActual(); RV.aviso('Imagen quitada'); } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    };
    pintarPestanas(); pintarActual();
    RV.dialogo({
      ante: 'Imágenes · LB y RB cambian de tipo', titulo: g0.name, ancho: true,
      cuerpo: [h('div.arte-cabeza', pestanas, actual), estado, h('div.arte-scroll.desplaza', rejilla, h('div.fila-botones', mas))],
      acciones: [
        { texto: 'Desde un archivo', fn: desdeArchivo },
        { texto: 'Quitar esta', fn: quitar },
        { texto: 'Juego equivocado', fn: (d) => { d.cerrar(); RV.juegoEquivocado(key); } },
        'hueco',
        { texto: 'Hecho', principal: true, fn: (d) => d.cerrar() }],
      alCerrar: () => { abierto = null; },
    });
    abierto = { paso: (n) => { const i = TIPOS.findIndex(([t]) => t === tipo); cambiar(TIPOS[(i + n + TIPOS.length) % TIPOS.length][0], true); } };
    cargar(false);
  };
  // LB / RB con el selector abierto (lo llama app.js)
  RV.arteNav = (paso) => { if (!abierto) return false; abierto.paso(paso); return true; };

  // ---------------------------------------------------------------- Juego equivocado (SteamGridDB)
  RV.juegoEquivocado = (key) => {
    const g = juego(key);
    if (!g) return;
    if (!D.config().sgdbKey) { sinClave('Corregir el juego'); return; }
    const q = h('input', { type: 'search', value: g.name, 'data-autofocus': true, spellcheck: 'false', 'aria-label': 'Nombre del juego' });
    const ul = h('ul.resultados');
    const buscar = async () => {
      ul.replaceChildren(h('li', RV.vacio('Buscando…')));
      try {
        const l = await api.sgdbSearch(q.value.trim());
        ul.replaceChildren(...l.map((r) => h('li', h('button.resultado', {
          type: 'button', style: { width: '100%', textAlign: 'left' },
          onclick: async () => {
            try {
              await api.override(key, { sgdbId: r.id });
              await api.refetchArt(key, true);
              d.cerrar();
              RV.aviso(`Ahora es «${r.name}». Descargando sus imágenes…`);
            } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
          },
        }, h('span.tit', r.name), h('span.versal', [r.year, r.verified ? 'verificado' : ''].filter(Boolean).join(' · '))))));
        if (!l.length) ul.append(h('li', RV.vacio('Nada con ese nombre. Prueba con menos palabras o el nombre en inglés.')));
      } catch (e) { ul.replaceChildren(h('li', RV.vacio(RV.limpiaError(e)))); }
    };
    q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } });
    const d = RV.dialogo({
      ante: 'Corregir el juego', titulo: g.name, ancho: true,
      cuerpo: [h('p.texto', 'Si las imágenes son de otro juego, búscalo aquí y elige el bueno: se descargan otra vez a partir de él.'),
        h('label.buscador', { style: { maxWidth: 'none' } }, h('span.lupa', 'Buscar'), q), ul],
      acciones: [{ texto: 'Buscar', principal: true, fn: buscar }, 'hueco', { texto: 'Cerrar', fn: (x) => x.cerrar() }],
    });
    buscar();
  };

  // ---------------------------------------------------------------- Cambiar el nombre
  RV.renombrarJuego = (key) => {
    const g = juego(key);
    if (!g) return;
    const inp = RV.entrada(g.name, { placeholder: g.originalName || 'Nombre', 'data-autofocus': true });
    const guardar = async (nombre, d) => {
      try {
        const ng = await api.override(key, { name: nombre && nombre !== g.originalName ? nombre : null });
        d.cerrar();
        actualiza(ng);
        // Con el nombre nuevo se buscan otra vez los datos de la tienda
        if (api.meta) api.meta(key).then((mg) => { if (mg) actualiza(mg); }).catch(() => {});
        RV.aviso('Nombre guardado');
      } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
    };
    const d = RV.dialogo({
      ante: 'Cambiar el nombre', titulo: g.name,
      cuerpo: [RV.campo('Nombre que se verá', inp), h('p.texto', `Nombre original: ${g.originalName || g.name}. También se usa para buscar sus imágenes en SteamGridDB y sus datos en la tienda de Steam.`)],
      acciones: [
        { texto: 'Guardar', principal: true, fn: (x) => guardar(inp.value.trim(), x) },
        { texto: 'Volver al original', fn: (x) => guardar(null, x) },
        'hueco',
        { texto: 'Cancelar', fn: (x) => x.cerrar() }],
    });
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); guardar(inp.value.trim(), d); } });
  };
})(window.RV);
