#!/usr/bin/env node
'use strict';
// Hace de «Atrio en el PC» para la prueba cruzada con el móvil (EscritosInteropTest.kt): baja lo de la cuenta, escribe
// en la primera escena, añade una ficha y 77 palabras escritas, y sube. Uso: node escritos-pc.js <base> <uid> <token>
const fs = require('fs');
const os = require('os');
const path = require('path');
const { crear } = require('../escritorio/src/main/escritos');

const [base, uid, token] = process.argv.slice(2);
const http = async (method, url, body) => {
  const r = await fetch(url, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${t}`);
  return t ? JSON.parse(t) : null;
};
(async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'esc-pc-'));
  const e = crear({ dir, dev: 'atrio-pc', net: { uid: () => uid, db: (m, p, b, q) => http(m, `${base}${p}.json?auth=${token}${q ? `&${q}` : ''}`, b) } });
  await e.sincroniza();
  const obra = e.obras()[0];
  const escena = e.escenasDe(obra.id)[0];
  const leido = { obras: e.obras().map((o) => o.titulo), texto: escena.texto, fichas: e.fichas(obra.id).map((f) => f.nombre), borradores: e.borradores(escena.id).length, hoyMovil: e.escritas(e.hoy(), obra.id) };
  e.escribe(escena.id, `${escena.texto} Y lo continué en el PC.`);
  e.nuevo('f', obra.id, { tipo: 'lugar', nombre: 'Cala del PC', resumen: 'Creada en Atrio' });
  e.registra(obra.id, 77);
  await e.sincroniza();
  e.parar();
  console.log(JSON.stringify({ leido, pendientes: e.pendientes().length }));
  process.exit(0);
})().catch((x) => { console.error(x.message); process.exit(1); });
