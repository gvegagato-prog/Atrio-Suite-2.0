#!/usr/bin/env node
'use strict';
// Abre Atrio de verdad (bajo Xvfb) contra el Firebase de mentira con la cuenta indicada, para la prueba en el
// emulador: el móvil lo ve conectado, le pide capturas y le lanza «Juego de Prueba» (un proceso que dura 10 min).
//   node herramientas/lanzar-atrio.js <firebase> <usuario> <contraseña> <carpeta>
// Se queda en marcha hasta que se le mata; «pkill -f juego-de-prueba» cierra el juego.
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const [FB, USER, PASS, OUT0] = process.argv.slice(2);
const OUT = path.resolve(OUT0);
const ROOT = path.resolve(__dirname, '..');
const HOME = path.join(OUT, 'atrio-home');

(async () => {
  fs.mkdirSync(path.join(HOME, '.config', 'atrio'), { recursive: true });
  const r = await fetch(`${FB}/v1/accounts:signInWithPassword?key=prueba`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `${USER}@ronron.example.com`, password: PASS, returnSecureToken: true }),
  });
  const s = await r.json();
  if (!s.localId) { console.error('No puedo entrar:', JSON.stringify(s)); process.exit(1); }
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid: s.localId, user: USER, refresh: s.refreshToken, dev: 'atrio-ci', seen: {} }));
  const game = path.join(OUT, 'juego-de-prueba.sh');
  fs.writeFileSync(game, '#!/bin/sh\nsleep 600\n', { mode: 0o755 });
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'config.json'), JSON.stringify({
    sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: true },
    customGames: [{ id: 'prueba', name: 'Juego de Prueba', exec: game, runner: 'native' }],
    overrides: { 'custom:prueba': { sgdbId: 4242 } },
    behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' },
  }));
  const env = { ...process.env, ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_RONRON_EVERY: '5000', ATRIO_NO_SANDBOX: '1', ATRIO_POWER_DRY: '1' };
  for (const k of ['XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME', 'WAYLAND_DISPLAY', 'XDG_CURRENT_DESKTOP']) delete env[k];
  const electron = path.join(ROOT, 'escritorio', 'node_modules', 'electron', 'dist', 'electron');
  const log = fs.openSync(path.join(OUT, 'atrio.log'), 'a');
  const a = spawn('xvfb-run', ['-a', '-s', '-screen 0 1920x1080x24', electron, '--no-sandbox', '--ventana', path.join(ROOT, 'escritorio')], { env, stdio: ['ignore', log, log] });
  console.log('Atrio en marcha, pid', a.pid);
  a.on('exit', (c) => process.exit(c || 0));
})();
