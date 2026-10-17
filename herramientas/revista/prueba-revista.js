#!/usr/bin/env node
'use strict';
// Prueba de la revista de escritorio (la parte web) en Chromium, con un puente de mentira en lugar de Electron
// y una cuenta de ejemplo. Recorre todas las páginas, hace capturas y falla si la página da algún error.
// Uso: node herramientas/revista/prueba-revista.js carpeta-capturas
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const RAIZ = path.resolve(__dirname, '../../escritorio/src/renderer/revista');
const out = process.argv[2] || 'capturas-revista';
fs.mkdirSync(out, { recursive: true });
const MIME = { html: 'text/html', js: 'text/javascript', css: 'text/css', ttf: 'font/ttf', json: 'application/json' };
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const f = path.join(RAIZ, u === '/' ? 'index.html' : u);
  if (!f.startsWith(RAIZ) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

// ---------------------------------------------------------------- La cuenta de ejemplo
const DIA = 86400000;
const hoy = new Date();
const dia = (y, m, d) => Math.floor(Date.UTC(y, m - 1, d) / DIA);
const Y = hoy.getFullYear();
const H = dia(Y, hoy.getMonth() + 1, hoy.getDate());
const tapa = (t, a, b) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300"><defs><linearGradient id="g"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="600" height="300" fill="url(#g)"/><text x="30" y="250" font-size="90" fill="#fff8" font-family="serif">${t}</text></svg>`)}`;
const juego = (id, name, general, end, extra = {}) => ({ id, name, general, gfx: general - 3, story: general - 6, music: general + 2, gameplay: general, end, start: end - 20, review: '', cover: '', hero: '', creator: '', platform: 'PC', minutes: 1500, updated: end * DIA, ...extra });
const cuenta = {
  status: { signed: true, user: 'Gonzalo', partner: null },
  data: {
    keys: { rawg: 'clave-rawg', tmdb: 'clave-tmdb' },
    kinds: [{ id: 2, title: 'Películas', criteria: ['Guion', 'Dirección'], progress: 'Viendo', source: 'movie' }],
    games: [
      juego(1, 'Clair Obscur: Expedition 33', 94, H - 30, { creator: 'Sandfall Interactive', review: 'Un juego de rol por turnos que se juega como uno de acción. La música y el último acto, de lo mejor de la década.', hero: tapa('Clair Obscur', '#3a2a6b', '#d08c3a'), minutes: 3900 }),
      juego(2, 'Hades II', 91, H - 60, { creator: 'Supergiant Games', minutes: 2400 }),
      juego(3, 'Hollow Knight: Silksong', 88, H - 4, { creator: 'Team Cherry', minutes: 3300 }),
      juego(4, 'Balatro', 85, H - 95, { creator: 'LocalThunk', minutes: 4100 }),
      juego(5, 'The Witcher 3: Wild Hunt', 96, dia(Y - 3, hoy.getMonth() + 1, hoy.getDate()), { creator: 'CD Projekt Red', minutes: 9000 }),
      juego(6, 'Starfield', 52, H - 120, { creator: 'Bethesda', minutes: 1800 }),
      juego(7, 'Outer Wilds', 93, dia(Y - 1, hoy.getMonth() + 1, hoy.getDate()), { creator: 'Mobius Digital' }),
    ],
    plays: [
      { id: 10, name: 'Deadlock', status: 1, creator: 'Valve', minutes: 3120, start: H - 40, updated: Date.now() - 3600e3, hero: tapa('Deadlock', '#e08a1c', '#8a4a08'),
        entries: [{ time: Date.now() - 7200e3, note: 'La partida de hoy ha sido muy igualada: el último tramo con Haze, de infarto.', general: 80, gfx: 75, story: 50, music: 70, gameplay: 85 }] },
      { id: 11, name: 'Elden Ring Nightreign', status: 1, creator: 'FromSoftware', minutes: 900, start: H - 10, updated: Date.now() - 86400e3 },
      { id: 12, name: 'Death Stranding 2', status: 0, creator: 'Kojima Productions' },
    ],
    books: [{ id: 401, name: 'Dune', general: 89, gfx: 50, story: 92, music: 88, gameplay: 80, end: H - 32, creator: 'Frank Herbert', pages: 690, updated: (H - 32) * DIA }],
    bplays: [{ id: 402, name: 'El nombre del viento', status: 1, creator: 'Patrick Rothfuss', chapter: 31, pages: 880, start: H - 12, updated: Date.now() - 5e6 }],
    items_2: [{ id: 27205, name: 'Origen', general: 87, gfx: 80, story: 90, end: H - 70, creator: 'Christopher Nolan', updated: (H - 70) * DIA }],
    iplays_2: [],
  },
};
const pc = {
  config: { ui: 'revista', revista: { tema: process.env.TEMA || 'claro', acento: 'rojo' } },
  games: [
    { key: 'steam:1422450', name: 'Deadlock', source: 'steam', sourceLabel: 'Steam', lastPlayed: Date.now() - 3600e3, steamPlaytime: 3120 * 60000, art: { hero: tapa('Deadlock', '#e08a1c', '#8a4a08'), cover: tapa('D', '#e08a1c', '#5a2a04') }, meta: { description: 'Un shooter de héroes por equipos de Valve.', developers: ['Valve'], genres: ['Acción'] } },
    { key: 'steam:2622380', name: 'ELDEN RING NIGHTREIGN', source: 'steam', sourceLabel: 'Steam', lastPlayed: Date.now() - 5 * 86400e3, steamPlaytime: 900 * 60000, art: { hero: tapa('Nightreign', '#334', '#889'), cover: tapa('E', '#223', '#667') }, links: [{ u: 'https://example.com/guia', n: 'Guía de prueba' }] },
    { key: 'steam:1145360', name: 'Hades II', source: 'steam', sourceLabel: 'Steam', lastPlayed: Date.now() - 40 * 86400e3, steamPlaytime: 2400 * 60000, art: { cover: tapa('H', '#904e95', '#e96443') }, favorite: true },
    ...Array.from({ length: 14 }, (_, i) => ({ key: `heroic:${i}`, name: `Juego de Heroic ${i + 1}`, source: 'heroic', sourceLabel: 'Heroic', lastPlayed: 0, steamPlaytime: 0, art: { cover: tapa(String(i + 1), `hsl(${i * 25},50%,40%)`, '#222') } })),
    { key: 'custom:x', name: 'Juego oculto', source: 'custom', sourceLabel: 'Tuyo', hidden: true, art: {} },
  ],
  play: { sessions: [], active: [] },
};
// Respuestas de internet de mentira
const RAWG_JUEGOS = Array.from({ length: 30 }, (_, i) => ({
  id: 9000 + i, name: `Juego de prueba ${i + 1}`, released: `${2010 + (i % 15)}-05-01`, background_image: '', metacritic: 70 + (i % 25),
  rating: 4, playtime: 10 + i, added: 1000 * i, genres: [{ slug: i % 2 ? 'role-playing-games-rpg' : 'adventure', name: 'RPG' }], tags: [{ slug: 'singleplayer' }],
}));

(async () => {
  await new Promise((r) => srv.listen(0, r));
  const port = srv.address().port;
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  let fallos = 0;
  const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
  for (const tema of ['claro', 'oscuro']) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, colorScheme: tema === 'oscuro' ? 'dark' : 'light' });
    const page = await ctx.newPage();
    const log = [];
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) log.push(m.text()); });
    page.on('pageerror', (e) => log.push(`pageerror ${e.message}`));
    await page.addInitScript(({ cuenta: c, pc: p, juegos, tema: tm }) => {
      p.config.revista.tema = tm;
      const ls = {};
      const edits = [];
      window.__edits = edits;
      window.atrio = {
        state: async () => p, ronron: async () => c, on: (ev, fn) => { (ls[ev] ||= []).push(fn); },
        ronronEdit: async (op) => { edits.push(op); return c; }, ronronArt: async () => ({ cover: '', hero: '', logo: '' }),
        patchConfig: async (x) => { Object.assign(p.config, x); return p; }, log: () => {}, launch: async (k) => { (window.__lanzados ||= []).push(k); return {}; },
        netJson: async (url) => {
          if (url.includes('/platforms/lists/parents')) return { results: [{ slug: 'pc', id: 1 }, { slug: 'playstation', id: 2 }, { slug: 'nintendo', id: 7 }] };
          if (url.includes('api.rawg.io/api/games/')) return { description_raw: 'Resumen de prueba.', metacritic: 88 };
          if (url.includes('api.rawg.io')) return { results: juegos, next: null };
          if (url.includes('openlibrary.org')) return { docs: [{ key: '/works/OL1W', title: 'Libro de prueba', author_name: ['Autora'], first_publish_year: 1990, cover_i: 1, ratings_average: 4.2, subject: ['fantasy'] }] };
          if (url.includes('themoviedb')) return { results: [{ id: 1, title: 'Peli de prueba', release_date: '2001-01-01', poster_path: '/a.jpg', vote_average: 7.5, genre_ids: [18] }], crew: [] };
          return [];
        },
        switchUi: async () => {}, toggleFullscreen: async () => {}, minimize: async () => {}, quit: async () => {}, rescan: async () => p,
        openExternal: async () => {}, sgdbSearch: async () => [], sgdbTest: async () => ({ ok: true }), suiteShots: async () => [], suiteShotShow: async () => true,
        ronronLogin: async () => c, ronronLogout: async () => c, ronronSync: async () => c,
        override: async (key, patch) => { const g = p.games.find((x) => x.key === key); Object.assign(g, patch); return { ...g }; },
        pickFile: async () => '/home/gonzalo/Juegos/juego.AppImage', protonList: async () => [{ name: 'GE-Proton10', path: '/proton/ge10' }],
        autostart: async () => ({ available: true, mode: 'visible', installed: true, path: '/home/gonzalo/Applications/Atrio.AppImage', steam: true }),
        setAutostart: async () => ({}), suiteMethods: async () => ['spectacle', 'grim'], refetchArt: async () => {},
        streamInfo: async () => ({ installed: true, running: false, creds: false, ts: { installed: false }, ips: ['192.168.1.20'] }),
        streamStart: async () => ({}), streamCheck: async () => ({ ok: true, msg: 'Bien' }), streamWeb: async () => {},
        power: async (a) => { window.__energia = a; },
        webOpen: async (u) => { window.__web = u; return { open: true, url: u, title: 'Guía', loading: false }; },
        webHide: async () => {}, webShow: async () => ({}), webClose: async () => { window.__webCerrada = true; return { open: false }; }, webNav: async () => {}, webBounds: async () => {},
      };
    }, { cuenta, pc, juegos: RAWG_JUEGOS, tema });
    await page.goto(`http://localhost:${port}/`);
    await page.waitForTimeout(1500);
    const shot = async (n) => { await page.waitForTimeout(500); await page.screenshot({ path: path.join(out, `${tema}-${n}.png`), fullPage: true }); };
    const ir = async (r) => { await page.evaluate((x) => window.RV.ir(x), r); await page.waitForTimeout(400); };
    const texto = async () => page.evaluate(() => document.getElementById('pagina').innerText);
    const errorPagina = async () => /Fe de erratas|No se ha podido mostrar/.test(await texto());
    // Atrio arranca en el modo Juegos; esta pasada es la de la revista
    await page.evaluate(() => window.RV.cambiarModo('revista')); await page.waitForTimeout(500);

    await shot('01-portada');
    let t = await texto();
    ok(t.includes('Deadlock') && t.includes('Elden Ring Nightreign'), `${tema}: portada con lo que llevas entre manos`);
    ok(/Tal día como hoy/i.test(t) && t.includes('The Witcher 3') && t.includes('Outer Wilds'), `${tema}: «Tal día como hoy» con lo terminado tal día como hoy`);
    ok(new RegExp(`Tu ${Y}, número especial`).test(t), `${tema}: el sumario enlaza el número especial`);

    await ir({ v: 'especial' }); await shot('02-especial');
    t = await texto();
    ok(!(await errorPagina()) && t.includes('Clair Obscur') && /Lo mejor del año/i.test(t) && /Mes a mes/i.test(t), `${tema}: número especial ${Y}`);
    ok(/La decepción/i.test(t) && t.includes('Starfield'), `${tema}: número especial con la decepción`);
    await page.click('.especial .folio button');
    await page.waitForTimeout(400);
    t = await texto();
    ok(!(await errorPagina()) && new RegExp(`Tu ${Y - 1}`).test(t) && t.includes('Outer Wilds'), `${tema}: el año anterior (${Y - 1})`);
    await shot('02b-especial-anterior');

    for (const [k, n] of [[0, 'juegos'], [1, 'libros'], [2, 'pelis']]) {
      await ir({ v: 'seccion', k }); await shot(`03-seccion-${n}`);
      ok(!(await errorPagina()), `${tema}: sección ${n}`);
      if (k === 0) {
        // Los juegos instalados en el PC: «Jugar →» en su fila
        const b = await page.$('.item-fila > .enlace');
        ok(!!b, `${tema}: «Jugar →» en la sección de videojuegos`);
        if (b) { await b.click(); await page.waitForTimeout(300); }
        const l = await page.evaluate(() => window.__lanzados || []);
        ok(l.includes('steam:1145360') || l.includes('steam:1422450'), `${tema}: «Jugar →» lanza el juego (${l.join(', ')})`);
      }
    }
    await ir({ v: 'ficha', k: 0, t: 'g', id: 1 }); await shot('04-ficha-valorado');
    ok(!(await errorPagina()) && (await texto()).includes('Clair Obscur'), `${tema}: ficha valorada`);
    await ir({ v: 'ficha', k: 0, t: 'p', id: 10 }); await shot('05-ficha-en-curso');
    ok(!(await errorPagina()) && (await texto()).includes('Deadlock'), `${tema}: ficha en curso`);
    await ir({ v: 'ficha', k: 1, t: 'p', id: 402 }); await shot('05b-ficha-libro');
    ok(!(await errorPagina()), `${tema}: ficha de libro`);
    await ir({ v: 'diario' }); await shot('06-diario');
    ok(!(await errorPagina()), `${tema}: diario`);
    await ir({ v: 'cifras' }); await shot('07-cifras');
    ok(!(await errorPagina()), `${tema}: cifras`);
    await ir({ v: 'pc' }); await shot('08-pc');
    ok(!(await errorPagina()), `${tema}: tu PC`);
    await ir({ v: 'ajustes' }); await shot('09-ajustes');
    // Todas las secciones de Ajustes (las del PC, traídas del lanzador clásico)
    for (const sec of ['revista', 'cuenta', 'bibliotecas', 'propios', 'emuladores', 'ocultos', 'mando', 'comportamiento', 'movil', 'streaming']) {
      await ir({ v: 'ajustes', sec }); await page.waitForTimeout(250);
      if (tema === 'claro') await shot(`09-ajustes-${sec}`);
      ok(!(await errorPagina()) && (await page.$$('.ajuste')).length > 0, `${tema}: Ajustes → ${sec}`);
    }
    await ir({ v: 'ajustes', sec: 'propios' });
    await page.click('button:has-text("+ Añadir un juego")'); await page.waitForTimeout(300);
    await page.click('.dialogo .ruta button'); await page.waitForTimeout(200);
    const nom = await page.$eval('.dialogo input', (i) => i.value);
    ok(nom === 'juego', `${tema}: añadir un juego propio, al elegir el archivo se rellena el nombre («${nom}»)`);
    await page.keyboard.press('Escape'); await page.waitForTimeout(200);
    {
      await ir({ v: 'portada' });
      const n = await page.$$eval('ol.numerada li .enlace', (l) => l.filter((b) => /Jugar/.test(b.textContent)).length);
      ok(n === 1, `${tema}: portada, «Jugar →» en «Ahora mismo» solo en lo instalado (${n})`);
    }
    ok(!(await errorPagina()), `${tema}: ajustes`);

    // Descubrir un juego: intro con filtros, siete preguntas con el teclado y resultados
    await ir({ v: 'descubrir', dom: 0 }); await shot('10-descubrir');
    t = await texto();
    ok(!(await errorPagina()) && /Filtr|año|plataforma/i.test(t), `${tema}: descubrir (intro con filtros)`);
    await page.click('.desc-n button.acento');
    await page.waitForTimeout(600);
    await shot('11-pregunta');
    ok(/Pregunta 1 de 7/i.test(await texto()), `${tema}: primera pregunta`);
    for (let i = 0; i < 7; i++) { await page.keyboard.press(i % 3 === 2 ? 'Space' : i % 2 ? 'b' : 'a'); await page.waitForTimeout(250); }
    await page.waitForFunction(() => /resultados/i.test(document.getElementById('pagina').innerText), null, { timeout: 15000 }).catch(() => {});
    await shot('12-resultados');
    t = await texto();
    ok(/resultados/i.test(t) && t.includes('Juego de prueba'), `${tema}: resultados (${(t.match(/(\d+) resultados/i) || [])[1] || 0})`);
    if (await page.$('.desc-res .ver')) {
      await page.click('.desc-res .ver'); await page.waitForTimeout(800); await shot('13-resultado-abierto');
      ok(/Metacritic|88/.test(await page.evaluate(() => document.body.innerText)), `${tema}: al abrir un juego, resumen y Metacritic`);
      await page.keyboard.press('Escape');
    }
    await ir({ v: 'descubrir', dom: 1 });
    ok(!(await errorPagina()), `${tema}: descubrir un libro`);
    await ir({ v: 'descubrir', dom: 2 });
    ok(!(await errorPagina()), `${tema}: descubrir una peli o serie`);

    // Ventana estrecha
    await page.setViewportSize({ width: 760, height: 900 });
    await ir({ v: 'portada' }); await shot('14-portada-estrecha');
    await ir({ v: 'especial' }); await shot('15-especial-estrecho');
    ok(!(await errorPagina()), `${tema}: ventana estrecha`);
    // Nada se sale de lado
    const ancho = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    ok(ancho <= 1, `${tema}: sin desplazamiento lateral en ventana estrecha (${ancho}px)`);

    ok(!log.length, `${tema}: sin errores en la página${log.length ? `: ${log.slice(0, 5).join(' | ')}` : ''}`);
    await ctx.close();
  }
  // ---------------- El modo Juegos (el lanzador) con un mando simulado
  {
    const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    const log = [];
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) log.push(m.text()); });
    page.on('pageerror', (e) => log.push(`pageerror ${e.message}`));
    await page.addInitScript(({ cuenta: c, pc: p }) => {
      p.config.revista.tema = 'claro';
      const boton = () => ({ pressed: false, value: 0 });
      window.__fakePads = [{ id: 'Mando de prueba (STANDARD GAMEPAD)', mapping: 'standard', connected: true, buttons: Array.from({ length: 17 }, boton), axes: [0, 0, 0, 0] }];
      window.__pulsar = async (i) => {
        const pad = window.__fakePads[0];
        pad.buttons[i] = { pressed: true, value: 1 };
        await new Promise((r) => setTimeout(r, 70));
        pad.buttons[i] = { pressed: false, value: 0 };
        await new Promise((r) => setTimeout(r, 70));
      };
      const n = async () => {};
      window.atrio = {
        state: async () => p, ronron: async () => c, on: () => {}, ronronEdit: async (op) => { (window.__edits ||= []).push(op); return c; }, log: n,
        ronronAssets: async (id, type) => ({ sgdbId: id, items: [{ id: 1, url: `https://cdn.example/${type}.png`, thumb: p.games[0].art.cover, width: 600, height: 900 }] }), patchConfig: async (x) => { Object.assign(p.config, x); return p; },
        launch: async (k) => { (window.__lanzados ||= []).push(k); return {}; }, switchUi: n, toggleFullscreen: n, minimize: n, quit: n, rescan: async () => p,
        override: async (key, patch) => { const g = p.games.find((x) => x.key === key); Object.assign(g, patch); return { ...g }; },
        power: async (a) => { window.__energia = a; },
        webOpen: async (u) => { window.__web = u; return { open: true, url: u, title: 'Guía', loading: false }; },
        webHide: n, webShow: async () => ({}), webClose: async () => { window.__webCerrada = true; return { open: false }; }, webNav: n, webBounds: n,
        autostart: async () => ({ available: false }), suiteMethods: async () => [], streamInfo: async () => null, protonList: async () => [],
        sgdbAssets: async (key, type) => ({ sgdbId: 7, items: [{ id: 1, url: `https://cdn.example/${type}.png`, thumb: p.games[0].art.cover, width: 600, height: 900, author: 'x' }] }),
        setArt: async (key, type, src) => { (window.__arte ||= []).push([key, type, src]); const g = p.games.find((x) => x.key === key); return { ...g }; },
      };
      p.config.sgdbKey = 'clave';
    }, { cuenta, pc });
    await page.goto(`http://localhost:${port}/`);
    await page.waitForTimeout(1600);
    const shot = async (n) => { await page.waitForTimeout(450); await page.screenshot({ path: path.join(out, `juegos-${n}.png`) }); };
    const btn = (i) => page.evaluate((x) => window.__pulsar(x), i);
    const A = 0, B = 1, Y = 3, LB = 4, RB = 5, VISTA = 8, MENU = 9, ABAJO = 13, DER = 15;
    const estado = () => page.evaluate(() => ({ v: window.RV.ruta().v, modo: window.RV.modo, foco: (document.activeElement && document.activeElement.textContent || '').trim().slice(0, 40), entrada: document.body.dataset.entrada }));
    let e = await estado();
    ok(e.modo === 'juegos' && e.v === 'juegos', `juegos: Atrio arranca en el modo Juegos (${e.modo}/${e.v})`);
    await shot('01-portada');
    const txt = await page.evaluate(() => document.body.innerText);
    ok(/Deadlock/i.test(txt) && /SEGUIR JUGANDO/i.test(txt) && /LA BIBLIOTECA/i.test(txt), 'juegos: portada con el último jugado, seguir jugando y la biblioteca');
    ok(!/Juego oculto/.test(txt), 'juegos: los juegos ocultos no salen');
    // A: Jugar (lo enfocado al empezar)
    await btn(A); await page.waitForTimeout(300);
    let lanz = await page.evaluate(() => window.__lanzados || []);
    ok(lanz[0] === 'steam:1422450', `juegos: A en «Jugar →» lanza el juego de portada (${lanz.join(', ')})`);
    e = await estado();
    ok(e.entrada === 'mando', 'juegos: con el mando se ve el foco (entrada «mando»)');
    // El escaparate cambia con la fila de portadas: abajo, derecha, arriba y A juega al de la portada enfocada
    const ARRIBA = 12;
    await btn(ABAJO); await page.waitForTimeout(150);
    e = await estado();
    ok(/Deadlock/.test(e.foco), `juegos: abajo entra en «Seguir jugando» por el juego de portada («${e.foco}»)`);
    await btn(DER); await page.waitForTimeout(450);
    const tit = await page.evaluate(() => (document.querySelector('.j-escaparate .titular') || {}).textContent);
    ok(tit === 'ELDEN RING NIGHTREIGN', `juegos: al recorrer la fila, el escaparate pasa al juego enfocado («${tit}»)`);
    await btn(ARRIBA); await page.waitForTimeout(150);
    e = await estado();
    ok(/Jugar/.test(e.foco), `juegos: arriba vuelve a «Jugar →» («${e.foco}»)`);
    await btn(A); await page.waitForTimeout(300);
    lanz = await page.evaluate(() => window.__lanzados || []);
    ok(lanz[1] === 'steam:2622380', `juegos: «Jugar →» juega al juego del escaparate (${lanz.join(', ')})`);
    await btn(ABAJO); await page.waitForTimeout(150);
    e = await estado();
    ok(/NIGHTREIGN/i.test(e.foco), `juegos: al volver a la fila, se vuelve a la portada de antes («${e.foco}»)`);
    await btn(ARRIBA); await page.waitForTimeout(150);
    // Bajar hasta la biblioteca y abrir un juego
    for (let i = 0; i < 4; i++) { await btn(ABAJO); await page.waitForTimeout(120); }
    await btn(DER); await page.waitForTimeout(150);
    e = await estado();
    await shot('02-foco-biblioteca');
    ok(e.foco.length > 0, `juegos: la cruceta mueve el foco («${e.foco}»)`);
    // Y: opciones del juego enfocado
    await btn(Y); await page.waitForTimeout(300);
    const opc = await page.evaluate(() => !!document.querySelector('#capa .dialogo') && document.querySelector('#capa .dialogo').innerText);
    ok(!!opc && /favoritos/i.test(opc), 'juegos: Y abre las opciones del juego');
    await shot('03-opciones');
    ok(/Imágenes/.test(opc) && /Cambiar el nombre/.test(opc), 'juegos: las opciones permiten cambiar las imágenes y el nombre');
    await page.click('#capa button:has-text("Imágenes")'); await page.waitForTimeout(500);
    ok(!!(await page.$('#capa .arte-opcion')), 'juegos: el selector de imágenes muestra las de SteamGridDB');
    await btn(RB); await page.waitForTimeout(400);
    const tipoArte = await page.evaluate(() => (document.querySelector('#capa .arte-tipos [aria-pressed="true"]') || {}).textContent);
    ok(tipoArte === 'Horizontal', `juegos: RB cambia el tipo de imagen («${tipoArte}»)`);
    await page.click('#capa .arte-opcion'); await page.waitForTimeout(400);
    const arte = await page.evaluate(() => window.__arte || []);
    ok(arte.length === 1 && arte[0][1] === 'wide' && /wide\.png$/.test(arte[0][2]), `juegos: elegir una imagen la guarda (${JSON.stringify(arte[0] || null)})`);
    await shot('03b-imagenes');
    await btn(B); await page.waitForTimeout(250);
    await btn(Y); await page.waitForTimeout(300);
    await btn(B); await page.waitForTimeout(250);
    ok(!(await page.$('#capa .dialogo')), 'juegos: B cierra las opciones');
    // A: su página
    await btn(A); await page.waitForTimeout(400);
    e = await estado();
    ok(e.v === 'j-juego', `juegos: A abre la página del juego (${e.v})`);
    await shot('04-pagina-juego');
    await btn(B); await page.waitForTimeout(400);
    e = await estado();
    ok(e.v === 'juegos', `juegos: B vuelve a la portada (${e.v})`);
    // LB: Valorados; RB, RB: Revista
    await btn(LB); await page.waitForTimeout(400);
    e = await estado();
    ok(e.v === 'j-valorados', `juegos: LB pasa a Valorados (${e.v})`);
    await shot('05-valorados');
    ok(/96/.test(await page.evaluate(() => document.getElementById('pagina').innerText)), 'juegos: Valorados con las notas');
    // Y también con los juegos que no están en este PC (The Witcher 3, el primero)
    await btn(Y); await page.waitForTimeout(300);
    const opcV = await page.evaluate(() => (document.querySelector('#capa .dialogo') || {}).innerText || '');
    ok(/Witcher/i.test(opcV) && /favoritos/i.test(opcV) && /Cambiar la nota/.test(opcV), 'juegos: Y abre las opciones de un juego que no está instalado (favorito, nota…)');
    // Sus imágenes (de la cuenta): elegir una portada la guarda en la ficha
    await page.click('#capa button:has-text("Imágenes de la ficha")'); await page.waitForTimeout(500);
    await page.click('#capa .arte-opcion'); await page.waitForTimeout(400);
    const ed = await page.evaluate(() => (window.__edits || []).slice(-1)[0] || null);
    ok(ed && ed.op === 'saveGame' && /cover\.png$/.test(ed.item.cover), `juegos: cambiar la portada de un juego no instalado la guarda en la cuenta (${ed && ed.item && ed.item.cover})`);
    await btn(B); await page.waitForTimeout(250);
    await btn(RB); await page.waitForTimeout(300);
    await btn(RB); await page.waitForTimeout(500);
    e = await estado();
    ok(e.modo === 'revista' && e.v === 'portada', `juegos: RB hasta «Revista» abre la revista (${e.modo}/${e.v})`);
    await shot('06-revista');
    // En la revista: RB recorre las secciones y Vista vuelve a Juegos
    await btn(RB); await page.waitForTimeout(400);
    e = await estado();
    ok(e.v === 'seccion', `revista con mando: RB pasa a la siguiente sección (${e.v})`);
    await btn(ABAJO); await page.waitForTimeout(150); await btn(ABAJO); await page.waitForTimeout(150);
    e = await estado();
    await shot('07-revista-foco');
    ok(e.foco.length > 0, `revista con mando: la cruceta recorre la página («${e.foco}»)`);
    await btn(VISTA); await page.waitForTimeout(500);
    e = await estado();
    ok(e.modo === 'juegos' && e.v === 'juegos', `juegos: Vista cambia de modo (${e.modo}/${e.v})`);
    // Menú: energía con confirmación (la opción segura enfocada)
    await btn(MENU); await page.waitForTimeout(300);
    await shot('08-menu');
    ok(/Apagar/.test(await page.evaluate(() => (document.querySelector('#capa') || {}).innerText || '')), 'juegos: Menú con apagar, reiniciar y suspender');
    await page.click('#capa button:has-text("Apagar")'); await page.waitForTimeout(300);
    await btn(A); await page.waitForTimeout(300);
    ok(!(await page.evaluate(() => window.__energia)), 'juegos: al pedir apagar, A en lo enfocado (Cancelar) no apaga');
    // Guías: desde la página de Nightreign (con una guía guardada se abre directamente)
    await page.evaluate(() => window.RV.ir({ v: 'j-juego', key: 'steam:2622380' })); await page.waitForTimeout(400);
    await page.click('button:has-text("Guías")'); await page.waitForTimeout(400);
    ok((await page.evaluate(() => window.__web)) === 'https://example.com/guia', 'juegos: Guías abre la web guardada');
    await shot('09-guia');
    await btn(B); await page.waitForTimeout(300);
    ok(await page.evaluate(() => window.__webCerrada === true), 'juegos: B cierra la guía');
    // Teclado: flechas e Intro
    await page.evaluate(() => window.RV.ir({ v: 'juegos' })); await page.waitForTimeout(400);
    await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowDown'); await page.waitForTimeout(200);
    e = await estado();
    ok(e.foco.length > 0, `teclado: las flechas mueven el foco («${e.foco}»)`);
    // Ratón: al moverlo, el marco del foco desaparece
    await page.mouse.move(400, 400); await page.mouse.move(460, 470); await page.waitForTimeout(100);
    e = await estado();
    ok(e.entrada === 'raton', 'ratón: al moverlo se usa el ratón (sin marco de foco)');
    // Ventana estrecha y alta (un monitor vertical o media pantalla)
    await page.setViewportSize({ width: 900, height: 1200 }); await page.waitForTimeout(300);
    await shot('10-estrecha');
    const ancho = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    ok(ancho <= 1, `juegos: sin desplazamiento lateral en ventana estrecha (${ancho}px)`);
    ok(!log.length, `juegos: sin errores en la página${log.length ? `: ${log.slice(0, 5).join(' | ')}` : ''}`);
    await ctx.close();
  }
  await browser.close();
  srv.close();
  if (fallos) { console.log(`${fallos} fallos`); process.exitCode = 1; } else console.log('Todo bien');
})();
