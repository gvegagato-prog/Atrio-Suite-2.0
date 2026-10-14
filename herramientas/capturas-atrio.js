#!/usr/bin/env node
'use strict';
// Capturas de la interfaz de Atrio con la suite conectada (Firebase de mentira, un «móvil» conectado,
// un juego en curso con partidas por valorar y apuntes rápidos). Uso: node herramientas/capturas-atrio.js [carpeta]
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DESK = path.join(ROOT, 'escritorio');
const OUT = path.resolve(process.argv[2] || path.join(ROOT, 'capturas'));
const PORT = 9700 + Math.floor(Math.random() * 200);
const FB = `http://127.0.0.1:${PORT}`;
const HOME = path.join(os.tmpdir(), 'capturas-atrio-home');
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
async function j(method, url, body) {
  const r = await fetch(url, { method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text();
  try { return JSON.parse(t); } catch { return t; }
}

(async () => {
  fs.rmSync(HOME, { recursive: true, force: true });
  fs.mkdirSync(path.join(HOME, '.config', 'atrio'), { recursive: true });
  const fake = spawn(process.execPath, [path.join(__dirname, 'fakefirebase.js'), String(PORT)], { stdio: 'ignore' });
  await wait(700);
  const su = await j('POST', `${FB}/v1/accounts:signUp?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  const uid = su.localId;
  const tok = su.idToken;
  const db = (m, p, b) => j(m, `${FB}${p}.json?auth=${tok}`, b);
  await db('PUT', '/config/webKey', 'prueba');
  const now = Date.now();
  const play = {
    id: 4242, name: 'Juego de Prueba', cover: '', hero: '', status: 1, chapter: 0, every: 3, start: 0, end: 0, creator: 'Estudio', platform: 'PC',
    pages: 0, minutes: 754, updated: now, ttbMain: 1500, ttbExtra: 0, ttbFull: 0, ttbSrc: '', parts: [], logo: '',
    entries: [{ time: now - 86400000 * 2, general: 82, gfx: 90, story: 75, music: 80, gameplay: 85, note: 'El segundo acto engancha muchísimo.', chapter: 0, seg: 'a1' }],
    segs: [
      { id: 'a1', s: now - 86400000 * 2 - 7200000, e: now - 86400000 * 2, min: 120, src: 'atrio', pc: 'sobremesa' },
      { id: 'a2', s: now - 5400000, e: now - 600000, min: 80, src: 'atrio', pc: 'sobremesa' },
    ],
    memos: [{ t: now - 1800000, x: 'El jefe del puente: probar con fuego la próxima vez.', u: now }],
  };
  const data = { games: [], plays: [play], books: [], bplays: [], kinds: [], tombs: {}, kindsAt: 0, recs: [], reacts: {}, keys: {}, goals: {}, likes: {} };
  await db('PUT', `/users/${uid}/dev/mtest`, { name: 'gonzalo', user: 'gonzalo', device: 'android', updated: { '.sv': 'timestamp' }, data: JSON.stringify(data) });
  const ts = await db('GET', `/users/${uid}/dev/mtest/updated`);
  await db('PATCH', `/users/${uid}`, { 'stamps/mtest': ts });
  await db('PUT', `/users/${uid}/suite/live/mtest`, { app: 'ronron', name: 'Pixel de Gonzalo', os: 'android', on: true, at: { '.sv': 'timestamp' } });
  const sig = await j('POST', `${FB}/v1/accounts:signInWithPassword?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid, user: 'gonzalo', refresh: sig.refreshToken, dev: 'atrio-prueba', seen: {} }));
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'config.json'), JSON.stringify({
    sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: true },
    customGames: [
      { id: 'prueba', name: 'Juego de Prueba', exec: '/bin/true', runner: 'native' },
      { id: 'otro', name: 'Otro juego', exec: '/bin/true', runner: 'native' },
      { id: 'deadlock', name: 'Deadlock', exec: '/bin/true', runner: 'native' },
    ],
    overrides: { 'custom:prueba': { sgdbId: 4242 }, 'custom:deadlock': { online: true } },
    behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' },
  }));
  const plan = path.join(HOME, 'plan.json');
  fs.writeFileSync(plan, JSON.stringify([
    { wait: 9000, name: '01-biblioteca' },
    { click: '#btn-suite', wait: 2500, name: '02-panel-suite' },
    { close: true, wait: 500 },
    { page: 'custom:prueba', wait: 2000, name: '03-pagina-juego' },
    { close: true, wait: 500 },
    { filter: 'valorados', wait: 1500, name: '04-valorados' },
    { click: '.rv-head .seg [data-v="curso"]', wait: 1200, name: '05-en-curso' },
    { click: '[data-rr="p:4242"]', wait: 1800, name: '06-ficha-ronron' },
    { close: true, wait: 500 },
    { open: 'settings', section: 'suite', wait: 1500, name: '07-ajustes-suite' },
    { close: true, wait: 500 },
    { page: 'custom:deadlock', wait: 1500 },
    { click: '.gp-web-btn', wait: 2500, name: '08-guias-inicio' },
    { click: '.web-card', wait: 9000, name: '09-guias-web', screen: true },
    { click: '.web-save', wait: 1500, name: '10-guias-guardada', screen: true },
    { close: true, wait: 600 },
    { open: 'settings', section: 'apariencia', wait: 1500, name: '11-apariencia' },
    { open: 'settings', section: 'streaming', wait: 1800, name: '12-ajustes-streaming' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-neon', font: 'cyberpunk' } }, wait: 1800, name: '13-estilo-cyberpunk' },
    { config: { theme: { scheme: 'p-gameboy', font: 'arcade' } }, wait: 1800, name: '14-estilo-arcade' },
    { config: { theme: { scheme: 'p-dracula', font: 'geometrica' } }, wait: 1800, name: '15-estilo-dracula' },
    { config: { theme: { scheme: 'p-catppuccin-claro', font: 'retro' } }, wait: 1800, name: '16-estilo-latte' },
    { config: { theme: { scheme: 'p-vino', font: 'fantasia' } }, wait: 1800, name: '17-estilo-fantasia' },
    { config: { theme: { scheme: 'noche', font: 'grotesca' } }, wait: 600 },
    { config: { theme: { scheme: 'noche', font: 'grotesca', form: 'clasico' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '20-forma-clasico-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '20-forma-clasico-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-cafe', font: 'elegante', form: 'editorial' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '21-forma-editorial-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '21-forma-editorial-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-gameboy', font: 'arcade', form: 'pixel' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '22-forma-pixel-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '22-forma-pixel-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-ambar', font: 'terminal', form: 'terminal' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '23-forma-terminal-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '23-forma-terminal-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-neon', font: 'cyberpunk', form: 'neon' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '24-forma-neon-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '24-forma-neon-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-catppuccin', font: 'retro', form: 'suave' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '25-forma-suave-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '25-forma-suave-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'oled', font: 'geometrica', form: 'brutal' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '26-forma-brutal-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '26-forma-brutal-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-atardecer', font: 'retro', form: 'comic' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '27-forma-comic-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '27-forma-comic-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-hielo-claro', font: 'grotesca', form: 'minimal' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '28-forma-minimal-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '28-forma-minimal-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-bosque', font: 'deportiva', form: 'militar' } }, wait: 1500 },
    { filter: 'all', wait: 1500, name: '29-forma-militar-biblioteca' },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '29-forma-militar-ficha' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-gameboy', font: 'arcade', form: 'pixel', gauge: 'stars' } }, wait: 1500 },
    { filter: 'valorados', wait: 1200 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '30-mezcla-pixel-estrellas' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'p-neon', font: 'cyberpunk', form: 'neon', gauge: 'blocks' } }, wait: 1500 },
    { click: '[data-rr="p:4242"]', wait: 1500, name: '31-mezcla-neon-bloques' },
    { close: true, wait: 400 },
    { open: 'settings', section: 'apariencia', wait: 1500, name: '32-apariencia-notas' },
    { close: true, wait: 400 },
    { config: { theme: { scheme: 'noche', font: 'grotesca', form: 'clasico', gauge: '' } }, wait: 600 },
  ]));
  fs.mkdirSync(OUT, { recursive: true });
  const env = { ...process.env, ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_NO_SANDBOX: '1', ATRIO_SHOTS: OUT, ATRIO_SHOT_PLAN: plan, ATRIO_SIZE: '1720x968' };
  for (const k of ['XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME']) delete env[k];
  const electron = path.join(DESK, 'node_modules', 'electron', 'dist', process.platform === 'win32' ? 'electron.exe' : 'electron');
  const a = spawn('xvfb-run', ['-a', '-s', '-screen 0 3500x1500x24', electron, '--no-sandbox', DESK], { env, stdio: ['ignore', 'pipe', 'pipe'] });
  a.stdout.on('data', (d) => { const s = String(d); if (/->|error/.test(s)) process.stdout.write(s); });
  await new Promise((r) => a.on('exit', r));
  fake.kill();
  console.log('Capturas en', OUT, fs.readdirSync(OUT).join(', '));
})();
