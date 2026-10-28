'use strict';
// Registro de partidas: detecta qué juego está abierto mirando /proc y guarda sesiones
// (juego, inicio, fin). Funciona con juegos lanzados desde Atrio y desde fuera (Steam, Heroic…),
// siempre que Atrio esté abierto, aunque sea minimizado o en segundo plano.
const fs = require('fs');
const path = require('path');
const { XDG_DATA, readJson, mkdirp } = require('./paths');

const DATA_DIR = path.join(XDG_DATA, 'atrio');
const LOG_PATH = path.join(DATA_DIR, 'playlog.json');
const MIN_SESSION_MS = 30 * 1000;      // sesiones más cortas no cuentan (arranques fallidos)
const MISSES_TO_END = 2;               // lecturas sin el juego para darlo por cerrado
const SLOW_MS = 10 * 1000;
const FAST_MS = 3 * 1000;
const GAP_MS = 2 * 60 * 1000;          // un hueco así entre lecturas = el equipo estaba suspendido

const IS_WIN = process.platform === 'win32';
// En Windows las rutas no distinguen mayúsculas y usan «\»: se comparan normalizadas
const norm = (p) => (IS_WIN ? String(p).replace(/\\/g, '/').toLowerCase() : String(p));

// Rutas que identifican a un juego en la línea de comandos de sus procesos
function trackPaths(g) {
  const out = [];
  if (g.installDir && fs.existsSync(g.installDir)) out.push({ p: norm(g.installDir).replace(/\/+$/, '') + '/', dir: true });
  if (g.romPath) out.push({ p: norm(g.romPath) });
  if (g.exePath) out.push({ p: norm(g.exePath) });
  return out;
}

// ---------- Windows: lista de procesos ----------
// Un único PowerShell en segundo plano lee cada pocos segundos los procesos (ruta del ejecutable y
// línea de órdenes) y el juego de Steam en marcha según el registro. Así no se abre un proceso por lectura.
const WIN_PS = `
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
while ($true) {
  $run = 0
  try { $run = (Get-ItemProperty -Path 'HKCU:\\Software\\Valve\\Steam' -Name RunningAppID).RunningAppID } catch {}
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("@@BEGIN $run")
  foreach ($p in (Get-CimInstance Win32_Process -Property ProcessId,Name,ExecutablePath,CommandLine)) {
    $lines.Add(("{0}\`t{1}\`t{2}\`t{3}" -f $p.ProcessId, $p.Name, $p.ExecutablePath, (($p.CommandLine -replace "[\`r\`n\`t]", " "))))
  }
  $lines.Add("@@END")
  [Console]::Out.Write(($lines -join "\`n") + "\`n")
  [Console]::Out.Flush()
  Start-Sleep -Seconds 4
}
`;
let winSnap = { procs: [], steamRunning: 0, at: 0 };
let winChild = null;
function startWinProcs() {
  if (winChild) return;
  const { spawn } = require('child_process');
  const enc = Buffer.from(WIN_PS, 'utf16le').toString('base64');
  try {
    winChild = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', enc],
      { windowsHide: true, stdio: ['ignore', 'pipe', 'ignore'] });
  } catch { winChild = null; return; }
  let buf = '';
  let cur = null;
  winChild.stdout.setEncoding('utf8');
  winChild.stdout.on('data', (chunk) => {
    buf += chunk;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).replace(/\r$/, '');
      buf = buf.slice(i + 1);
      if (line.startsWith('@@BEGIN')) cur = { procs: [], steamRunning: Number(line.slice(8)) || 0 };
      else if (line === '@@END') { if (cur) { cur.at = Date.now(); winSnap = cur; } cur = null; }
      else if (cur) {
        const [pid, name, exe, cmd] = line.split('\t');
        if (Number(pid) === process.pid) continue;
        cur.procs.push({ pid: Number(pid), name: (name || '').toLowerCase(), exe: norm(exe || ''), cmd: norm(cmd || '') });
      }
    }
  });
  winChild.on('exit', () => { winChild = null; setTimeout(startWinProcs, 5000); });
}
function stopWinProcs() {
  if (!winChild) return;
  const c = winChild;
  winChild = null;
  c.removeAllListeners('exit');
  try { c.kill(); } catch { /* */ }
}

