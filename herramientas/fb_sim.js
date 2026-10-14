#!/usr/bin/env node
'use strict';
// Hace de «otro dispositivo» (como Atrio) contra el Firebase de mentira, para la prueba en el emulador.
//   node fb_sim.js add <usuario> <contraseña> <id> <nombre>   añade un valorado desde otro dispositivo
//   node fb_sim.js del <usuario> <contraseña> <id>            lo borra (con su marca de borrado)
//   node fb_sim.js pair <usuario> <contraseña> <nombre> <usuarioPareja>   crea la cuenta de la pareja y comparte
//   node fb_sim.js book <usuario> <contraseña> <id> <título>  añade un libro «Por leer» desde otro dispositivo
//   node fb_sim.js has <usuario> <contraseña> <texto>         ¿lo último del móvil contiene ese texto? (sale con 0 o 1)
//   node fb_sim.js at <usuario> <contraseña> <sitio> <texto>  ¿/users/<uid>/<sitio> contiene ese texto? (la suite)
const B = process.env.FB || 'http://127.0.0.1:9000';
const MAIL = (u) => `${u}@ronron.example.com`;
const DEV = 'atrio-prueba';

async function j(method, url, body) {
  const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text();
  if (!r.ok) throw new Error(`${method} ${url} -> ${r.status} ${t}`);
  return t ? JSON.parse(t) : null;
}
async function login(u, p, create) {
  if (create) {
    try { return await j('POST', `${B}/v1/accounts:signUp?key=prueba`, { email: MAIL(u), password: p, returnSecureToken: true }); } catch { /* ya existe */ }
  }
  return j('POST', `${B}/v1/accounts:signInWithPassword?key=prueba`, { email: MAIL(u), password: p, returnSecureToken: true });
}
const db = (s, m, path, body) => j(m, `${B}${path}.json?auth=${s.idToken}`, body);

async function latest(s, uid, skip) {
  const st = (await db(s, 'GET', `/users/${uid}/stamps`)) || {};
  const ids = Object.keys(st).filter((k) => k !== skip).sort((a, b) => st[b] - st[a]);
  if (!ids.length) return null;
  return db(s, 'GET', `/users/${uid}/dev/${ids[0]}`);
}
async function upload(s, data, name) {
  await db(s, 'PUT', `/users/${s.localId}/dev/${DEV}`, { name, user: '', device: 'atrio', updated: { '.sv': 'timestamp' }, data: JSON.stringify(data) });
  const ts = await db(s, 'GET', `/users/${s.localId}/dev/${DEV}/updated`);
  await db(s, 'PATCH', `/users/${s.localId}`, { [`stamps/${DEV}`]: ts });
}

(async () => {
  const [cmd, u, p, ...rest] = process.argv.slice(2);
  if (cmd === 'add' || cmd === 'del') {
    const s = await login(u, p);
    const l = await latest(s, s.localId, DEV);
    const d = JSON.parse(l.data);
    d.games = d.games || [];
    d.tombs = d.tombs || {};
    const id = Number(rest[0]);
    if (cmd === 'add') {
      d.games = d.games.filter((g) => g.id !== id);
      d.games.push({ id, name: rest[1], cover: '', general: 87, gfx: 90, story: 80, music: 85, gameplay: 88, review: 'Escrito desde otro dispositivo', hero: '', start: 0, end: 20000, creator: '', fav: false, platform: 'PC', pages: 0, minutes: 600, updated: Date.now(), ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '' });
    } else {
      d.games = d.games.filter((g) => g.id !== id);
      d.tombs[`games:${id}`] = Date.now();
    }
    await upload(s, d, l.name);
    console.log(`${cmd} ok`);
  } else if (cmd === 'book') {
    const s = await login(u, p);
    const l = await latest(s, s.localId, DEV);
    const d = JSON.parse(l.data);
    const id = Number(rest[0]);
    d.bplays = (d.bplays || []).filter((b) => b.id !== id);
    d.bplays.push({ id, name: rest[1], cover: '', hero: '', status: 0, entries: [], chapter: 0, every: 3, start: 0, end: 0, creator: 'Atrio', platform: '', pages: 3, minutes: 0, updated: Date.now() });
    await upload(s, d, l.name);
    console.log('book ok');
  } else if (cmd === 'at') {
    // ¿En ese sitio de la cuenta (p. ej. suite/shots) aparece ese texto? (sale con 0 o 1)
    const s = await login(u, p);
    const v = await db(s, 'GET', `/users/${s.localId}/${rest[0]}`);
    const ok = JSON.stringify(v || null).includes(rest[1]);
    console.log(ok ? 'sí' : 'no');
    process.exit(ok ? 0 : 1);
  } else if (cmd === 'pair') {
    const s = await login(u, p, true);
    try { await db(s, 'PUT', `/usernames/${u}`, s.localId); } catch { /* ya reservado */ }
    const data = { games: [{ id: 4242, name: 'Juego de la pareja', cover: '', general: 75, gfx: 70, story: 80, music: 70, gameplay: 75, review: 'Me gustó', updated: Date.now() }], plays: [], books: [], bplays: [], kinds: [], tombs: {}, recs: [], reacts: {} };
    await upload(s, data, rest[0]);
    const other = await db(s, 'GET', `/usernames/${rest[1]}`);
    await db(s, 'PUT', `/users/${s.localId}/partner`, other);
    console.log('pair ok', other);
  } else if (cmd === 'has') {
    const s = await login(u, p);
    const l = await latest(s, s.localId, DEV);
    const ok = !!(l && l.data.includes(rest[0]));
    console.log(ok ? 'sí' : 'no');
    process.exit(ok ? 0 : 1);
  }
})().catch((e) => { console.error(e.message); process.exit(2); });
