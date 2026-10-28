#!/usr/bin/env node
'use strict';
// Prueba de «Escribir» en el PC (escritorio/src/main/escritos.js) contra el Firebase de mentira: guardado en archivos,
// escribir sin conexión y subir después, dos dispositivos a la vez (mezcla y conflictos sin perder texto), borrados y las
// palabras del día. Los mismos casos que EscritosTest.kt del móvil. Uso (desde la raíz): node herramientas/prueba-escritos.js
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert');
const { crear } = require('../escritorio/src/main/escritos');

const port = 9300 + Math.floor(Math.random() * 600);
const base = `http://127.0.0.1:${port}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };

async function http(method, url, body) {
  const r = await fetch(url, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${t}`);
  return t ? JSON.parse(t) : null;
}

async function cuenta() {
  const su = await http('POST', `${base}/v1/accounts:signUp?key=k`, { email: `p${Date.now()}${Math.random()}@ronron.example.com`, password: 'secreto1' });
  return { uid: su.localId, token: su.idToken };
}
function dispositivo(c, nombre, urlBase = base) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `esc-${nombre}-`));
  const net = {
    uid: () => c.uid,
    db: async (method, p, body, query) => http(method, `${c.urlBase || urlBase}${p}.json?auth=${c.token}${query ? `&${query}` : ''}`, body),
  };
  return { dir, e: crear({ dir, dev: nombre, net }) };
}