// Ejecutables de la carpeta de un juego (para los procesos cuya ruta Windows no deja leer, p. ej. con antitrampas)
const exeCache = new Map();
const NOT_GAME_EXE = /^(unins\d*|uninstall|setup|vc_?redist.*|dxsetup|dotnet.*|crashreport.*|unitycrashhandler(32|64)?|crashpad_handler|easyanticheat_setup|launcher)\.exe$/i;
function exeNames(dir) {
  if (exeCache.has(dir)) return exeCache.get(dir);
  const out = new Set();
  const walk = (d, depth) => {
    let entries = [];
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      if (out.size > 60) return;
      if (e.isDirectory() && depth > 0) walk(path.join(d, e.name), depth - 1);
      else if (/\.exe$/i.test(e.name) && !NOT_GAME_EXE.test(e.name)) out.add(e.name.toLowerCase());
    }
  };
  walk(dir, 3);
  exeCache.set(dir, out);
  return out;
}

function readCmdlines() {
  if (IS_WIN) {
    startWinProcs();
    // Si la lectura se ha quedado vieja (PowerShell bloqueado), mejor no decir nada que dar partidas falsas
    if (Date.now() - winSnap.at > 30 * 1000) return [];
    return winSnap.procs.map((p) => `${p.exe}\0${p.cmd}`);
  }
  // macOS no tiene /proc: se lee la lista de procesos con ps
  if (process.platform === 'darwin') {
    try {
      return require('child_process').execFileSync('/bin/ps', ['-axww', '-o', 'command='], { encoding: 'utf8', timeout: 4000, maxBuffer: 16 * 1024 * 1024 })
        .split('\n').filter(Boolean);
    } catch { return []; }
  }
  const out = [];
  let pids = [];
  try { pids = fs.readdirSync('/proc').filter((p) => /^\d+$/.test(p)); } catch { return out; }
  for (const pid of pids) {
    if (Number(pid) === process.pid) continue;
    try {
      const c = fs.readFileSync(`/proc/${pid}/cmdline`, 'utf8');
      if (c) out.push(c);
    } catch { /* el proceso ya no existe */ }
  }
  return out;
}

function alive(pid) {
  try { process.kill(pid, 0); return true; } catch { return false; }
}

