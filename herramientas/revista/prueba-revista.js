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
    { key: 'steam:1422450', name: 'Deadlock', sourceLabel: 'Steam', lastPlayed: Date.now() - 3600e3, steamPlaytime: 3120 * 60000, art: {} },
    { key: 'steam:2622380', name: 'ELDEN RING NIGHTREIGN', sourceLabel: 'Steam', lastPlayed: Date.now() - 5 * 86400e3, steamPlaytime: 900 * 60000, art: {} },
    { key: 'steam:1145360', name: 'Hades II', sourceLabel: 'Steam', lastPlayed: Date.now() - 40 * 86400e3, steamPlaytime: 2400 * 60000, art: {} },
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
      };
    }, { cuenta, pc, juegos: RAWG_JUEGOS, tema });
    await page.goto(`http://localhost:${port}/`);
    await page.waitForTimeout(1500);
    const shot = async (n) => { await page.waitForTimeout(500); await page.screenshot({ path: path.join(out, `${tema}-${n}.png`), fullPage: true }); };
    const ir = async (r) => { await page.evaluate((x) => window.RV.ir(x), r); await page.waitForTimeout(400); };
    const texto = async () => page.evaluate(() => document.getElementById('pagina').innerText);
    const errorPagina = async () => /Fe de erratas|No se ha podido mostrar/.test(await texto());

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
    {
      await page.click('button.opcion:has-text("El lanzador")'); await page.waitForTimeout(300);
      const ui = await page.evaluate(() => window.RV.D.config().ui);
      ok(ui === 'lanzador', `${tema}: «Al abrir Atrio» guarda el lanzador (${ui})`);
      await page.click('button.opcion:has-text("La revista")'); await page.waitForTimeout(300);
    }
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
  await browser.close();
  srv.close();
  if (fallos) { console.log(`${fallos} fallos`); process.exitCode = 1; } else console.log('Todo bien');
})();
