'use strict';
// Streaming: Atrio se encarga de Polaris (el servidor de streaming del PC, basado en Sunshine) para que desde el móvil
// juegues con Nova en casa o fuera de ella.
//
//  · Lo encuentra (paquete del sistema o AppImage en marcha), mira si va y lo arranca si hace falta (polaris.service).
//  · Lee su identificador y su nombre (los que ve Nova) para que el móvil abra Nova directo en este PC.
//  · Empareja el móvil sin teclear nada: pide a Polaris un código de un solo uso (OTP) y el móvil se lo pasa a Nova.
//    También vale el PIN de 4 números que enseña Nova, si lo prefieres.
//  · Mira Tailscale (para jugar con datos fuera de casa) y las direcciones de red y MAC (para encender el PC por red).
// Polaris solo existe para Linux (no hay versión de Windows).
const fs = require('fs');
const os = require('os');
const net = require('net');
const path = require('path');
const https = require('https');
const crypto = require('crypto');
const { execFile } = require('child_process');

// Pruebas: dirección de la API web (la de Polaris es el puerto base + 1)
const API_HOST = process.env.ATRIO_POLARIS_HOST || '127.0.0.1';
let info = { installed: false, running: false };
let timer = null;
let onChange = () => {};
let getCfg = () => ({});
let logf = () => {};
let session = null; // { cookie, csrf } de /api/login

const run = (bin, args, ms = 8000) => new Promise((resolve) => {
  execFile(bin, args, { timeout: ms, windowsHide: true, env: require('./launcher').cleanEnv() }, (err, out, errOut) => resolve({ ok: !err, out: String(out || ''), err: String(errOut || (err && err.message) || '') }));
});
const which = (bin) => require('./launcher').which(bin);

function confDir() {
  const home = process.env.ATRIO_HOME || os.homedir();
  return path.join(process.env.XDG_CONFIG_HOME || path.join(home, '.config'), 'polaris');
}

// polaris.conf: líneas «clave = valor»
function readConf(file) {
  const out = {};
  try {
    for (const l of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const m = /^\s*([\w.]+)\s*=\s*(.*?)\s*$/.exec(l);
      if (m && !l.trim().startsWith('#')) out[m[1]] = m[2];
    }
  } catch { /* sin fichero: valores por defecto */ }
  return out;
}

function portOpen(port, host = '127.0.0.1', ms = 700) {
  return new Promise((resolve) => {
    const s = net.connect({ port, host });
    const done = (v) => { s.destroy(); resolve(v); };
    s.setTimeout(ms, () => done(false));
    s.once('connect', () => done(true));
    s.once('error', () => done(false));
  });
}

function lan() {
  const ips = [];
  const macs = [];
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    if (/^(lo|docker|br-|veth|virbr|tailscale|zt|wg)/i.test(name)) continue;
    for (const a of list || []) {
      if (a.internal || a.family !== 'IPv4') continue;
      if (/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(a.address)) continue; // 100.64/10: Tailscale
      ips.push(a.address);
      if (a.mac && a.mac !== '00:00:00:00:00:00' && !macs.includes(a.mac)) macs.push(a.mac);
    }
  }
  return { ips, macs };
}

async function tailscale() {
  const bin = which(process.platform === 'win32' ? 'tailscale.exe' : 'tailscale')
    || (process.platform === 'win32' && fs.existsSync('C:\\Program Files\\Tailscale\\tailscale.exe') ? 'C:\\Program Files\\Tailscale\\tailscale.exe' : null);
  if (!bin) return null;
  const r = await run(bin, ['status', '--json'], 5000);
  if (!r.ok) return { installed: true, up: false };
  try {
    const j = JSON.parse(r.out);
    const self = j.Self || {};
    const ip = (self.TailscaleIPs || []).find((x) => /^\d+\.\d+\.\d+\.\d+$/.test(x)) || null;
    return { installed: true, up: j.BackendState === 'Running' && !!ip, ip, dns: String(self.DNSName || '').replace(/\.$/, '') || null };
  } catch { return { installed: true, up: false }; }
}

async function tailscale() {
  const bin = which(process.platform === 'win32' ? 'tailscale.exe' : 'tailscale')
    || (process.platform === 'win32' && fs.existsSync('C:\\Program Files\\Tailscale\\tailscale.exe') ? 'C:\\Program Files\\Tailscale\\tailscale.exe' : null);
  if (!bin) return null;
  const r = await run(bin, ['status', '--json'], 5000);
  if (!r.ok) return { installed: true, up: false };
  try {
    const j = JSON.parse(r.out);
    const self = j.Self || {};
    const ip = (self.TailscaleIPs || []).find((x) => /^\d+\.\d+\.\d+\.\d+$/.test(x)) || null;
    return { installed: true, up: j.BackendState === 'Running' && !!ip, ip, dns: String(self.DNSName || '').replace(/\.$/, '') || null };
  } catch { return { installed: true, up: false }; }
}