function createTracker({ getGames, onChange, onEnd, getUid }) {
  const data = readJson(LOG_PATH, null) || {};
  const sessions = Array.isArray(data.sessions) ? data.sessions : []; // [key, inicio, fin, cuenta?]
  const forgot = data.forgot && typeof data.forgot === 'object' ? data.forgot : {}; // key -> cuándo se borró su historial
  const who = () => { try { return (getUid && getUid()) || null; } catch { return null; } };
  // Cada partida se apunta a la cuenta de Ronrón con la que estabas (si habías entrado)
  const rec = (key, s, e) => { const u = who(); sessions.push(u ? [key, s, e, u] : [key, s, e]); };
  const active = new Map();   // key -> { s: inicio, last: última vez visto, miss }
  const pids = new Map();     // key -> pid lanzado por Atrio (emuladores, juegos propios)
  let expecting = null;       // { key, until } juego recién lanzado desde Atrio
  let timer = null;
  let dirty = false;
  let lastSave = 0;

  // Sesiones que quedaron abiertas si Atrio se cerró a mitad de partida
  const pending = data.active && typeof data.active === 'object' ? data.active : {};

  function save(force) {
    if (!dirty && !force) return;
    const now = Date.now();
    if (!force && now - lastSave < 60 * 1000 && !dirty) return;
    const act = {};
    for (const [k, a] of active) act[k] = { s: a.s, last: a.last };
    try {
      mkdirp(DATA_DIR);
      const tmp = LOG_PATH + '.tmp';
      fs.writeFileSync(tmp, JSON.stringify({ version: 1, sessions, active: act, forgot }));
      fs.renameSync(tmp, LOG_PATH);
      lastSave = now;
      dirty = false;
    } catch { /* sin espacio o sin permisos: se reintenta en la siguiente lectura */ }
  }

  function close(key, a) {
    active.delete(key);
    pids.delete(key);
    if (a.last - a.s >= MIN_SESSION_MS) {
      rec(key, a.s, a.last);
      dirty = true;
      return true;
    }
    dirty = true;
    return false;
  }

  function tick() {
    const now = Date.now();
    const games = getGames();
    const cmdlines = readCmdlines();
    const running = new Set();
    // Steam lanza sus juegos (y los añadidos a Steam) con "reaper SteamLaunch AppId=<id>"
    const steamIds = new Set();
    for (const c of cmdlines) {
      if (!c.includes('SteamLaunch')) continue;
      const m = c.match(/(?:^|\0)AppId=(\d+)(?:\0|$)/);
      if (m) steamIds.add(m[1]);
    }
    // Windows: Steam apunta en el registro el juego que tiene abierto
    if (IS_WIN && winSnap.steamRunning && Date.now() - winSnap.at <= 30 * 1000) steamIds.add(String(winSnap.steamRunning));
    // Windows: procesos sin ruta legible (se comparan por nombre del ejecutable)
    const blindNames = IS_WIN && Date.now() - winSnap.at <= 30 * 1000
      ? new Set(winSnap.procs.filter((p) => !p.exe && p.name.endsWith('.exe')).map((p) => p.name)) : null;
    for (const g of games) {
      if (g.steamTrackId && steamIds.has(String(g.steamTrackId))) { running.add(g.key); continue; }
      const paths = trackPaths(g);
      if (paths.length && cmdlines.some((c) => paths.some((t) => c.includes(t.p)))) { running.add(g.key); continue; }
      if (blindNames && blindNames.size && g.installDir && fs.existsSync(g.installDir)) {
        for (const n of exeNames(g.installDir)) if (blindNames.has(n)) { running.add(g.key); break; }
      }
    }
    for (const [key, pid] of pids) if (alive(pid)) running.add(key);

    let changed = false;
    // Primera lectura: recuperar sesiones pendientes
    if (pending) {
      for (const [key, a] of Object.entries(pending)) {
        if (!a || !a.s) continue;
        if (running.has(key)) active.set(key, { s: a.s, last: now, miss: 0 });
        else if ((a.last || a.s) - a.s >= MIN_SESSION_MS) { rec(key, a.s, a.last); dirty = true; changed = true; }
      }
      for (const k of Object.keys(pending)) delete pending[k];
    }
    for (const key of running) {
      const a = active.get(key);
      // Si ha pasado mucho desde la última lectura, el equipo ha estado suspendido: ese rato no se cuenta.
      // La partida de antes se cierra donde se quedó y empieza otra al despertar.
      if (a && now - a.last > GAP_MS) {
        const counted = close(key, a);
        changed = true;
        if (onEnd) onEnd(key, counted, false);
      } else if (a) { a.last = now; a.miss = 0; continue; }
      active.set(key, { s: now, last: now, miss: 0 });
      dirty = true;
      changed = true;
      if (expecting && expecting.key === key) expecting.started = true;
    }
    for (const [key, a] of active) {
      if (running.has(key)) continue;
      if (++a.miss < MISSES_TO_END) continue;
      const counted = close(key, a);
      changed = true;
      if (onEnd) onEnd(key, counted, expecting && expecting.key === key);
      if (expecting && expecting.key === key) expecting = null;
    }
    if (expecting && !expecting.started && now > expecting.until) expecting = null;
    if (changed) { save(true); if (onChange) onChange(); } else if (active.size && now - lastSave > 60 * 1000) { dirty = true; save(true); }
    schedule();
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(tick, active.size || expecting ? FAST_MS : SLOW_MS);
  }

  return {
    start() { tick(); },
    stop() { clearTimeout(timer); save(true); if (IS_WIN) stopWinProcs(); },
    // Antes de suspender: se cierran las partidas en curso para que el rato dormido no cuente
    pause() {
      for (const [key, a] of [...active]) { close(key, a); if (onEnd) onEnd(key, true, false); }
      save(true);
      if (onChange) onChange();
    },
    // Se acaba de lanzar este juego desde Atrio
    expect(key, pid) {
      if (pid) pids.set(key, pid);
      expecting = { key, until: Date.now() + 20 * 60 * 1000, started: false };
      schedule();
    },
    // Las partidas de antes de entrar en ninguna cuenta pasan a ser de esta
    claim(uid) {
      if (!uid) return false;
      let n = 0;
      for (const ses of sessions) if (!ses[3]) { ses[3] = uid; n++; }
      if (n) { dirty = true; save(true); }
      return n > 0;
    },
    forgotten() { return forgot; },
    state() {
      return {
        sessions,
        active: [...active].map(([k, a]) => ({ k, s: a.s, last: a.last })),
      };
    },
    // Borra el historial de un juego
    forget(key) {
      for (let i = sessions.length - 1; i >= 0; i--) if (sessions[i][0] === key) sessions.splice(i, 1);
      forgot[key] = Date.now();
      dirty = true;
      save(true);
    },
    LOG_PATH,
  };
}

module.exports = { createTracker, trackPaths };
