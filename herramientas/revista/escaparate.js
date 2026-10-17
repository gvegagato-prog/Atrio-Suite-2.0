#!/usr/bin/env node
'use strict';
// Escaparate del escritorio: capturas del modo Juegos y de la revista con una biblioteca realista, a varios
// tamaños de pantalla y en claro y oscuro, para revisar el diseño a ojo. No comprueba nada (eso es
// prueba-revista.js). Con ATRIO_ARTE=carpeta usa las imágenes de esa carpeta (<appid>_library_600x900.jpg,
// <appid>_library_hero.jpg, <appid>_header.jpg, <appid>_logo.png); sin ella, portadas de colores.
// Uso: node herramientas/revista/escaparate.js carpeta-capturas [1920x1080,1280x800] [claro,oscuro]
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const RAIZ = path.resolve(__dirname, '../../escritorio/src/renderer/revista');
const ARTE = process.env.ATRIO_ARTE ? path.resolve(process.env.ATRIO_ARTE) : null;
const out = process.argv[2] || 'escaparate';
const TAMANOS = (process.argv[3] || '1920x1080,1280x800').split(',').map((s) => s.split('x').map(Number));
const TEMAS = (process.argv[4] || 'claro,oscuro').split(',');
const SOLO = process.env.SOLO ? new RegExp(process.env.SOLO) : null;
fs.mkdirSync(out, { recursive: true });
const MIME = { html: 'text/html', js: 'text/javascript', css: 'text/css', ttf: 'font/ttf', json: 'application/json', jpg: 'image/jpeg', png: 'image/png' };
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const base = u.startsWith('/arte/') && ARTE ? ARTE : RAIZ;
  const f = path.join(base, u.startsWith('/arte/') ? u.slice(6) : (u === '/' ? 'index.html' : u));
  if (!f.startsWith(base) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

const DIA = 86400000;
const hoy = new Date();
const H = Math.floor(Date.UTC(hoy.getFullYear(), hoy.getMonth(), hoy.getDate()) / DIA);
const tapa = (t, a, b) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900"><defs><linearGradient id="g" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="600" height="900" fill="url(#g)"/><text x="40" y="820" font-size="80" fill="#fff9" font-family="serif">${t}</text></svg>`)}`;
// [appid, nombre, horas, hace (días; null = sin estrenar), fav, color]
const BIBLIO = [
  [1422450, 'Deadlock', 52, 0.05, false, '#c87a1a'], [2622380, 'ELDEN RING NIGHTREIGN', 15, 5, false, '#556'],
  [1145350, 'Hades II', 40, 12, true, '#904e95'], [1086940, "Baldur's Gate 3", 210, 30, true, '#6b2a2a'],
  [1245620, 'ELDEN RING', 160, 90, false, '#8a7a40'], [413150, 'Stardew Valley', 75, 200, false, '#4a7a3a'],
  [367520, 'Hollow Knight', 44, 400, true, '#2a3a5a'], [1091500, 'Cyberpunk 2077', 120, 150, false, '#b0a020'],
  [814380, 'Sekiro: Shadows Die Twice', 65, 600, false, '#5a2020'], [504230, 'Celeste', 22, 700, false, '#5a3a8a'],
  [646570, 'Slay the Spire', 130, 60, false, '#3a5a6a'], [892970, 'Valheim', 0, null, false, '#3a4a3a'],
];
const arte = (id, color, n) => (ARTE ? {
  cover: `/arte/${id}_library_600x900.jpg`, hero: `/arte/${id}_library_hero.jpg`, wide: `/arte/${id}_header.jpg`, logo: `/arte/${id}_logo.png`,
} : { cover: tapa(n, color, '#111'), hero: tapa(n, color, '#222') });
const pc = {
  config: { ui: 'revista', revista: { tema: 'claro', acento: 'rojo' }, behavior: {} },
  games: [
    ...BIBLIO.map(([id, name, hrs, hace, fav, color]) => ({
      key: `steam:${id}`, name, source: 'steam', sourceLabel: 'Steam', lastPlayed: hace == null ? 0 : Date.now() - hace * DIA,
      steamPlaytime: hrs * 3600e3, favorite: fav, art: arte(id, color, name),
      meta: id === 1422450 ? { description: 'Un shooter de héroes por equipos de Valve: cuatro carriles, almas que recoger y una ciudad que defender.', developers: ['Valve'], genres: ['Acción', 'MOBA'], release: '2024', controller: 'full' }
        : id === 1086940 ? { description: 'Reúne a tu grupo y vuelve a los Reinos Olvidados en una historia de compañerismo, traición y supervivencia.', developers: ['Larian Studios'], genres: ['Rol'], release: '2023', metacritic: 96 } : {},
    })),
    ...['Hollow Knight: Silksong', 'Hades', 'Disco Elysium', 'Outer Wilds'].map((n, i) => ({ key: `heroic:${i}`, name: n, source: 'heroic', sourceLabel: 'Heroic', lastPlayed: 0, steamPlaytime: 0, art: ARTE ? {} : { cover: tapa(n, `hsl(${i * 70},40%,35%)`, '#111') } })),
  ],
  play: { sessions: [], active: [] },
};
const juego = (id, name, general, end, extra = {}) => ({ id, name, general, gfx: general - 3, story: general - 6, music: general + 2, gameplay: general, end, start: end - 20, review: '', cover: '', hero: '', creator: '', platform: 'PC', minutes: 1500, updated: end * DIA, ...extra });
const cuenta = {
  status: { signed: true, user: 'Gonzalo', partner: null },
  data: {
    keys: {}, kinds: [],
    games: [
      juego(1, "Baldur's Gate 3", 97, H - 30, { creator: 'Larian Studios', review: 'La mejor partida de rol que he jugado nunca: cada decisión pesa y el acto 3 es un regalo.', fav: true }),
      juego(2, 'Hades II', 91, H - 60, { creator: 'Supergiant Games' }), juego(3, 'Hollow Knight', 93, H - 400, { creator: 'Team Cherry', fav: true }),
      juego(4, 'Sekiro: Shadows Die Twice', 90, H - 600, { creator: 'FromSoftware' }), juego(5, 'ELDEN RING', 95, H - 90, { creator: 'FromSoftware' }),
      juego(6, 'Celeste', 89, H - 700, { creator: 'Maddy Makes Games' }), juego(7, 'Cyberpunk 2077', 84, H - 150, { creator: 'CD Projekt Red' }),
      juego(8, 'Starfield', 52, H - 120, { creator: 'Bethesda' }), juego(9, 'Outer Wilds', 94, H - 365, { creator: 'Mobius Digital' }),
    ],
    plays: [
      { id: 10, name: 'Deadlock', status: 1, creator: 'Valve', minutes: 3120, start: H - 40, updated: Date.now() - 3600e3, entries: [{ time: Date.now() - 7200e3, note: 'La partida de hoy, muy igualada: el último tramo con Haze, de infarto.', general: 80, gfx: 75, story: 50, music: 70, gameplay: 85 }] },
      { id: 11, name: 'ELDEN RING NIGHTREIGN', status: 1, creator: 'FromSoftware', minutes: 900, start: H - 10, updated: Date.now() - 86400e3 },
    ],
    books: [{ id: 401, name: 'Dune', general: 89, end: H - 32, creator: 'Frank Herbert', pages: 690, updated: (H - 32) * DIA }],
    bplays: [{ id: 402, name: 'El nombre del viento', status: 1, creator: 'Patrick Rothfuss', chapter: 31, pages: 880, start: H - 12, updated: Date.now() - 5e6 }],
  },
};

(async () => {
  await new Promise((r) => srv.listen(0, r));
  const port = srv.address().port;
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  for (const [w, hh] of TAMANOS) {
    for (const tema of TEMAS) {
      const ctx = await browser.newContext({ viewport: { width: w, height: hh }, deviceScaleFactor: 1, colorScheme: tema === 'oscuro' ? 'dark' : 'light' });
      const page = await ctx.newPage();
      page.on('pageerror', (e) => console.log('pageerror', e.message));
      page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) console.log('console', m.text()); });
      await page.addInitScript(({ cuenta: c, pc: p, tema: tm }) => {
        p.config.revista.tema = tm;
        for (const g of p.games) for (const k of Object.keys(g.art || {})) if (g.art[k].startsWith('/arte/')) g.art[k] = location.origin + g.art[k];
        const boton = () => ({ pressed: false, value: 0 });
        window.__fakePads = [{ id: 'Steam Controller (STANDARD GAMEPAD)', mapping: 'standard', connected: true, buttons: Array.from({ length: 17 }, boton), axes: [0, 0, 0, 0] }];
        window.__pulsar = async (i) => {
          const pad = window.__fakePads[0];
          pad.buttons[i] = { pressed: true, value: 1 };
          await new Promise((r) => setTimeout(r, 70));
          pad.buttons[i] = { pressed: false, value: 0 };
          await new Promise((r) => setTimeout(r, 90));
        };
        const n = async () => {};
        window.atrio = {
          state: async () => p, ronron: async () => c, on: () => {}, ronronEdit: async () => c, log: n, patchConfig: async (x) => { Object.assign(p.config, x); return p; },
          launch: async () => ({}), switchUi: n, toggleFullscreen: n, minimize: n, quit: n, rescan: async () => p, ronronArt: async () => ({}),
          override: async (key, patch) => { const g = p.games.find((x) => x.key === key); Object.assign(g, patch); return { ...g }; },
          power: n, webOpen: async (u) => ({ open: true, url: u, title: 'Guía', loading: false }), webHide: n, webShow: async () => ({}), webClose: async () => ({ open: false }), webNav: n, webBounds: n,
          autostart: async () => ({ available: false }), suiteMethods: async () => [], suiteShots: async () => [], suiteShotShow: async () => true, sgdbSearch: async () => [], streamInfo: async () => null, protonList: async () => [], netJson: async () => ({ results: [] }),
        };
      }, { cuenta, pc, tema });
      await page.goto(`http://localhost:${port}/`);
      await page.waitForTimeout(1500);
      const nombre = (n) => `${w}x${hh}-${tema}-${n}.png`;
      const shot = async (n, full) => { if (SOLO && !SOLO.test(n)) return; await page.waitForTimeout(700); await page.screenshot({ path: path.join(out, nombre(n)), fullPage: !!full }); };
      const btn = (i) => page.evaluate((x) => window.__pulsar(x), i);
      const ir = async (r) => { await page.evaluate((x) => window.RV.ir(x), r); await page.waitForTimeout(500); };
      const A = 0, B = 1, Y = 3, LB = 4, ABAJO = 13, DER = 15, MENU = 9;
      await shot('j1-portada');
      await btn(DER); await btn(DER);
      await shot('j1b-portada-foco');
      await shot('j1c-portada-entera', true);
      await btn(ABAJO); await btn(ABAJO); await btn(DER);
      await shot('j2-biblioteca-foco');
      await btn(Y); await shot('j3-opciones'); await btn(B);
      await ir({ v: 'j-juego', key: 'steam:1086940' }); await shot('j4-pagina-juego');
      await ir({ v: 'j-juego', key: 'steam:1422450' }); await shot('j4b-pagina-jugando', true);
      await ir({ v: 'juegos' }); await btn(LB); await page.waitForTimeout(500); await shot('j5-valorados');
      await btn(MENU); await shot('j6-menu'); await btn(B);
      await page.evaluate(() => window.RV.buscarJuegos()); await page.keyboard.type('el'); await shot('j7-buscar'); await page.keyboard.press('Escape');
      await ir({ v: 'ajustes', sec: 'mando' }); await shot('j8-ajustes-mando');
      await page.evaluate(() => window.RV.cambiarModo('revista')); await page.waitForTimeout(700);
      await shot('r1-portada');
      await btn(ABAJO); await btn(ABAJO);
      await shot('r2-portada-foco');
      await ir({ v: 'pc' }); await shot('r3-pc');
      await ir({ v: 'seccion', k: 0 }); await btn(ABAJO); await btn(ABAJO); await btn(ABAJO); await shot('r4-seccion-foco');
      await ir({ v: 'ficha', k: 0, t: 'g', id: 1 }); await shot('r5-ficha');
      await btn(ABAJO); await btn(ABAJO); await shot('r5b-ficha-foco');
      await ir({ v: 'diario' }); await btn(ABAJO); await btn(ABAJO); await shot('r6-diario');
      await ir({ v: 'descubrir', dom: 0 }); await btn(ABAJO); await shot('r7-descubrir');
      await ir({ v: 'cifras' }); await btn(ABAJO); await shot('r8-cifras');
      await ir({ v: 'ajustes' }); await btn(ABAJO); await btn(ABAJO); await shot('r9-ajustes');
      await ctx.close();
      console.log('hecho', w, hh, tema);
    }
  }
  await browser.close();
  srv.close();
})().catch((e) => { console.error(e); process.exit(1); });
