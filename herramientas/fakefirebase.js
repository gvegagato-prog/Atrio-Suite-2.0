#!/usr/bin/env node
'use strict';
// Firebase de mentira para pruebas: Authentication (correo y contraseña, REST) y Realtime Database (REST)
// con las mismas reglas que se ponen en la consola para las cuentas de Atrio.
// Uso: node fakefirebase.js [puerto] [archivo.json]   ·   TOKEN_TTL=segundos para probar que el pase caduca.
const http = require('http');
const fs = require('fs');

const PORT = Number(process.argv[2] || 9000);
const FILE = process.argv[3] || null;
const TTL = Number(process.env.TOKEN_TTL || 3600);

let state = { users: {}, db: {} };
if (FILE && fs.existsSync(FILE)) state = JSON.parse(fs.readFileSync(FILE, 'utf8'));
const save = () => { if (FILE) fs.writeFileSync(FILE, JSON.stringify(state, null, 1)); };

const tokens = new Map(); // idToken -> { uid, exp }
const refresh = new Map(); // refreshToken -> uid
let n = 0;
function issue(uid) {
  n++;
  const id = `id.${uid}.${n}.${Math.random().toString(36).slice(2)}`;
  const rt = `rt.${uid}.${n}.${Math.random().toString(36).slice(2)}`;
  tokens.set(id, { uid, exp: Date.now() + TTL * 1000 });
  refresh.set(rt, uid);
  return { id, rt };
}

const parts = (p) => p.split('/').filter(Boolean);
function getAt(path) {
  let cur = state.db;
  for (const k of parts(path)) { if (cur == null || typeof cur !== 'object') return null; cur = cur[k]; }
  return cur === undefined ? null : cur;
}
function prune(o) {
  if (o && typeof o === 'object') {
    for (const k of Object.keys(o)) { o[k] = prune(o[k]); if (o[k] === null || o[k] === undefined) delete o[k]; }
    return Object.keys(o).length ? o : null;
  }
  return o;
}
function setAt(path, val) {
  const ps = parts(path);
  if (!ps.length) { state.db = val || {}; return; }
  let cur = state.db;
  for (const k of ps.slice(0, -1)) { if (!cur[k] || typeof cur[k] !== 'object') cur[k] = {}; cur = cur[k]; }
  if (val === null) delete cur[ps[ps.length - 1]]; else cur[ps[ps.length - 1]] = val;
  state.db = prune(state.db) || {};
}
function sv(v) {
  if (v && typeof v === 'object') {
    if (v['.sv'] === 'timestamp') return Date.now();
    const o = Array.isArray(v) ? [] : {};
    for (const k of Object.keys(v)) o[k] = sv(v[k]);
    return o;
  }
  return v;
}

// ---- Reglas (las de Account.RULES del móvil) ----
function canRead(path, uid) {
  const p = parts(path);
  if (p[0] === 'config') return true;
  if (p[0] === 'couples') return true;
  if (p[0] === 'usernames' && p.length >= 2) return !!uid;
  if (p[0] === 'users' && p.length >= 2) {
    if (!uid) return false;
    return uid === p[1] || getAt(`users/${p[1]}/partner`) === uid;
  }
  return false;
}
function canWrite(path, uid, val) {
  const p = parts(path);
  if (p[0] === 'couples') return true;
  if (p[0] === 'config') return p[1] === 'webKey' && p.length === 2 && !!uid && getAt('config/webKey') == null;
  if (p[0] === 'usernames' && p.length === 2) return !!uid && getAt(path) == null && val === uid;
  if (p[0] === 'users' && p.length >= 2) return !!uid && uid === p[1];
  return false;
}

// ---- Streaming (EventSource), como el de Firebase: put/patch/keep-alive/auth_revoked
const listeners = new Set(); // { path, res, uid }
function sse(res, event, data) {
  try { res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); } catch { /* cerrado */ }
}
function rel(base, p) {
  const b = parts(base); const q = parts(p);
  return '/' + q.slice(b.length).join('/');
}
function notify(path) {
  for (const l of listeners) {
    const L = parts(l.path).join('/');
    const W = parts(path).join('/');
    if (W === L || W.startsWith(L + '/') || L === '') sse(l.res, 'put', { path: L === W ? '/' : rel(l.path, path), data: getAt(path) });
    else if (L.startsWith(W + '/') || W === '') sse(l.res, 'put', { path: '/', data: getAt(l.path) });
  }
}
setInterval(() => { for (const l of listeners) sse(l.res, 'keep-alive', null); }, Number(process.env.KEEPALIVE_MS || 25000));
setInterval(() => {
  for (const l of listeners) {
    const tk = tokens.get(l.token);
    if (l.token && (!tk || tk.exp < Date.now())) { sse(l.res, 'auth_revoked', 'credential is no longer valid'); try { l.res.end(); } catch { /* */ } listeners.delete(l); }
  }
}, 1000);
let pushN = 0;
function pushId() { pushN++; return '-N' + Date.now().toString(36) + String(pushN).padStart(6, '0'); }

function send(res, code, body) {
  const text = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(text);
}

function authErr(res, msg) { send(res, 400, { error: { code: 400, message: msg, errors: [{ message: msg }] } }); }