// UUID de la app de Polaris con ese nombre (en su apps.json): Nova la abre con él sin depender de su caché de apps
function appUuidOf(apps, name) {
  const want = String(name || 'Desktop').toLowerCase();
  const a = ((apps && apps.apps) || []).find((x) => String(x.name || '').toLowerCase() === want);
  return (a && typeof a.uuid === 'string' && a.uuid) || '';
}

function appUuidFromFile(dir, conf, name) {
  try {
    const f = conf.file_apps ? (path.isAbsolute(conf.file_apps) ? conf.file_apps : path.join(dir, conf.file_apps)) : path.join(dir, 'apps.json');
    return appUuidOf(JSON.parse(fs.readFileSync(f, 'utf8')), name);
  } catch { return ''; }
}

// Si el fichero no la tiene, se pregunta a Polaris (que además le pone UUID a las apps que no lo tengan)
async function appUuid() {
  if (info && info.appUuid) return info.appUuid;
  try {
    const r = await api('GET', '/api/apps');
    const u = appUuidOf(r, (getCfg().app || 'Desktop'));
    if (u && info) info.appUuid = u;
    return u;
  } catch { return ''; }
}

async function refresh() {
  const dir = confDir();
  const conf = readConf(path.join(dir, 'polaris.conf'));
  const port = Number(conf.port) || 47989;
  let uuid = null;
  try {
    const st = JSON.parse(fs.readFileSync(path.join(dir, conf.file_state || 'polaris_state.json'), 'utf8'));
    uuid = (st.root && st.root.uniqueid) || null;
  } catch { /* aún no ha arrancado nunca */ }
  const running = await portOpen(port);
  const bin = process.platform === 'win32' ? null : which('polaris');
  const c = getCfg();
  const next = {
    installed: !!bin || running || fs.existsSync(path.join(dir, 'polaris.conf')), bin: bin || null, running,
    linux: process.platform !== 'win32',
    name: conf.sunshine_name || os.hostname(), uuid, port, web: `https://localhost:${port + 1}`,
    app: (c.app || 'Desktop'),
    appUuid: appUuidFromFile(dir, conf, c.app || 'Desktop'),
    ...lan(),
    ts: await tailscale().catch(() => null),
    creds: !!((c.user && c.pass) || c.apiKey),
  };
  const changed = JSON.stringify(next) !== JSON.stringify(info);
  info = next;
  if (changed) onChange(info);
  return info;
}

// Arranca Polaris: su servicio de usuario (polaris.service) o el programa
async function startService() {
  await refresh();
  if (info.running) return info;
  if (!info.linux) throw new Error('Polaris solo funciona en Linux: en este PC no se puede preparar el streaming.');
  if (!info.installed) throw new Error('Polaris no está instalado en este PC. En Atrio: Ajustes → Streaming.');
  const r = await run('systemctl', ['--user', 'start', 'polaris.service'], 10000);
  if (!r.ok) logf('polaris: no arranca con systemctl', r.err.slice(0, 160));
  for (let i = 0; i < 20 && !(await refresh()).running; i++) await new Promise((x) => setTimeout(x, 750));
  if (!info.running && info.bin) {
    // Sin servicio: el programa directamente (sigue vivo aunque se cierre Atrio)
    await require('./launcher').spawnDetached(info.bin, []).catch(() => {});
    for (let i = 0; i < 24 && !(await refresh()).running; i++) await new Promise((x) => setTimeout(x, 750));
  }
  if (!info.running) throw new Error('No he podido arrancar Polaris. Ábrelo a mano una vez y vuelve a probar.');
  return info;
}

// Petición https a la API web de Polaris (certificado propio, solo en este PC)
function raw(method, p, body, headers = {}) {
  const data = body === undefined ? null : JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const req = https.request({
      host: API_HOST, port: (info.port || 47989) + 1, path: p, method, rejectUnauthorized: false, timeout: 130000,
      headers: { ...headers, ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}) },
    }, (res) => {
      let t = '';
      res.on('data', (d) => { t += d; });
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, text: t }));
    });
    req.on('timeout', () => req.destroy(new Error('Polaris no contesta')));
    req.on('error', (e) => reject(new Error(/ECONNREFUSED/.test(e.message) ? 'Polaris no está en marcha.' : e.message)));
    if (data) req.write(data);
    req.end();
  });
}