(async () => {
  const srv = spawn('node', [path.join(__dirname, 'fakefirebase.js'), String(port)], { stdio: 'ignore' });
  await sleep(700);
  try {
    console.log('Núcleo');
    {
      const { e } = dispositivo({ uid: null }, 'solo');
      ok(e.palabras ? true : true, 'carga');
      const { palabras } = require('../escritorio/src/main/escritos');
      ok(palabras('# Capítulo uno\n\n*Hola* **mundo**, ¡qué tal-vez! No\'s') === 7, 'cuenta palabras como el móvil (7)');
      const o = e.nuevo('p', '', { titulo: 'La torre' });
      const c = e.nuevo('c', o.id, { titulo: 'Uno' });
      const s = e.nuevo('e', o.id, { cid: c.id, titulo: 'Llegada', texto: 'Aldara subió la torre. El Cuervo la esperaba.' });
      e.nuevo('f', o.id, { tipo: 'personaje', nombre: 'Aldara', alias: 'la hechicera, Ald' });
      e.nuevo('f', o.id, { tipo: 'personaje', nombre: 'El Cuervo' });
      e.nuevo('f', o.id, { tipo: 'lugar', nombre: 'Valle' });
      ok(e.palabrasObra(o.id) === 8, 'palabras de la obra');
      ok(JSON.stringify(e.fichasEn(o.id, e.get(s.id).texto).map((f) => f.nombre).sort()) === '["Aldara","El Cuervo"]', 'reconoce las fichas por nombre y alias');
      ok(e.busca(o.id, 'TORRE').filter((h) => h.doc.t === 'e').length === 1, 'busca sin mirar mayúsculas');
      ok(e.reemplaza(o.id, 'cuervo', 'Halcón') === 1 && e.get(s.id).texto.includes('El Halcón la esperaba'), 'reemplaza');
      e.escribe(s.id, `${e.get(s.id).texto} Y entonces ocurrió algo terrible.`);
      ok(e.escritas(e.hoy(), o.id) >= 5 && e.racha(o.id) === 1, 'cuenta lo escrito hoy y la racha');
      const c2 = e.nuevo('c', o.id, { titulo: 'Dos' });
      e.mueve(c2.id, null, c.id);
      ok(e.capitulos(o.id).map((x) => x.titulo).join() === 'Dos,Uno', 'reordena capítulos');
      ok(e.instantanea(s.id, 'primera', { fuerza: true }) && !e.instantanea(s.id, 'primera', { fuerza: true }), 'versiones sin repetir');
      e.borra(o.id);
      ok(e.todos().length === 0, 'borrar una obra borra todo lo suyo');
    }
    console.log('Disco');
    {
      const { dir, e } = dispositivo({ uid: null }, 'disco');
      const o = e.nuevo('p', '', { titulo: 'Persistente' });
      const c = e.nuevo('c', o.id, { titulo: 'Uno' });
      e.nuevo('e', o.id, { cid: c.id, texto: 'Texto que no se pierde' });
      const otra = crear({ dir, dev: 'disco', net: { uid: () => null } });
      ok(otra.obras()[0].titulo === 'Persistente' && otra.escenasDe(o.id)[0].texto === 'Texto que no se pierde', 'todo vuelve al reabrir');
    }
    console.log('Sin conexión');
    {
      const c = { ...(await cuenta()), urlBase: 'http://127.0.0.1:1' };
      const { e } = dispositivo(c, 'tren');
      const o = e.nuevo('p', '', { titulo: 'En el tren' });
      const ca = e.nuevo('c', o.id, { titulo: 'Uno' });
      const s = e.nuevo('e', o.id, { cid: ca.id, texto: 'Escrito sin cobertura' });
      let fallo = null;
      try { await e.sincroniza(); } catch (x) { fallo = x; }
      ok(!!fallo && e.pendientes().length === 3, 'sin red falla y todo queda pendiente');
      e.escribe(s.id, 'Escrito sin cobertura, y seguí escribiendo');
      c.urlBase = base;
      await e.sincroniza();
      ok(e.pendientes().length === 0, 'vuelve la red: sube todo');
      const raw = await http('GET', `${base}/users/${c.uid}/escritos/${s.id}.json?auth=${c.token}`);
      ok(raw.texto.endsWith('seguí escribiendo'), 'lo último también');
      e.parar();
    }
    console.log('Dos dispositivos');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Compartida' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Primera línea.' });
      await a.sincroniza(); await b.sincroniza();
      ok(b.escenasDe(o.id)[0].texto === 'Primera línea.', 'llega la obra entera al otro');
      b.escribe(s.id, 'Primera línea. Segunda línea.');
      await b.sincroniza(); await a.sincroniza();
      ok(a.get(s.id).texto === 'Primera línea. Segunda línea.' && !a.pendientes().length && !b.pendientes().length, 'lo que escribe el otro llega');
      a.borra(s.id);
      await a.sincroniza(); await b.sincroniza();
      ok(!b.get(s.id) && !b.escenasDe(o.id).length, 'el borrado se propaga');
      a.parar(); b.parar();
    }
    console.log('Editar lo mismo a la vez');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Conflicto' });
      const ca = a.nuevo('c', o.id, { titulo: 'Uno' });
      const s = a.nuevo('e', o.id, { cid: ca.id, texto: 'Base.' });
      await a.sincroniza(); await b.sincroniza();
      a.escribe(s.id, 'Base. Lo que escribí en el PC.');
      await sleep(5);
      b.escribe(s.id, 'Base. Lo que escribí en la tablet.');
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza();
      ok(b.get(s.id).texto === 'Base. Lo que escribí en la tablet.' && a.get(s.id).texto === b.get(s.id).texto, 'gana el más nuevo y los dos acaban igual');
      const bor = b.borradores(s.id);
      ok(bor.length === 1 && bor[0].texto === 'Base. Lo que escribí en el PC.' && bor[0].conflicto === 1, 'el texto del otro se guarda como borrador');
      ok(a.borradores(s.id).length === 1 && !a.pendientes().length && !b.pendientes().length, 'el borrador llega al otro, una sola vez');
      a.parar(); b.parar();
    }
    console.log('Palabras del día');
    {
      const c = await cuenta();
      const a = dispositivo(c, 'pc').e; const b = dispositivo(c, 'tablet').e;
      const o = a.nuevo('p', '', { titulo: 'Palabras' });
      await a.sincroniza(); await b.sincroniza();
      a.registra(o.id, 300); b.registra(o.id, 450);
      await a.sincroniza(); await b.sincroniza(); await a.sincroniza(); await b.sincroniza();
      ok(a.escritas(a.hoy(), o.id) === 750 && b.escritas(b.hoy(), o.id) === 750, 'se suman las de todos los dispositivos');
      a.registra(o.id, 50);
      await a.sincroniza(); await b.sincroniza();
      ok(b.escritas(b.hoy(), o.id) === 800, 'más palabras no pisan las del otro');
      a.parar(); b.parar();
    }
    console.log('Otra cuenta');
    {
      const c1 = await cuenta(); const c2 = await cuenta();
      const d = dispositivo(c1, 'pc');
      const o = d.e.nuevo('p', '', { titulo: 'Mía' });
      d.e.cuenta(c1.uid); await d.e.sincroniza();
      ok(d.e.pendientes().length === 0, 'subida a la primera cuenta');
      // Misma carpeta, otra cuenta: lo escrito aquí se sube también a la nueva
      const net2 = { uid: () => c2.uid, db: async (m, p, b, q) => http(m, `${base}${p}.json?auth=${c2.token}${q ? `&${q}` : ''}`, b) };
      const e2 = crear({ dir: d.dir, dev: 'pc', net: net2 });
      e2.cuenta(c2.uid);
      ok(e2.pendientes().length >= 1, 'al cambiar de cuenta todo vuelve a estar pendiente');
      await e2.sincroniza();
      const sub = await http('GET', `${base}/users/${c2.uid}/escritos/${o.id}.json?auth=${c2.token}`);
      ok(sub && sub.titulo === 'Mía', 'y llega a la cuenta nueva');
      d.e.parar(); e2.parar();
    }
  } catch (e) {
    console.error(e);
    fallos++;
  }
  srv.kill();
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})();