const server = http.createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => { chunks.push(c); });
  req.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8');
    const u = new URL(req.url, `http://${req.headers.host}`);
    // Como Firebase: un POST puede decir qué método es (X-HTTP-Method-Override o ?x-http-method-override=)
    const method = String(req.headers['x-http-method-override'] || u.searchParams.get('x-http-method-override') || req.method).toUpperCase();
    const path = decodeURIComponent(u.pathname);
    try {
      // -------- Authentication
      if (path.startsWith('/v1/accounts:')) {
        if (!u.searchParams.get('key')) return authErr(res, 'API key not valid. Please pass a valid API key.');
        const op = path.slice('/v1/accounts:'.length);
        const b = raw ? JSON.parse(raw) : {};
        if (op === 'signUp') {
          if (!b.email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(b.email)) return authErr(res, 'INVALID_EMAIL');
          if (state.users[b.email]) return authErr(res, 'EMAIL_EXISTS');
          if (!b.password || b.password.length < 6) return authErr(res, 'WEAK_PASSWORD : Password should be at least 6 characters');
          const uid = 'u' + Math.random().toString(36).slice(2, 12);
          state.users[b.email] = { uid, password: b.password };
          save();
          const t = issue(uid);
          return send(res, 200, { kind: 'identitytoolkit#SignupNewUserResponse', idToken: t.id, email: b.email, refreshToken: t.rt, expiresIn: String(TTL), localId: uid });
        }
        if (op === 'signInWithPassword') {
          const usr = state.users[b.email];
          if (!usr || usr.password !== b.password) return authErr(res, 'INVALID_LOGIN_CREDENTIALS');
          const t = issue(usr.uid);
          return send(res, 200, { idToken: t.id, email: b.email, refreshToken: t.rt, expiresIn: String(TTL), localId: usr.uid, registered: true });
        }
        if (op === 'update') {
          const tk = tokens.get(b.idToken);
          if (!tk || tk.exp < Date.now()) return authErr(res, 'INVALID_ID_TOKEN');
          if (!b.password || b.password.length < 6) return authErr(res, 'WEAK_PASSWORD : Password should be at least 6 characters');
          const e = Object.keys(state.users).find((k) => state.users[k].uid === tk.uid);
          state.users[e].password = b.password;
          save();
          const t = issue(tk.uid);
          return send(res, 200, { localId: tk.uid, email: e, idToken: t.id, refreshToken: t.rt, expiresIn: String(TTL) });
        }
        return authErr(res, 'UNKNOWN_OP');
      }
      if (path === '/v1/token') {
        const f = new URLSearchParams(raw);
        if (f.get('grant_type') !== 'refresh_token') return authErr(res, 'INVALID_GRANT_TYPE');
        const uid = refresh.get(f.get('refresh_token'));
        if (!uid) return authErr(res, 'INVALID_REFRESH_TOKEN');
        const t = issue(uid);
        return send(res, 200, { id_token: t.id, refresh_token: t.rt, expires_in: String(TTL), token_type: 'Bearer', user_id: uid, project_id: 'prueba' });
      }
      if (path === '/__dump') return send(res, 200, state);

      // -------- Realtime Database
      if (!path.endsWith('.json')) { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end('<html>consola</html>'); }
      const dbPath = path.slice(0, -5);
      let uid = null;
      const a = u.searchParams.get('auth');
      if (a) {
        const tk = tokens.get(a);
        if (!tk) return send(res, 401, { error: 'Could not parse auth token.' });
        if (tk.exp < Date.now()) return send(res, 401, { error: 'Auth token is expired' });
        uid = tk.uid;
      }
      const silent = u.searchParams.get('print') === 'silent';
      if (method === 'GET' && /text\/event-stream/.test(req.headers.accept || '')) {
        if (!canRead(dbPath, uid)) return send(res, 401, { error: 'Permission denied' });
        res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
        const l = { path: dbPath, res, uid, token: a };
        listeners.add(l);
        sse(res, 'put', { path: '/', data: getAt(dbPath) });
        res.on('close', () => listeners.delete(l));
        return;
      }
      if (method === 'GET') {
        if (!canRead(dbPath, uid)) return send(res, 401, { error: 'Permission denied' });
        let v = getAt(dbPath);
        if (u.searchParams.get('shallow') === 'true' && v && typeof v === 'object') {
          const o = {}; for (const k of Object.keys(v)) o[k] = (v[k] && typeof v[k] === 'object') ? true : v[k]; v = o;
        }
        return send(res, 200, v);
      }
      const body = raw ? sv(JSON.parse(raw)) : null;
      if (method === 'PUT') {
        if (!canWrite(dbPath, uid, body)) return send(res, 401, { error: 'Permission denied' });
        setAt(dbPath, body); save(); notify(dbPath);
        return silent ? (res.writeHead(204), res.end()) : send(res, 200, body);
      }
      if (method === 'PATCH') {
        for (const k of Object.keys(body || {})) {
          if (!canWrite(`${dbPath}/${k}`, uid, body[k])) return send(res, 401, { error: 'Permission denied' });
        }
        for (const k of Object.keys(body || {})) { setAt(`${dbPath}/${k}`, body[k]); notify(`${dbPath}/${k}`); }
        save();
        return silent ? (res.writeHead(204), res.end()) : send(res, 200, body);
      }
      if (method === 'DELETE') {
        if (!canWrite(dbPath, uid, null)) return send(res, 401, { error: 'Permission denied' });
        setAt(dbPath, null); save(); notify(dbPath);
        return send(res, 200, null);
      }
      if (method === 'POST') {
        const id = pushId();
        if (!canWrite(`${dbPath}/${id}`, uid, body)) return send(res, 401, { error: 'Permission denied' });
        setAt(`${dbPath}/${id}`, body); save(); notify(`${dbPath}/${id}`);
        return send(res, 200, { name: id });
      }
      send(res, 405, { error: 'method' });
    } catch (e) {
      send(res, 400, { error: String(e && e.message) });
    }
  });
});
server.listen(PORT, '0.0.0.0', () => console.log(`firebase de mentira en :${PORT}`));
