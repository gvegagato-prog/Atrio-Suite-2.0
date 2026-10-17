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

  // ---------------------------------------------------------------- El selector (común)
  // f: { titulo, tipos: [[id, texto]], inicial, actual(tipo) → url, listar(tipo, pagina) → { sgdbId, items },
  //      poner(tipo, url|ruta) → promesa, quitar(tipo), archivo (bool), url (bool: pegar una dirección),
  //      equivocado(), sinLista: texto si no se puede buscar }
  function selector(f) {
    let tipo = f.inicial || f.tipos[0][0];
    let pagina = 0;
    let turno = 0;
    const nombreTipo = (t) => f.tipos.find(([x]) => x === t)[1];
    const pestanas = h('div.j-grupo.arte-tipos', { role: 'tablist' });
    const actual = h('div.arte-actual');
    const rejilla = h('div.arte-rejilla');
    const estado = h('p.texto.arte-estado');
    const mas = h('button.enlace', { type: 'button', hidden: true, onclick: () => { pagina++; cargar(true); } }, 'Cargar más');

    function pintarPestanas() {
      pestanas.replaceChildren(...f.tipos.map(([t, txt]) => h('button.j-opt', {
        type: 'button', role: 'tab', 'aria-pressed': String(t === tipo), 'data-autofocus': t === tipo ? true : null, onclick: () => cambiar(t),
      }, txt)));
    }
    function pintarActual() {
      const src = f.actual(tipo);
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
    async function poner(src, el, aviso) {
      if (el) el.classList.add('cargando');
      try {
        await f.poner(tipo, src);
        if (el) { for (const p of rejilla.querySelectorAll('.elegida')) p.classList.remove('elegida'); el.classList.add('elegida'); }
        pintarActual();
        RV.aviso(aviso || `${nombreTipo(tipo)}: cambiado`);
      } catch (e) { RV.aviso(RV.limpiaError(e), 'error'); }
      if (el) el.classList.remove('cargando');
    }
    async function cargar(seguir) {
      const mio = ++turno;
      if (!seguir) { rejilla.className = `arte-rejilla ${tipo}`; rejilla.replaceChildren(); }
      mas.hidden = true;
      if (f.sinLista) { estado.textContent = f.sinLista; return; }
      estado.textContent = 'Buscando en SteamGridDB…';
      try {
        const res = await f.listar(tipo, pagina);
        if (mio !== turno) return;
        const items = (tipo === 'hero' || tipo === 'wide') ? [...res.items].sort((a, b) => (b.width || 0) - (a.width || 0)) : res.items;
        if (!items.length && !seguir) {
          estado.textContent = res.sgdbId
            ? 'SteamGridDB no tiene imágenes de este tipo para este juego. Prueba otro tipo o pon una a tu gusto.'
            : `No he encontrado este juego en SteamGridDB.${f.equivocado ? ' Usa «Juego equivocado» para buscarlo por otro nombre.' : ''}`;
          return;
        }
        estado.textContent = '';
        const primero = rejilla.children.length;
        for (const it of items) {
          const b = h('button.arte-opcion', { type: 'button', title: it.author ? `De ${it.author}` : '', onclick: () => poner(it.url, b) },
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
      if (p) poner(p, null, 'Imagen puesta');
    };
    const pegarDireccion = () => {
      const inp = RV.entrada('', { placeholder: 'https://…/imagen.jpg', 'data-autofocus': true, class: 'entrada mono' });
      const vale = (u) => /^https:\/\/\S+$/i.test(u);
      const ok = (d) => { const u = inp.value.trim(); if (!vale(u)) { RV.aviso('Tiene que ser una dirección https://', 'error'); return; } d.cerrar(); poner(u, null, 'Imagen puesta'); };
      const d = RV.dialogo({
        ante: nombreTipo(tipo), titulo: 'Una imagen de internet',
        cuerpo: [RV.campo('Dirección de la imagen', inp), h('p.texto', 'Se guarda en tu cuenta, así que también la verás en el móvil.')],
        acciones: [{ texto: 'Poner', principal: true, fn: ok }, 'hueco', { texto: 'Cancelar', fn: (x) => x.cerrar() }],
      });
      inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); ok(d); } });
    };
    pintarPestanas(); pintarActual();
    RV.dialogo({
      ante: 'Imágenes · LB y RB cambian de tipo', titulo: f.titulo, ancho: true,
      cuerpo: [h('div.arte-cabeza', pestanas, actual), estado, h('div.arte-scroll.desplaza', rejilla, h('div.fila-botones', mas))],
      acciones: [
        f.archivo ? { texto: 'Desde un archivo', fn: desdeArchivo } : null,
        f.url ? { texto: 'Pegar una dirección', fn: pegarDireccion } : null,
        { texto: 'Quitar esta', fn: () => poner(null, null, 'Imagen quitada') },
        f.equivocado ? { texto: 'Juego equivocado', fn: (d) => { d.cerrar(); f.equivocado(); } } : null,
        'hueco',
        { texto: 'Hecho', principal: true, fn: (d) => d.cerrar() }],
      alCerrar: () => { abierto = null; },
    });
    abierto = { paso: (n) => { const i = f.tipos.findIndex(([t]) => t === tipo); cambiar(f.tipos[(i + n + f.tipos.length) % f.tipos.length][0], true); } };
    cargar(false);
  }

  // ---------------------------------------------------------------- Imágenes de un juego del PC
  RV.arteJuego = (key, tipoInicial) => {
    const g0 = juego(key);
    if (!g0) return;
    selector({
      titulo: g0.name, tipos: TIPOS, inicial: tipoInicial, archivo: true,
      actual: (t) => ((juego(key) || {}).art || {})[t] || '',
      listar: (t, p) => api.sgdbAssets(key, t, null, p),
      poner: async (t, src) => actualiza(await api.setArt(key, t, src)),
      equivocado: () => RV.juegoEquivocado(key),
    });
  };

  // ---------------------------------------------------------------- Imágenes de una ficha de tu cuenta
  // (también de los juegos que no están en este PC; se guardan en la cuenta y se ven en el móvil)
  RV.arteFicha = (kind, t, item0) => {
    const fresca = () => D.buscar(kind, t, item0.id) || item0;
    const tipos = [['cover', 'Portada'], ['hero', 'Fondo'], ['logo', 'Logo']];
    selector({
      titulo: item0.name, tipos, url: true,
      actual: (tp) => fresca()[tp] || '',
      // Los juegos de la cuenta se identifican por su id de SteamGridDB
      listar: (tp, p) => api.ronronAssets(Number(fresca().id), tp, p),
      sinLista: kind.isGame ? null : 'Para esta ficha no hay catálogo de imágenes: pega la dirección de una imagen.',
      poner: async (tp, src) => {
        const cur = fresca();
        const next = { ...cur, [tp]: src || '' };
        const op = t === 'g' ? { op: 'saveGame', kind: kind.id, item: D.completarJuego(next) } : { op: 'savePlay', kind: kind.id, item: D.completarFicha(next) };
        if (!(await RV.editar(op, null))) throw new Error('No se ha podido guardar');
      },
    });
  };

  // LB / RB con el selector abierto (lo llama app.js)
  RV.arteNav = (paso) => { if (!abierto) return false; abierto.paso(paso); return true; };

  // ---------------------------------------------------------------- Juego equivocado (SteamGridDB)
  RV.juegoEquivocado = (key) => {
    const g = juego(key);
    if (!g) return;
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
