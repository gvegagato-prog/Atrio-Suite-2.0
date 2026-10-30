'use strict';
// Streaming con Steam Link: en el móvil se juega con la app Steam Link (de Valve), que se conecta a Steam de este PC.
// Atrio no hace de servidor de streaming (lo hace Steam, con su Remote Play). Aquí solo:
//
//  · Mira si Steam está instalado y si está abierto (Steam Link no conecta con un PC sin Steam abierto).
//  · Abre Steam (en segundo plano, -silent) cuando el móvil va a jugar en streaming.
//  · Publica las direcciones de red y MAC del PC (para encenderlo por red desde el móvil).
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFile } = require('child_process');
const { HOME } = require('./paths');

const IS_WIN = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';
const FLATPAK_DIR = path.join(HOME, '.var/app/com.valvesoftware.Steam');
let info = { installed: false, running: false };
let timer = null;
let onChange = () => {};

const run = (bin, args, ms = 8000) => new Promise((resolve) => {
  execFile(bin, args, { timeout: ms, windowsHide: true }, (err, out) => resolve({ ok: !err, out: String(out || '') }));
});
const which = (bin) => require('./launcher').which(bin);

function lan() {
  const ips = [];
  const macs = [];
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    if (/^(lo|docker|br-|veth|virbr|tailscale|zt|wg)/i.test(name)) continue;
    for (const a of list || []) {
      if (a.internal || a.family !== 'IPv4') continue;
      if (/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(a.address)) continue; // 100.64/10: redes privadas virtuales
      ips.push(a.address);
      if (a.mac && a.mac !== '00:00:00:00:00:00' && !macs.includes(a.mac)) macs.push(a.mac);
    }
  }
  return { ips, macs };
}

// Windows: el Steam.exe que tiene apuntado el registro (o el sitio de siempre)
async function winSteamExe() {
  const r = await run('reg', ['query', 'HKCU\\Software\\Valve\\Steam', '/v', 'SteamExe'], 5000);
  const m = r.out.match(/SteamExe\s+REG_\w+\s+(.+)/i);
  const exe = m ? path.normalize(m[1].trim()) : path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', 'Steam', 'steam.exe');
  return fs.existsSync(exe) ? exe : null;
}
const macApp = () => ['/Applications/Steam.app', path.join(HOME, 'Applications/Steam.app')].find((p) => fs.existsSync(p)) || null;

// Cómo se abre Steam en este PC: [programa, argumentos] o null si no está instalado
async function opener() {
  if (IS_WIN) { const exe = await winSteamExe(); return exe ? [exe, ['-silent']] : null; }
  if (IS_MAC) { const app = macApp(); return app ? ['/usr/bin/open', ['-g', '-a', app, '--args', '-silent']] : null; }
  const bin = which('steam');
  if (bin) return [bin, ['-silent']];
  if (fs.existsSync(FLATPAK_DIR) && which('flatpak')) return ['flatpak', ['run', 'com.valvesoftware.Steam', '-silent']];
  return null;
}

// ¿Steam abierto? Linux: un proceso «steam» (el cliente y su lanzador se llaman así); Windows: steam.exe; macOS: steam_osx
async function running() {
  if (IS_WIN) return /steam\.exe/i.test((await run('tasklist', ['/FI', 'IMAGENAME eq steam.exe', '/NH'], 6000)).out);
  if (IS_MAC) return (await run('/usr/bin/pgrep', ['-x', 'steam_osx'], 4000)).ok;
  try {
    for (const pid of fs.readdirSync('/proc')) {
      if (!/^\d+$/.test(pid)) continue;
      try { if (fs.readFileSync(`/proc/${pid}/comm`, 'utf8').trim() === 'steam') return true; } catch { /* ya no existe */ }
    }
  } catch { /* sin /proc */ }
  return false;
}

async function refresh() {
  const next = { installed: !!(await opener()), running: await running(), name: os.hostname(), ...lan() };
  const changed = JSON.stringify(next) !== JSON.stringify(info);
  info = next;
  if (changed) onChange(info);
  return info;
}

// Abre Steam si no lo está (en segundo plano) y espera a que arranque
async function startSteam() {
  await refresh();
  if (info.running) return info;
  const how = await opener();
  if (!how) throw new Error('Steam no está instalado en este PC: Steam Link necesita Steam abierto.');
  await require('./launcher').spawnDetached(how[0], how[1]).catch(() => {});
  for (let i = 0; i < 40 && !(await refresh()).running; i++) await new Promise((x) => setTimeout(x, 750));
  if (!info.running) throw new Error('No he podido abrir Steam. Ábrelo a mano y vuelve a probar.');
  return info;
}

function init(opts) {
  onChange = opts.onChange || onChange;
  refresh().catch(() => {});
  clearInterval(timer);
  timer = setInterval(() => refresh().catch(() => {}), 30000);
}

module.exports = { init, refresh, info: () => info, startSteam };