// Sesión con tu usuario de Polaris: cookie «auth» y el token CSRF que la web lleva en su <meta>
async function login() {
  const c = getCfg();
  const r = await raw('POST', '/api/login', { username: c.user, password: c.pass });
  if (r.status === 401) throw new Error('Polaris no acepta el usuario o la contraseña guardados en Atrio.');
  if (r.status === 429) throw new Error('Polaris ha bloqueado el acceso un rato por demasiados intentos fallidos.');
  if (r.status === 409) throw new Error('Polaris aún no tiene usuario: créalo en su panel (https://localhost:47990/#/welcome).');
  const set = [].concat(r.headers['set-cookie'] || []).map((x) => /(?:^|;\s*)auth=([^;]+)/.exec(x)).find(Boolean);
  if (r.status >= 400 || !set) throw new Error(`Polaris no ha dejado entrar (${r.status}).`);
  const cookie = `auth=${set[1]}`;
  const page = await raw('GET', '/', undefined, { Cookie: cookie });
  const m = /<meta name="csrf-token" content="([^"]+)"/.exec(page.text);
  session = { cookie, csrf: m ? m[1] : '' };
  return session;
}

// API de Polaris: con la clave de API (si la has puesto) o con tu usuario
async function api(method, p, body, retry = true) {
  const c = getCfg();
  let headers;
  if (c.apiKey) headers = { Authorization: `Bearer ${c.apiKey}` };
  else {
    if (!c.user || !c.pass) throw new Error('Falta tu usuario de Polaris en Atrio (Ajustes → Streaming).');
    if (!session) await login();
    headers = { Cookie: session.cookie, 'X-CSRF-Token': session.csrf };
  }
  const r = await raw(method, p, body, headers);
  // Sesión caducada (o Polaris reiniciado): se entra otra vez
  if ((r.status === 401 || r.status === 403) && !c.apiKey && retry) { session = null; return api(method, p, body, false); }
  if (r.status === 401) throw new Error('Polaris no acepta la clave o el usuario guardados en Atrio.');
  let j = null;
  try { j = JSON.parse(r.text); } catch { /* */ }
  if (r.status >= 400) throw new Error((j && (j.error || j.message)) || `Polaris ha contestado ${r.status}`);
  return j;
}

// PIN de emparejamiento (el que enseña Nova en el móvil)
async function pin(code, name) {
  code = String(code || '').replace(/\D/g, '');
  if (code.length !== 4) throw new Error('El PIN son 4 números.');
  await startService();
  const nm = String(name || 'Móvil').slice(0, 60);
  const pending = await api('GET', '/api/pin');
  const list = (pending && pending.pairings) || [];
  if (!list.length) throw new Error('Polaris no tiene ningún emparejamiento esperando: en Nova, toca el PC para que salga el PIN y vuelve a probar.');
  const r = await api('POST', '/api/pin', { pairing_id: list[list.length - 1].id, pin: code, name: nm });
  if (!r || r.status === false) throw new Error('Polaris no ha aceptado el PIN (¿ha caducado? vuelve a tocar el PC en Nova).');
  setTimeout(() => refresh().catch(() => {}), 1500);
  return true;
}

// Emparejar sin teclear: código de un solo uso de Polaris (vale 3 minutos) que Nova recibe en un enlace art://
async function otp(name) {
  await startService();
  const passphrase = crypto.randomBytes(6).toString('hex');
  const r = await api('POST', '/api/otp', { passphrase, deviceName: String(name || 'Móvil').slice(0, 60) });
  if (!r || !r.otp) throw new Error('Polaris no ha dado el código de emparejamiento.');
  return { otp: String(r.otp), passphrase, ip: r.ip || (info.ips || [])[0] || '', name: r.name || info.name, port: info.port || 47989 };
}

async function clients() {
  const r = await api('GET', '/api/clients/list');
  return (r && (r.named_certs || r.clients)) || [];
}

function init(opts) {
  onChange = opts.onChange || onChange;
  getCfg = opts.config || getCfg;
  logf = opts.log || logf;
  refresh().catch(() => {});
  clearInterval(timer);
  timer = setInterval(() => refresh().catch(() => {}), 30000);
}

module.exports = { init, refresh, info: () => info, startService, pin, otp, clients, api, appUuid, appUuidOf };
